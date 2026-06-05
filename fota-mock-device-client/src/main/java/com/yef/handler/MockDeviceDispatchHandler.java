package com.yef.handler;

import com.alibaba.fastjson.JSON;
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
import java.time.Duration;
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
    private static final String MOCK_DEV_RESULT_LOCK_KEY = "mock-dev:upgrade:result-lock:";
    private static final String STATUS_RECEIVING = "RECEIVING";
    private static final String STATUS_WAIT_RESULT = "WAIT_RESULT";
    private static final String STATUS_RESULT_READY = "RESULT_READY";
    private static final String STATUS_RESULT_SENT = "RESULT_SENT";

    private static final int SHARD_COUNT = 8;
    /**
     * mock-device 接收分包过程中的 Redis runtime checkpoint 间隔。
     * 避免每个 chunk 都 putAll，降低 Redis 高频写压力。
     */
    private static final int MOCK_RUNTIME_CHECKPOINT_PACKET_INTERVAL = 10;

    /**
     * 写线程池队列监控日志间隔，避免每个 packet 都刷日志。
     */
    private static final long QUEUE_MONITOR_LOG_INTERVAL_MILLIS = 3_000L;

    private final ThreadPoolExecutor[] shardExecutors = new ThreadPoolExecutor[SHARD_COUNT];
    private final long[] lastQueueMonitorLogAt = new long[SHARD_COUNT];

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
                    new ArrayBlockingQueue<>(200),
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
        //通用应答【平台回复设备】
        if (msg instanceof FotaProtocol.PlatformAckDTO ack) {
            handlePlatformAck(ack);
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
        /**对taskId取模，保证同一个taskId落在同一个queue上。从而保证局部串行，整体并行。从而保证写固件临时文件是同一个线程*/
        //int shardIndex = Math.floorMod(taskId, SHARD_COUNT);
        int shardIndex = Math.floorMod(Objects.hash(taskId, packet.imei()), SHARD_COUNT);

        ThreadPoolExecutor executor = shardExecutors[shardIndex];
        int queueSize = executor.getQueue().size();
        int remainingCapacity = executor.getQueue().remainingCapacity();
        int capacity = queueSize + remainingCapacity;
        double queueUsage = capacity == 0 ? 1.0 : queueSize * 1.0 / capacity;

        logQueueUsageIfNecessary(shardIndex, executor, queueSize, remainingCapacity, capacity, queueUsage);
        /**背压机制 若线程池等待队列中的人物挤压过大。则发消息给服务端，服务端则降低发送分包数据的速率*/
        if (queueUsage >= 0.8) {
            //协议消息应该交给eventLoop去执行，而不是由来自线程池的线程进行ctx.writeAndFlush
            ctx.executor().execute(() -> ctx.writeAndFlush(
                    new FotaProtocol.Ack(packet.imei(), packet.taskId(), packet.packetNo(), AckType.BUSY)
            ));
            log.info(">>>>>>>>>>>shardExecutors写本地固件异步线程池触发背压消息");
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
                        writeUpgradeResult(ctx, upgradeResult);
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
            log.warn("MockDevice 本地固件写入队列已满，拒绝分包写入，taskId={}，imei={}，packetNo={}，shardIndex={}，queueSize={}，remainingCapacity={}，capacity={}，queueUsage={}%，activeCount={}",
                    packet.taskId(), packet.imei(), packet.packetNo(), shardIndex, queueSize, remainingCapacity, capacity,
                    String.format(Locale.ROOT, "%.2f", queueUsage * 100), executor.getActiveCount(), e);
            ctx.executor().execute(() -> {
                if (ctx.channel().isActive()) {
                    ctx.writeAndFlush(new FotaProtocol.Ack(packet.imei(), packet.taskId(), packet.packetNo(), AckType.BUSY));
                }
            });
        }
    }


    private void logQueueUsageIfNecessary(int shardIndex,
                                          ThreadPoolExecutor executor,
                                          int queueSize,
                                          int remainingCapacity,
                                          int capacity,
                                          double queueUsage) {
        long now = System.currentTimeMillis();
        if (now - lastQueueMonitorLogAt[shardIndex] < QUEUE_MONITOR_LOG_INTERVAL_MILLIS) {
            return;
        }

        lastQueueMonitorLogAt[shardIndex] = now;
        log.info("MockDevice 写线程池队列监控，shardIndex={}，queueSize={}，remainingCapacity={}，capacity={}，queueUsage={}%，activeCount={}，poolSize={}，completedTaskCount={}，taskCount={}",
                shardIndex,
                queueSize,
                remainingCapacity,
                capacity,
                String.format(Locale.ROOT, "%.2f", queueUsage * 100),
                executor.getActiveCount(),
                executor.getPoolSize(),
                executor.getCompletedTaskCount(),
                executor.getTaskCount());
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

        if (packet.packetNo() == totalPacket) {

            runtimeHash.put("status", STATUS_WAIT_RESULT);
            updateMockRuntimeIfNecessary(packet, totalPacket, runtimeHash);

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

    private void updateMockRuntimeIfNecessary(FotaProtocol.UpgradePacketDTO packet,
                                              int totalPacket,
                                              Map<Object, Object> runtimeHash) {
        if (!shouldCheckpointMockRuntime(packet.packetNo(), totalPacket)) {
            return;
        }

        redisTemplate.opsForHash().putAll(MOCK_DEV_RUNTIME_KEY + packet.imei(), runtimeHash);
    }

    private boolean shouldCheckpointMockRuntime(int packetNo, int totalPacket) {
        return packetNo == 1
                || packetNo >= totalPacket
                || packetNo % MOCK_RUNTIME_CHECKPOINT_PACKET_INTERVAL == 0;
    }

    //接收到所有分包
    private FotaProtocol.UpgradeResultDTO handleUpgradeCompleted(FotaProtocol.UpgradePacketDTO packet,
                                                                 Map<Object, Object> mockRuntimeHash,
                                                                 Path path) throws Exception {
        FirmwareReceiveResult receiveResult = completeFirmwareReceive(packet, mockRuntimeHash, path);

        uploadToMinio(mockRuntimeHash, receiveResult.firmware());

        simulateMcuFlush();

        log.info("MockDevice 分包接收完成,耗时(s):{}, imei={}，taskId={}，md5Matched={}",
                receiveResult.costTime(), packet.imei(), packet.taskId(), receiveResult.md5Matched());

        FotaProtocol.UpgradeResultDTO result = buildUpgradeResult(packet, receiveResult);
        persistUpgradeResult(result);
        return result;
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
        redisTemplate.delete(MOCK_DEV_RESULT_LOCK_KEY + packet.imei());
        canceledTaskIds.remove(packet.taskId());
    }

    /**
     * 模拟设备 MCU 写入固件耗时。
     */
    private void simulateMcuFlush() throws InterruptedException {
        Thread.sleep(500);
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
        Path path = buildFirmwarePath(packet.taskId(), packet.imei());

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

    /**
     * 设备重连后，如果最后一包已经写入但 0x06 升级结果没有被平台确认，则补偿生成或重发升级结果。
     */
    public void recoverPendingUpgradeResult(ChannelHandlerContext ctx, String imei) {
        Map<Object, Object> runtimeHash = redisTemplate.opsForHash().entries(MOCK_DEV_RUNTIME_KEY + imei);
        if (runtimeHash == null || runtimeHash.isEmpty()) {
            return;
        }

        FotaProtocol.UpgradeResultDTO readyResult = buildUpgradeResultFromRuntime(runtimeHash);
        if (readyResult != null) {
            writeUpgradeResult(ctx, readyResult);
            return;
        }

        if (!isFirmwareReceiveCompleted(runtimeHash)) {
            return;
        }

        long taskId = Long.parseLong(String.valueOf(runtimeHash.get("taskId")));
        int shardIndex = resolveShardIndex(taskId, imei);
        shardExecutors[shardIndex].execute(() -> {
            if (!tryAcquireResultLock(imei)) {
                return;
            }
            try {
                Map<Object, Object> latestRuntimeHash = redisTemplate.opsForHash().entries(MOCK_DEV_RUNTIME_KEY + imei);
                FotaProtocol.UpgradeResultDTO latestReadyResult = buildUpgradeResultFromRuntime(latestRuntimeHash);
                if (latestReadyResult != null) {
                    ctx.executor().execute(() -> writeUpgradeResult(ctx, latestReadyResult));
                    return;
                }

                if (!isFirmwareReceiveCompleted(latestRuntimeHash)) {
                    return;
                }

                long latestTaskId = Long.parseLong(String.valueOf(latestRuntimeHash.get("taskId")));
                int totalPacket = Integer.parseInt(String.valueOf(latestRuntimeHash.get("totalPacket")));
                Path path = buildFirmwarePath(latestTaskId, imei);
                FotaProtocol.UpgradePacketDTO syntheticPacket =
                        new FotaProtocol.UpgradePacketDTO(imei, latestTaskId, totalPacket, totalPacket, new byte[0]);

                firmwareFileHolder.closeAndRemove(latestTaskId);
                FotaProtocol.UpgradeResultDTO result = handleUpgradeCompleted(syntheticPacket, latestRuntimeHash, path);
                ctx.executor().execute(() -> writeUpgradeResult(ctx, result));
            } catch (Exception e) {
                log.error("MockDevice 重连后补偿升级结果失败，imei={}", imei, e);
            } finally {
                redisTemplate.delete(MOCK_DEV_RESULT_LOCK_KEY + imei);
            }
        });
    }

    private void writeUpgradeResult(ChannelHandlerContext ctx, FotaProtocol.UpgradeResultDTO result) {
        if (!ctx.channel().isActive()) {
            log.warn("MockDevice 升级结果待上报，但 Channel 已断开，imei={}，taskId={}", result.imei(), result.taskId());
            return;
        }

        ctx.writeAndFlush(result).addListener(future -> {
            if (future.isSuccess()) {
                markUpgradeResultSent(result);
            } else {
                log.warn("MockDevice 升级结果上报失败，等待下次重连补偿，imei={}，taskId={}",
                        result.imei(), result.taskId(), future.cause());
            }
        });
    }

    private void handlePlatformAck(FotaProtocol.PlatformAckDTO ack) throws IOException {
        log.info("===========>asda:handlePlatformAck={}", JSON.toJSONString(ack));
        if (ack.refMessageType() != FotaProtocol.UPGRADE_RESULT) {
            return;
        }

        if (ack.ackStatus() != 0) {
            log.warn("MockDevice 收到平台升级结果ACK失败，保留runtime等待重试，imei={}，taskId={}，reasonCode={}",
                    ack.imei(), ack.taskId(), ack.reasonCode());
            return;
        }

        Map<Object, Object> runtimeHash = redisTemplate.opsForHash().entries(MOCK_DEV_RUNTIME_KEY + ack.imei());
        if (runtimeHash == null || runtimeHash.isEmpty()) {
            return;
        }

        long runtimeTaskId = Long.parseLong(String.valueOf(runtimeHash.get("taskId")));
        if (runtimeTaskId != ack.taskId()) {
            return;
        }

        cleanupRuntime(
                new FotaProtocol.UpgradePacketDTO(ack.imei(), ack.taskId(), 0, 0, new byte[0]),
                buildFirmwarePath(ack.taskId(), ack.imei())
        );
        log.info("MockDevice 收到平台升级结果ACK，已清理升级runtime，imei={}，taskId={}", ack.imei(), ack.taskId());
    }

    private void persistUpgradeResult(FotaProtocol.UpgradeResultDTO result) {
        Map<String, String> fields = new HashMap<>();
        fields.put("status", STATUS_RESULT_READY);
        fields.put("result", String.valueOf(result.result()));
        fields.put("errorCode", String.valueOf(result.errorCode()));
        fields.put("costTime", String.valueOf(result.costTime()));
        fields.put("resultReadyAt", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().putAll(MOCK_DEV_RUNTIME_KEY + result.imei(), fields);
    }

    private void markUpgradeResultSent(FotaProtocol.UpgradeResultDTO result) {
        Map<String, String> fields = new HashMap<>();
        fields.put("status", STATUS_RESULT_SENT);
        fields.put("resultSentAt", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().putAll(MOCK_DEV_RUNTIME_KEY + result.imei(), fields);
    }

    private FotaProtocol.UpgradeResultDTO buildUpgradeResultFromRuntime(Map<Object, Object> runtimeHash) {
        if (runtimeHash == null || runtimeHash.isEmpty()) {
            return null;
        }

        if (!runtimeHash.containsKey("result")
                || !runtimeHash.containsKey("errorCode")
                || !runtimeHash.containsKey("costTime")) {
            return null;
        }

        return new FotaProtocol.UpgradeResultDTO(
                String.valueOf(runtimeHash.get("imei")),
                Long.parseLong(String.valueOf(runtimeHash.get("taskId"))),
                Byte.parseByte(String.valueOf(runtimeHash.get("result"))),
                Integer.parseInt(String.valueOf(runtimeHash.get("errorCode"))),
                Integer.parseInt(String.valueOf(runtimeHash.get("costTime")))
        );
    }

    private boolean isFirmwareReceiveCompleted(Map<Object, Object> runtimeHash) {
        if (runtimeHash == null || runtimeHash.isEmpty()) {
            return false;
        }

        Object packetNoValue = runtimeHash.get("packetNo");
        Object totalPacketValue = runtimeHash.get("totalPacket");
        if (packetNoValue != null && totalPacketValue != null) {
            int packetNo = Integer.parseInt(String.valueOf(packetNoValue));
            int totalPacket = Integer.parseInt(String.valueOf(totalPacketValue));
            if (totalPacket > 0 && packetNo >= totalPacket) {
                return true;
            }
        }

        Object offsetValue = runtimeHash.get("offset");
        Object fileSizeValue = runtimeHash.get("fileSize");
        if (offsetValue == null || fileSizeValue == null) {
            return false;
        }
        long offset = Long.parseLong(String.valueOf(offsetValue));
        long fileSize = Long.parseLong(String.valueOf(fileSizeValue));
        return fileSize > 0 && offset >= fileSize;
    }

    private boolean tryAcquireResultLock(String imei) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                MOCK_DEV_RESULT_LOCK_KEY + imei,
                String.valueOf(System.currentTimeMillis()),
                Duration.ofSeconds(30)
        );
        return Boolean.TRUE.equals(acquired);
    }

    private int resolveShardIndex(long taskId, String imei) {
        return Math.floorMod(Objects.hash(taskId, imei), SHARD_COUNT);
    }

    private Path buildFirmwarePath(long taskId, String imei) {
        return Paths.get(FIRMWARE_PATH + taskId + "/" + imei + ".bin");
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
        mockDevRuntime.put("status", STATUS_RECEIVING);
        return mockDevRuntime;
    }



}
