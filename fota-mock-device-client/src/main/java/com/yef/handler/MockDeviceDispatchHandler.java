package com.yef.handler;

import com.yef.UpgradeFailErrorCode;
import com.yef.cilent.MockDeviceClient;
import com.yef.fileWriter.FirmwareFileHolder;
import com.yef.protocol.AckType;
import com.yef.protocol.FotaProtocol;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.HexUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.*;

/**
 * 模拟设备业务分发处理器。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class MockDeviceDispatchHandler extends ChannelInboundHandlerAdapter {

    //存放固件的临时文件路径
    private static final String FIRMWARE_PATH = System.getProperty("java.io.tmpdir") + "mock-dev/firmware/";
    private static final Path UPGRADING_FIRMWARE_ROOT = Paths.get(FIRMWARE_PATH);

    private final MinioClient minioClient;
    private final StringRedisTemplate redisTemplate;

    private final String minioBucket;
    private final FirmwareFileHolder firmwareFileHolder;
    private final Set<Long> canceledTaskIds = ConcurrentHashMap.newKeySet();

    private static final String MOCK_DEV_RUNTIME_KEY = "mock-dev:upgrade:runtime:";

    private static final int SHARD_COUNT = 8;
    private final ThreadPoolExecutor[] shardExecutors = new ThreadPoolExecutor[SHARD_COUNT];

    public MockDeviceDispatchHandler(StringRedisTemplate redisTemplate,
                                     MinioClient minioClient,
                                     @Value("${minio.device-bucket-name}")String minioBucket,
                                     FirmwareFileHolder firmwareFileHolder) {
        this.minioClient = minioClient;
        this.minioBucket = minioBucket;
        this.redisTemplate = redisTemplate;
        this.firmwareFileHolder = firmwareFileHolder;

        for (int i = 0; i < SHARD_COUNT; i++) {
            int threadId = i;
             shardExecutors[i] = new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1000),
                    r -> {
                        Thread t = new Thread(r);
                        t.setName("mock-device-file-writer-" + threadId);
                        return t;
                    },
                    new ThreadPoolExecutor.AbortPolicy()
            );
        }
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
                    cancelUpgrade.imei(),
                    cancelUpgrade.taskId(),
                    0,
                    FotaProtocol.ACK_TYPE_CANCEL)
            );
            log.info("MockDevice 已确认取消升级，imei={}，taskId={}", cancelUpgrade.imei(), cancelUpgrade.taskId());
            return;
        }
        super.channelRead(ctx, msg);
    }


    /**
     * 提交线程池 异步执行
     */
    public void submit(ChannelHandlerContext ctx,
                       FotaProtocol.UpgradePacketDTO packet,
                       Map<Object, Object> mockRuntimeHash,
                       int totalPacket,
                       Path path) {

        long taskId = packet.taskId();
        //对taskId取模，保证同一个taskId落在同一个queue上。从而保证局部串行，整体并行
        //int shardIndex = Math.floorMod(taskId, SHARD_COUNT);
        int shardIndex = Math.floorMod(Objects.hash(taskId, packet.imei()), SHARD_COUNT);
        //背压
        ThreadPoolExecutor executor = shardExecutors[shardIndex];
        int queueSize = executor.getQueue().size();
        int capacity = queueSize + executor.getQueue().remainingCapacity();
        if (queueSize * 1.0 / capacity >= 0.8) {
            ctx.executor().execute(() -> ctx.writeAndFlush(
                    new FotaProtocol.Ack(packet.imei(), packet.taskId(), packet.packetNo(), AckType.BUSY)
            ));
            log.info(">>>>>>>>>>> shardExecutors写本地固件异步线程池触发背压消息");
            return;
        }
        Runnable writeTask = () -> {
            try {
                FotaProtocol.UpgradeResultDTO upgradeResult = doWriteChunk(packet, mockRuntimeHash, totalPacket, path);

                ctx.executor().execute(() -> {
                    if (!ctx.channel().isActive()) {
                        log.warn("MockDevice 分包已写入本地文件，但 Channel 已断开，跳过 ACK 发送，imei={}，taskId={}，packetNo={}",
                                packet.imei(), packet.taskId(), packet.packetNo());
                        return;
                    }

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
                ctx.executor().execute(() -> {
                    if (!ctx.channel().isActive()) {
                        log.warn("MockDevice 分包写入失败，但 Channel 已断开，跳过 FAIL 发送，imei={}，taskId={}，packetNo={}",
                                packet.imei(), packet.taskId(), packet.packetNo());
                        return;
                    }
                    ctx.writeAndFlush(new FotaProtocol.Fail(
                            packet.imei(),
                            packet.taskId(),
                            packet.packetNo(),
                            UpgradeFailErrorCode.FAIL_ERROR_DEVICE_WRITE
                    ));
                });
            }
        };

        try {
            executor.execute(writeTask);
        } catch (RejectedExecutionException e) {
            log.warn("MockDevice 本地固件写入队列已满，拒绝分包写入，taskId={}，imei={}，packetNo={}，shardIndex={}，queueSize={}，capacity={}",
                    packet.taskId(), packet.imei(), packet.packetNo(), shardIndex, queueSize, capacity, e);
            ctx.executor().execute(() -> {
                if (ctx.channel().isActive()) {
                    ctx.writeAndFlush(new FotaProtocol.Ack(packet.imei(), packet.taskId(), packet.packetNo(), AckType.BUSY));
                }
            });
        }
    }

    /**
     * 写固件 chunkData至本地文件
     *
     * @param packet
     * @param mockRuntimeHash
     * @param totalPacket
     * @param path
     * @throws Exception
     */
    private FotaProtocol.UpgradeResultDTO doWriteChunk(FotaProtocol.UpgradePacketDTO packet,
                                                       Map<Object, Object> mockRuntimeHash,
                                                       int totalPacket,
                                                       Path path) throws Exception {
        Map<Object, Object> runtimeHash = new HashMap<>();
        runtimeHash.put("packetNo", String.valueOf(packet.packetNo()));
        runtimeHash.put("receivePacketAt", String.valueOf(System.currentTimeMillis()));

        int chunkSize = resolveChunkSize(packet, mockRuntimeHash, totalPacket);
        long offset = (long) (packet.packetNo() - 1) * chunkSize;

        // taskId 是单设备一次升级任务的唯一标识，因此可以作为 FileChannel 的缓存 key。
        FileChannel fileChannel = firmwareFileHolder.getOrCreateChannel(packet.taskId(), path);
        ByteBuffer buffer = ByteBuffer.wrap(packet.chunkData());
        //int written = fileChannel.write(buffer, offset);
        long position = offset;
        int totalWritten = 0;
        while (buffer.hasRemaining()) {
            int written = fileChannel.write(buffer, position);
            if (written <= 0) {
                throw new IOException("FileChannel write returned " + written);
            }
            position += written;
            totalWritten += written;
        }
        if (totalWritten != packet.chunkData().length) {
            throw new IOException("写入chunkData的长度不等于写入长度，written="
                    + totalWritten + ", chunkData.length=" + packet.chunkData().length);
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
        FirmwareReceiveResult receiveResult = completeFirmwareReceive(packet, mockRuntimeHash, path);

        uploadToMinio(mockRuntimeHash, receiveResult.firmware());

        cleanupRuntime(packet, path);

        simulateMcuFlush();

        log.info("MockDevice 分包接收完成,耗时(s):{}, imei={}，taskId={}，md5Matched={}",
                receiveResult.costTime(), packet.imei(), packet.taskId(), receiveResult.md5Matched());

        return buildUpgradeResult(packet, receiveResult);
    }

    /**
     * 完成固件接收：读取临时文件并校验 MD5。
     */
    private FirmwareReceiveResult completeFirmwareReceive(FotaProtocol.UpgradePacketDTO packet,
                                                          Map<Object, Object> mockRuntimeHash,
                                                          Path path) throws IOException {
        byte[] sourceMD5 = HexUtils.fromHexString(String.valueOf(mockRuntimeHash.get("md5")));
        int costTime = Math.toIntExact((System.currentTimeMillis()
                - Long.parseLong(String.valueOf(mockRuntimeHash.get("startTime")))) / 1000);

        byte[] firmware = Files.readAllBytes(path);
        boolean md5Matched = Arrays.equals(FotaProtocol.md5(firmware), sourceMD5);
        return new FirmwareReceiveResult(firmware, md5Matched, costTime);
    }

    /**
     * 将设备侧接收到的完整固件上传到设备侧 MinIO bucket。
     */
    private void uploadToMinio(Map<Object, Object> mockRuntimeHash, byte[] firmware) throws Exception {
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String versionName = String.valueOf(mockRuntimeHash.get("firmwareVersionName"));
        String firmwareName = String.valueOf(mockRuntimeHash.get("firmwareName"));
        String objectName = "firmware/" + dateFormat.format(new Date()) + "/" + versionName + "/" + firmwareName;

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(firmware), firmware.length, -1)
                        .contentType("application/octet-stream")
                        .build()
        );
    }

    /**
     * 清理本次升级任务的临时文件、空目录、runtime 和取消标记。
     */
    private void cleanupRuntime(FotaProtocol.UpgradePacketDTO packet, Path path) throws IOException {
        Files.deleteIfExists(path);
        deleteDirectoryIfEmpty(path.getParent());
        redisTemplate.delete(MOCK_DEV_RUNTIME_KEY + packet.imei());
        canceledTaskIds.remove(packet.taskId());
    }

    /**
     * 模拟设备 MCU 写入固件耗时。
     */
    private void simulateMcuFlush() throws InterruptedException {
        Thread.sleep(200);
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

            MockDeviceClient.MockDeviceProfile profile = ctx.channel()
                    .attr(MockDeviceClient.MockDeviceAttributes.DEVICE_PROFILE).get();

            log.debug("MockDevice 触发写空闲，imei={}，准备发送 0x05 Heartbeat", profile.imei());
            ctx.writeAndFlush(new FotaProtocol.Heartbeat(profile.imei()));
        }
        super.userEventTriggered(ctx, evt);
    }

    private void handleUpgradeRequest(ChannelHandlerContext ctx, FotaProtocol.UpgradeRequestDTO request) {
        canceledTaskIds.remove(request.taskId());

        Map<String, String> mockDevRuntime = getDevRuntimeHash(request);
        redisTemplate.opsForHash().putAll(MOCK_DEV_RUNTIME_KEY + request.imei(), mockDevRuntime);

        ctx.writeAndFlush(new FotaProtocol.Ack(request.imei(), request.taskId(), 0, FotaProtocol.ACK_TYPE_UPGRADE_REQUEST));
        log.info("MockDevice 已接受升级请求，imei={}，taskId={}，totalPacket={}", request.imei(), request.taskId(), request.totalPacket());
    }

    private void handleUpgradePacket(ChannelHandlerContext ctx, FotaProtocol.UpgradePacketDTO packet) {
        if (canceledTaskIds.contains(packet.taskId())) {
            log.debug("MockDevice 忽略已取消任务的分包，imei={}，taskId={}，packetNo={}", packet.imei(), packet.taskId(), packet.packetNo());
            return;
        }

        Map<Object, Object> mockRuntimeHash = redisTemplate.opsForHash().entries(MOCK_DEV_RUNTIME_KEY + packet.imei());
        if (mockRuntimeHash == null || mockRuntimeHash.isEmpty()) {
            ctx.writeAndFlush(new FotaProtocol.Fail(packet.imei(), packet.taskId(), packet.packetNo(), 2));
            return;
        }

        String totalPacketStr = String.valueOf(mockRuntimeHash.get("totalPacket"));
        int totalPacket = Integer.parseInt(totalPacketStr);

        if (packet.packetNo() <= 0 || packet.packetNo() > totalPacket) {
            ctx.writeAndFlush(new FotaProtocol.Fail(packet.imei(), packet.taskId(), packet.packetNo(), 3));
            return;
        }

        // 固件文件路径：/mock-dev/firmware/task/imei.bin
        Path path = Paths.get(FIRMWARE_PATH + packet.taskId() + "/" + packet.imei() + ".bin");

        /** 后续写文件、更新 mock-dev runtime、最终 MD5 校验、上传 MinIO 都交给 shard 写线程处理，避免阻塞 Netty EventLoop。*/
        submit(ctx, packet, mockRuntimeHash, totalPacket, path);
    }


    /**
     * 计算当前分包的固定写入位置。
     * 优先使用升级请求阶段保存的 chunkSize；最后一个分包可能小于 chunkSize，不能用当前 chunkData.length 反推 offset。
     */
    private int resolveChunkSize(FotaProtocol.UpgradePacketDTO packet,
                                 Map<Object, Object> mockRuntimeHash,
                                 int totalPacket) {
        Object chunkSizeValue = mockRuntimeHash.get("chunkSize");
        if (chunkSizeValue != null) {
            return Integer.parseInt(String.valueOf(chunkSizeValue));
        }

        Object fileSizeValue = mockRuntimeHash.get("fileSize");
        if (fileSizeValue == null) {
            return packet.chunkData().length;
        }

        long fileSize = Long.parseLong(String.valueOf(fileSizeValue));
        if (fileSize <= 0 || totalPacket <= 0) {
            return packet.chunkData().length;
        }

        return Math.toIntExact((fileSize + totalPacket - 1) / totalPacket);
    }


    /**
     * 构造设备侧升级结果上报消息。
     */
    private FotaProtocol.UpgradeResultDTO buildUpgradeResult(FotaProtocol.UpgradePacketDTO packet,
                                                             FirmwareReceiveResult receiveResult) {
        return new FotaProtocol.UpgradeResultDTO(
                packet.imei(),
                packet.taskId(),
                receiveResult.md5Matched() ? (byte) 0 : (byte) 1,
                receiveResult.md5Matched() ? 0 : UpgradeFailErrorCode.FAIL_ERROR_CRC16,
                receiveResult.costTime()
        );
    }

    private record FirmwareReceiveResult(byte[] firmware, boolean md5Matched, int costTime) {
    }


    // 删除空目录辅助方法
    private void deleteDirectoryIfEmpty(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.list(directory)) {
            if (stream.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
                log.debug("MockDevice 已删除空升级临时目录，dir={}", directory);
            }
        } catch (Exception e) {
            log.warn("MockDevice 删除空升级临时目录失败，dir={}", directory, e);
        }
    }



    @NotNull
    private static Map<String, String> getDevRuntimeHash(FotaProtocol.UpgradeRequestDTO request) {
        Map<String, String> mockDevRuntime = new HashMap<>();
        mockDevRuntime.put("imei", String.valueOf(request.imei()));
        mockDevRuntime.put("taskId", String.valueOf(request.taskId()));
        mockDevRuntime.put("offset", String.valueOf(0));
        mockDevRuntime.put("fileSize", String.valueOf(request.fileSize()));
        mockDevRuntime.put("chunkSize", String.valueOf(request.chunkSize()));
        mockDevRuntime.put("totalPacket", String.valueOf(request.totalPacket()));
        mockDevRuntime.put("md5", HexUtils.toHexString(request.md5()));
        mockDevRuntime.put("firmwareName", String.valueOf(request.firmwareName()));
        mockDevRuntime.put("firmwareVersionName", String.valueOf(request.firmwareVersionName()));
        mockDevRuntime.put("startTime", String.valueOf(System.currentTimeMillis()));
        return mockDevRuntime;
    }



}
