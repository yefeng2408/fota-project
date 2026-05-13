package com.yef.handler;

import com.yef.UpgradeFailErrorCode;
import com.yef.cilent.MockDeviceClient;
import com.yef.fileWriter.FirmwareFileHolder;
import com.yef.protocol.FotaProtocol;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.HexUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 模拟设备业务分发处理器。
 */
@Slf4j
@Component
public class MockDeviceDispatchHandler extends ChannelInboundHandlerAdapter {

    //存放固件的临时文件路径
    private static final String UPGRADING_FIRMWARE_PATH = System.getProperty("java.io.tmpdir") + "mock-dev/firmware/";
    private static final Path UPGRADING_FIRMWARE_ROOT = Paths.get(UPGRADING_FIRMWARE_PATH);

    private final MockDeviceClient.MockDeviceProfile profile;
    private final MinioClient minioClient;
    private final StringRedisTemplate redisTemplate;
    private final String minioBucket;
    private final FirmwareFileHolder firmwareFileHolder;
    private final Set<Long> canceledTaskIds = ConcurrentHashMap.newKeySet();

    private static final String MOCK_DEV_RUNTIME_KEY = "mock-dev:upgrade:runtime:";

    private static final int SHARD_COUNT = 8;
    private final ExecutorService[] shardExecutors = new ExecutorService[SHARD_COUNT];

    public MockDeviceDispatchHandler(MockDeviceClient.MockDeviceProfile profile, StringRedisTemplate redisTemplate, MinioClient minioClient, String minioBucket, FirmwareFileHolder firmwareFileHolder) {
        this.profile = profile;
        this.minioClient = minioClient;
        this.minioBucket = minioBucket;
        this.redisTemplate = redisTemplate;
        this.firmwareFileHolder = firmwareFileHolder;

        for (int i = 0; i < SHARD_COUNT; i++) {
            int threadId = i;
            shardExecutors[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r);
                t.setName("mock-device-file-writer-" + threadId);
                return t;
            });

        }
    }

    public void submit(ChannelHandlerContext ctx,
                       FotaProtocol.UpgradePacketDTO packet,
                       Map<Object, Object> mockRuntimeHash,
                       int totalPacket,
                       long offset,
                       Path path) {

        long taskId = packet.taskId();
        int shardIndex = Math.floorMod(taskId, SHARD_COUNT);
        //对taskId取模，保证同一个taskId落在同一个queue上。从而保证局部串行，整体并行
        shardExecutors[shardIndex].submit(() -> {
            try {
                FotaProtocol.UpgradeResultDTO upgradeResult = doWriteChunk(ctx, packet, mockRuntimeHash, totalPacket, offset, path);

                ctx.executor().execute(() -> {
                    ctx.writeAndFlush(new FotaProtocol.Ack(
                            packet.imei(),
                            packet.taskId(),
                            packet.packetNo(),
                            FotaProtocol.ACK_TYPE_PACKET
                    ));

                    if (upgradeResult != null) {
                        ctx.writeAndFlush(upgradeResult);
                    }
                });
            } catch (Exception e) {
                log.error("MockDevice 异步写入固件分包失败，imei={}，taskId={}，packetNo={}", packet.imei(), packet.taskId(), packet.packetNo(), e);
                ctx.executor().execute(() -> ctx.writeAndFlush(new FotaProtocol.Fail(
                        packet.imei(),
                        packet.taskId(),
                        packet.packetNo(),
                        UpgradeFailErrorCode.FAIL_ERROR_DEVICE_WRITE
                )));
            }
        });
    }

    /**
     * 写固件 chunkData至本地文件
     *
     * @param ctx
     * @param packet
     * @param mockRuntimeHash
     * @param totalPacket
     * @param offset
     * @param path
     * @throws Exception
     */
    private FotaProtocol.UpgradeResultDTO doWriteChunk(ChannelHandlerContext ctx,
                                                       FotaProtocol.UpgradePacketDTO packet,
                                                       Map<Object, Object> mockRuntimeHash,
                                                       int totalPacket,
                                                       long offset,
                                                       Path path) throws Exception {
        Map<Object, Object> runtimeHash = new HashMap<>();
        runtimeHash.put("packetNo", String.valueOf(packet.packetNo()));
        runtimeHash.put("receivePacketAt", String.valueOf(System.currentTimeMillis()));

        FileChannel fileChannel = firmwareFileHolder.getOrCreateChannel(packet.taskId(), path);
        ByteBuffer buffer = ByteBuffer.wrap(packet.chunkData());
        int written = fileChannel.write(buffer, offset);
        if (written != packet.chunkData().length) {
            throw new IOException("写入chunkData的长度不等于写入长度，written=" + written + ", chunkData.length=" + packet.chunkData().length);
        }

        long nextOffset = offset + packet.chunkData().length;
        runtimeHash.put("offset", String.valueOf(nextOffset));
        redisTemplate.opsForHash().putAll(MOCK_DEV_RUNTIME_KEY + packet.imei(), runtimeHash);

        if (packet.packetNo() == totalPacket) {
            try {
                firmwareFileHolder.closeAndRemove(packet.taskId());
                return handleUpgradeCompleted(packet, mockRuntimeHash, path);
            } catch (Exception e) {
                firmwareFileHolder.closeAndRemove(packet.taskId());
                throw e;
            }
        }

        return null;
    }

    //接收到所有分包
    private FotaProtocol.UpgradeResultDTO handleUpgradeCompleted(FotaProtocol.UpgradePacketDTO packet,
                                                                 Map<Object, Object> mockRuntimeHash,
                                                                 Path path) throws Exception {
        byte[] sourceMD5 = HexUtils.fromHexString(String.valueOf(mockRuntimeHash.get("md5")));

        int costTime = Math.toIntExact((System.currentTimeMillis()
                - Long.parseLong(String.valueOf(mockRuntimeHash.get("startTime")))) / 1000);

        byte[] firmware = Files.readAllBytes(path);
        boolean md5Matched = Arrays.equals(FotaProtocol.md5(firmware), sourceMD5);


        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String versionName = String.valueOf(mockRuntimeHash.get("firmwareVersionName"));
        String firmwareName = String.valueOf(mockRuntimeHash.get("firmwareName"));
        String objectName = "firmware/" + dateFormat.format(new Date()) + "/" + versionName + "/" + firmwareName;

        //上传至设备侧的minIO bucket
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(firmware), firmware.length, -1)
                        .contentType("application/octet-stream")
                        .build()
        );

        Files.deleteIfExists(path);
        deleteDirectoryIfEmpty(path.getParent());
        redisTemplate.delete(MOCK_DEV_RUNTIME_KEY + packet.imei());
        canceledTaskIds.remove(packet.taskId());
        //模拟设备mcu写入flush耗时
        Thread.sleep(3000);
        log.info("MockDevice 分包接收完成,耗时(s):{}, imei={}，taskId={}，md5Matched={}",
                costTime, profile.imei(), packet.taskId(), md5Matched);

        return new FotaProtocol.UpgradeResultDTO(
                profile.imei(),
                packet.taskId(),
                md5Matched ? (byte) 0 : (byte) 1,
                md5Matched ? 0 : UpgradeFailErrorCode.FAIL_ERROR_CRC16,
                costTime
        );
    }


    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if (msg instanceof FotaProtocol.PlatformAckDTO ack) {
                /*log.info("MockDevice 收到平台ACK，taskId={}，refMessageType={}，ackStatus={}，reasonCode={}",
                        ack.taskId(), ack.refMessageType(), ack.ackStatus(), ack.reasonCode());*/
            return;
        }

        //接收0x81升级请求
        if (msg instanceof FotaProtocol.UpgradeRequestDTO request) {
            handleUpgradeRequest(ctx, request);
            return;
        }

        //接收0x82分包数据，并给网关应答
        if (msg instanceof FotaProtocol.UpgradePacketDTO packet) {
            handleUpgradePacket(ctx, packet);

        }

        //接收0x87取消升级请求。标记任务已取消，并给网关回复取消ACK
        if (msg instanceof FotaProtocol.CancelUpgradeDTO cancelUpgrade) {
            canceledTaskIds.add(cancelUpgrade.taskId());
            Path taskDir = UPGRADING_FIRMWARE_ROOT.resolve(String.valueOf(cancelUpgrade.taskId()));
            Path path = taskDir.resolve(cancelUpgrade.imei() + ".bin");
            //删除临时文件
            Files.deleteIfExists(path);
            //如果 taskId 目录已经为空，则顺手删除 taskId 目录
            deleteDirectoryIfEmpty(taskDir);
            //删除本次升级的会话key
            redisTemplate.delete(MOCK_DEV_RUNTIME_KEY + cancelUpgrade.imei());
            //模拟等待
            Thread.sleep(3000);
            ctx.writeAndFlush(new FotaProtocol.Ack(
                    profile.imei(),
                    cancelUpgrade.taskId(),
                    0,
                    FotaProtocol.ACK_TYPE_CANCEL)
            );
            log.info("MockDevice 已确认取消升级，imei={}，taskId={}", profile.imei(), cancelUpgrade.taskId());
            return;
        }
        super.channelRead(ctx, msg);
    }

    /**
     * 如果连续 60 秒没有向通道写入数据，→ 触发写空闲。然后发送心跳包
     *
     * @param ctx
     * @param evt
     * @throws Exception
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent idleStateEvent
                && idleStateEvent.state() == IdleState.WRITER_IDLE) {
            log.debug("MockDevice 触发写空闲，imei={}，准备发送 0x05 Heartbeat", profile.imei());
            ctx.writeAndFlush(new FotaProtocol.Heartbeat(profile.imei()));
        }
        super.userEventTriggered(ctx, evt);
    }

    private void handleUpgradeRequest(ChannelHandlerContext ctx, FotaProtocol.UpgradeRequestDTO request) {
        canceledTaskIds.remove(request.taskId());

        Map<String, String> mockDevRuntime = getDevRuntimeHash(request);
        redisTemplate.opsForHash().putAll(MOCK_DEV_RUNTIME_KEY + request.imei(), mockDevRuntime);

        ctx.writeAndFlush(new FotaProtocol.Ack(profile.imei(), request.taskId(), 0, FotaProtocol.ACK_TYPE_UPGRADE_REQUEST));
        log.info("MockDevice 已接受升级请求，imei={}，taskId={}，totalPacket={}", profile.imei(), request.taskId(), request.totalPacket());
    }

    @NotNull
    private static Map<String, String> getDevRuntimeHash(FotaProtocol.UpgradeRequestDTO request) {
        Map<String, String> mockDevRuntime = new HashMap<>();
        mockDevRuntime.put("imei", String.valueOf(request.imei()));
        mockDevRuntime.put("taskId", String.valueOf(request.taskId()));
        mockDevRuntime.put("offset", String.valueOf(0));
        mockDevRuntime.put("fileSize", String.valueOf(request.fileSize()));
        mockDevRuntime.put("totalPacket", String.valueOf(request.totalPacket()));
        mockDevRuntime.put("md5", HexUtils.toHexString(request.md5()));
        mockDevRuntime.put("firmwareName", String.valueOf(request.firmwareName()));
        mockDevRuntime.put("firmwareVersionName", String.valueOf(request.firmwareVersionName()));
        mockDevRuntime.put("startTime", String.valueOf(System.currentTimeMillis()));
        return mockDevRuntime;
    }

    private void handleUpgradePacket(ChannelHandlerContext ctx, FotaProtocol.UpgradePacketDTO packet) {
        if (canceledTaskIds.contains(packet.taskId())) {
            log.debug("MockDevice 忽略已取消任务的分包，imei={}，taskId={}，packetNo={}", profile.imei(), packet.taskId(), packet.packetNo());
            return;
        }

        Map<Object, Object> mockRuntimeHash = redisTemplate.opsForHash().entries(MOCK_DEV_RUNTIME_KEY + packet.imei());
        if (mockRuntimeHash == null || mockRuntimeHash.isEmpty()) {
            ctx.writeAndFlush(new FotaProtocol.Fail(profile.imei(), packet.taskId(), packet.packetNo(), 2));
            return;
        }

        long offset = Long.parseLong(String.valueOf(mockRuntimeHash.get("offset")));
        String totalPacketStr = String.valueOf(mockRuntimeHash.get("totalPacket"));
        int totalPacket = Integer.parseInt(totalPacketStr);

        if (packet.packetNo() <= 0 || packet.packetNo() > totalPacket) {
            ctx.writeAndFlush(new FotaProtocol.Fail(profile.imei(), packet.taskId(), packet.packetNo(), 3));
            return;
        }

        // 固件文件路径：/mock-dev/firmware/task/imei.bin
        Path path = Paths.get(UPGRADING_FIRMWARE_PATH + packet.taskId() + "/" + packet.imei() + ".bin");

        /** 后续写文件、更新 mock-dev runtime、最终 MD5 校验、上传 MinIO 都交给 shard 写线程处理，避免阻塞 Netty EventLoop。*/
        submit(ctx, packet, mockRuntimeHash, totalPacket, offset, path);
    }

    // 删除空目录辅助方法
    private void deleteDirectoryIfEmpty(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.list(directory)) {
            if (stream.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
                log.debug("MockDevice 已删除空升级临时目录，imei={}，dir={}", profile.imei(), directory);
            }
        } catch (Exception e) {
            log.warn("MockDevice 删除空升级临时目录失败，imei={}，dir={}", profile.imei(), directory, e);
        }
    }

}
