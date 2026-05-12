package com.yef.cilent;

import com.yef.chunkFile.ChunkedFileWriter;
import com.yef.codec.FotaFrameDecoder;
import com.yef.codec.FotaMessageDecoder;
import com.yef.codec.FotaMessageEncoder;
import com.yef.protocol.FotaProtocol;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.tomcat.util.buf.HexUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * @description: 模拟终端设备客户端
 * @author: 叶丰
 * @date: 2026/04/13 20:32
 */
@Component
public class MockDeviceClient implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(MockDeviceClient.class);

    private final String configuredHost;
    private final int configuredPort;
    private final JdbcTemplate jdbcTemplate;
    private final MinioClient minioClient;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${minio.device-bucket-name}")
    private String minioBucket;

    private volatile boolean running;
    private EventLoopGroup workerGroup;
    private Bootstrap bootstrap;
    private final Map<String, Channel> deviceChannels = new ConcurrentHashMap<>();

    public MockDeviceClient(
            @Value("${netty.device-gateway-server.host:127.0.0.1}") String configuredHost,
            @Value("${netty.device-gateway-server.port:7611}") int configuredPort,
            JdbcTemplate jdbcTemplate,
            MinioClient minioClient, StringRedisTemplate stringRedisTemplate) {
        this.configuredHost = configuredHost;
        this.configuredPort = configuredPort;
        this.jdbcTemplate = jdbcTemplate;
        this.minioClient = minioClient;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        log.info("准备启动 MockDeviceClient 控制器，目标网关地址: {}:{}", configuredHost, configuredPort);
        initializeBootstrapIfNecessary();
        this.running = true;
        log.info("MockDeviceClient 已就绪，等待平台按需触发模拟上线/下线");
    }

    @Override
    public synchronized void stop() {
        if (!running && deviceChannels.isEmpty() && workerGroup == null) {
            return;
        }
        shutdownQuietly();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    public synchronized MockDeviceControlResponse onlineDevices(List<String> imeiList) {
        List<String> normalizedImeis = normalizeImeis(imeiList);
        if (normalizedImeis.isEmpty()) {
            return buildResponse("模拟上线完成：共 0 台，成功上线 0 台，跳过 0 台", 0, 0, 0);
        }

        initializeBootstrapIfNecessary();
        this.running = true;

        Map<String, MockDeviceProfile> profileMap = loadDeviceProfiles(normalizedImeis);
        int successCount = 0;
        int skippedCount = 0;

        for (String imei : normalizedImeis) {
            Channel existingChannel = deviceChannels.get(imei);
            if (existingChannel != null && existingChannel.isActive()) {
                skippedCount++;
                continue;
            }
            if (existingChannel != null) {
                deviceChannels.remove(imei, existingChannel);
                closeChannelQuietly(existingChannel, imei);
            }

            MockDeviceProfile profile = profileMap.get(imei);
            if (profile == null) {
                skippedCount++;
                continue;
            }

            if (connectDevice(profile)) {
                successCount++;
            } else {
                skippedCount++;
            }
        }

        String summary = String.format("模拟上线完成：共 %d 台，成功上线 %d 台，跳过 %d 台",
                normalizedImeis.size(), successCount, skippedCount);
        return buildResponse(summary, normalizedImeis.size(), successCount, skippedCount);
    }

    public synchronized MockDeviceControlResponse offlineDevices(List<String> imeiList) {
        List<String> normalizedImeis = normalizeImeis(imeiList);
        if (normalizedImeis.isEmpty()) {
            return buildResponse("模拟下线完成：共 0 台，成功下线 0 台，跳过 0 台", 0, 0, 0);
        }

        int successCount = 0;
        int skippedCount = 0;
        for (String imei : normalizedImeis) {
            Channel channel = deviceChannels.remove(imei);
            if (channel == null) {
                skippedCount++;
                continue;
            }
            closeChannelQuietly(channel, imei);
            successCount++;
        }

        String summary = String.format("模拟下线完成：共 %d 台，成功下线 %d 台，跳过 %d 台",
                normalizedImeis.size(), successCount, skippedCount);
        return buildResponse(summary, normalizedImeis.size(), successCount, skippedCount);
    }

    private void initializeBootstrapIfNecessary() {
        if (workerGroup != null && bootstrap != null) {
            return;
        }
        this.workerGroup = new NioEventLoopGroup();
        this.bootstrap = createBootstrap(workerGroup);
    }

    private Map<String, MockDeviceProfile> loadDeviceProfiles(List<String> imeiList) {
        if (imeiList == null || imeiList.isEmpty()) {
            return Map.of();
        }
        String placeholders = imeiList.stream().map(item -> "?").collect(Collectors.joining(","));
        String sql = "select imei, current_firmware_version, device_type from device where imei in (" + placeholders + ")";
        List<MockDeviceProfile> profiles = jdbcTemplate.query(
                sql,
                imeiList.toArray(),
                (rs, rowNum) -> new MockDeviceProfile(
                        rs.getString("imei"),
                        rs.getString("current_firmware_version"),
                        rs.getString("device_type"))
        );
        Map<String, MockDeviceProfile> profileMap = new ConcurrentHashMap<>();
        if (profiles != null) {
            for (MockDeviceProfile profile : profiles) {
                if (profile.imei() != null && profile.imei().matches("\\d{8}")) {
                    profileMap.put(profile.imei(), profile);
                }
            }
        }
        return profileMap;
    }

    private Bootstrap createBootstrap(EventLoopGroup group) {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        MockDeviceProfile profile = ch.attr(MockDeviceAttributes.DEVICE_PROFILE).get();
                        ChannelPipeline pipeline = ch.pipeline();
                        //如果连续 60 秒没有向通道写入数据，→ 触发写空闲
                        pipeline.addLast("idleStateHandler", new IdleStateHandler(0, 60, 0, TimeUnit.SECONDS));
                        pipeline.addLast("lengthFieldFrameDecoder", new LengthFieldBasedFrameDecoder(
                                FotaProtocol.MAX_FRAME_LENGTH,
                                FotaProtocol.LENGTH_FIELD_OFFSET,
                                FotaProtocol.LENGTH_FIELD_LENGTH,
                                FotaProtocol.LENGTH_ADJUSTMENT,
                                FotaProtocol.INITIAL_BYTES_TO_STRIP));
                        pipeline.addLast("fotaFrameDecoder", new FotaFrameDecoder());
                        pipeline.addLast("fotaMessageDecoder", new FotaMessageDecoder());
                        pipeline.addLast("fotaMessageEncoder", new FotaMessageEncoder());
                        pipeline.addLast("mockDeviceConnectHandler", new MockDeviceConnectHandler(stringRedisTemplate, profile));
                        pipeline.addLast("mockDeviceDispatchHandler",
                                new MockDeviceDispatchHandler(profile, stringRedisTemplate, minioClient, minioBucket));
                        pipeline.addLast("mockDeviceExceptionHandler", new MockDeviceExceptionHandler(profile));
                    }
                });
        return bootstrap;
    }

    private boolean connectDevice(MockDeviceProfile profile) {
        try {
            ChannelFuture future = bootstrap.clone()
                    .attr(MockDeviceAttributes.DEVICE_PROFILE, profile)
                    .connect(configuredHost, configuredPort)
                    .sync();
            Channel channel = future.channel();
            deviceChannels.put(profile.imei(), channel);
            channel.closeFuture().addListener(listenerFuture -> {
                deviceChannels.remove(profile.imei(), channel);
                log.info("MockDevice 连接已关闭，imei={}", profile.imei());
            });
            //log.info("MockDevice 已上线，imei={}，remoteAddress={}", profile.imei(), channel.remoteAddress());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("MockDevice 启动被中断，imei={}", profile.imei(), e);
            return false;
        } catch (Exception e) {
            log.error("MockDevice 上线失败，imei={}", profile.imei(), e);
            return false;
        }
    }

    private void closeChannelQuietly(Channel channel, String imei) {
        try {
            channel.close().syncUninterruptibly();
            log.info("MockDevice 已下线，imei={}", imei);
        } catch (Exception e) {
            log.warn("关闭 MockDevice channel 异常，imei={}", imei, e);
        }
    }

    private List<String> normalizeImeis(List<String> imeiList) {
        if (imeiList == null || imeiList.isEmpty()) {
            return List.of();
        }
        return new LinkedHashSet<>(imeiList).stream()
                .map(imei -> imei == null ? "" : imei.trim())
                .filter(imei -> imei.matches("\\d{8}"))
                .toList();
    }

    private MockDeviceControlResponse buildResponse(String summary, int totalCount, int successCount, int skippedCount) {
        MockDeviceControlResponse response = new MockDeviceControlResponse();
        response.setTotalCount(totalCount);
        response.setSuccessCount(successCount);
        response.setSkippedCount(skippedCount);
        response.setSummary(summary);
        return response;
    }

    /**
     * 连接建立后负责发送设备上线通知。
     */
    private static class MockDeviceConnectHandler extends ChannelInboundHandlerAdapter {

        private final MockDeviceProfile profile;
        private final StringRedisTemplate redisTemplate;

        private MockDeviceConnectHandler(StringRedisTemplate redisTemplate,
                                         MockDeviceProfile profile) {
            this.redisTemplate = redisTemplate;
            this.profile = profile;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {

            String runtimeKey = MockDeviceDispatchHandler.MOCK_DEV_RUNTIME_KEY + profile.imei();
            Boolean upgrading = redisTemplate.hasKey(runtimeKey);

            if (Boolean.TRUE.equals(upgrading)) {
                ctx.writeAndFlush(new FotaProtocol.Heartbeat(profile.imei()));
            } else {
                log.info("MockDevice 建立连接成功，imei={}，准备发送 0x10 DeviceBootUpMessage", profile.imei());
                ctx.writeAndFlush(new FotaProtocol.DeviceBootUpDTO(
                        profile.imei(),
                        profile.currentFirmwareVersion(),
                        profile.deviceType()
                ));
            }
            super.channelActive(ctx);
        }
    }

    /**
     * 模拟设备业务分发处理器。
     */
    private static class MockDeviceDispatchHandler extends ChannelInboundHandlerAdapter {

        //存放固件的临时文件路径
        private static final String UPGRADING_FIRMWARE_PATH = System.getProperty("java.io.tmpdir") + "mock-dev/firmware/";
        private static final Path UPGRADING_FIRMWARE_ROOT = Paths.get(UPGRADING_FIRMWARE_PATH);

        private final MockDeviceProfile profile;
        private final MinioClient minioClient;
        private final StringRedisTemplate redisTemplate;
        private final String minioBucket;
        private final Set<Long> canceledTaskIds = ConcurrentHashMap.newKeySet();

        private static final String MOCK_DEV_RUNTIME_KEY = "mock-dev:upgrade:runtime:";

        private MockDeviceDispatchHandler(MockDeviceProfile profile,
                                          StringRedisTemplate redisTemplate,
                                          MinioClient minioClient,
                                          String minioBucket) {
            this.profile = profile;
            this.redisTemplate = redisTemplate;
            this.minioClient = minioClient;
            this.minioBucket = minioBucket;
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
            Long offset = Long.parseLong(String.valueOf(mockRuntimeHash.get("offset")));
            String totalPacketStr = String.valueOf(mockRuntimeHash.get("totalPacket"));
            int totalPacket = Integer.parseInt(totalPacketStr);

            if (mockRuntimeHash == null || mockRuntimeHash.isEmpty()) {
                ctx.writeAndFlush(new FotaProtocol.Fail(profile.imei(), packet.taskId(), packet.packetNo(), 2));
                return;
            }

            if (packet.packetNo() <= 0 || packet.packetNo() > totalPacket) {
                ctx.writeAndFlush(new FotaProtocol.Fail(profile.imei(), packet.taskId(), packet.packetNo(), 3));
                return;
            }

            Map<Object, Object> runtimeHash = new HashMap<>();
            runtimeHash.put("packetNo", String.valueOf(packet.packetNo()));
            runtimeHash.put("receivePacketAt", String.valueOf(System.currentTimeMillis()));

            //固件文件路径：/mock-dev/firmware/task/imei.bin
            String firmwarePath = UPGRADING_FIRMWARE_PATH + packet.taskId() + "/" + packet.imei() + ".bin";
            Path path = Paths.get(firmwarePath);

            try (ChunkedFileWriter fileWriter = new ChunkedFileWriter(path)) {
                fileWriter.writeChunk(offset,packet.chunkData());
                offset+=packet.chunkData().length;
                runtimeHash.put("offset", String.valueOf(offset));
            } catch (Exception e) {
                log.error("MockDevice 写入固件临时分包失败，imei={}，taskId={}，packetNo={}，path={}", profile.imei(), packet.taskId(), packet.packetNo(), path, e);
                ctx.writeAndFlush(new FotaProtocol.Fail(profile.imei(), packet.taskId(), packet.packetNo(), 5));
                return;
            }
            redisTemplate.opsForHash().putAll(MOCK_DEV_RUNTIME_KEY + packet.imei(), runtimeHash);
            ctx.writeAndFlush(new FotaProtocol.Ack(profile.imei(), packet.taskId(), packet.packetNo(), FotaProtocol.ACK_TYPE_PACKET));

            if (packet.packetNo() == totalPacket) {
                byte[] sourceMD5 = HexUtils.fromHexString(String.valueOf(mockRuntimeHash.get("md5")));

                int costTime = Math.toIntExact(
                        (System.currentTimeMillis() - Long.parseLong(String.valueOf(mockRuntimeHash.get("startTime")))) / 1000
                );
                //读取写入的完整的临时文件
                byte[] firmware;
                try {
                    firmware = Files.readAllBytes(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                boolean md5Matched = Arrays.equals(FotaProtocol.md5(firmware), sourceMD5);

                ctx.writeAndFlush(new FotaProtocol.UpgradeResultDTO(
                        profile.imei(),
                        packet.taskId(),
                        md5Matched ? (byte) 0 : (byte) 1,
                        md5Matched ? 0 : 4,
                        costTime
                ));

                DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                String versionName = String.valueOf(mockRuntimeHash.get("firmwareVersionName"));
                String firmwareName = String.valueOf(mockRuntimeHash.get("firmwareName"));
                String objectName = "firmware/" + dateFormat.format(new Date()) + "/" + versionName + "/" + firmwareName;
                try {
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(minioBucket)
                                    .object(objectName)
                                    .stream(new ByteArrayInputStream(firmware), firmware.length, -1)
                                    .contentType("application/octet-stream")
                                    .build()
                    );
                    //清除临时文件
                    Files.deleteIfExists(path);
                    //如果 taskId 目录已经为空，则顺手删除 taskId 目录
                    deleteDirectoryIfEmpty(path.getParent());
                    //删除本次升级会话的 MOCK_DEV_RUNTIME_KEY
                    redisTemplate.delete(MOCK_DEV_RUNTIME_KEY + packet.imei());
                } catch (Exception e) {
                    log.error("设备侧固件上传minio失败，imei={}，taskId={}", profile.imei(), packet.taskId(), e);
                }
                canceledTaskIds.remove(packet.taskId());
                try {
                    //模拟设备mcu写入 flush
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                log.info("MockDevice 分包接收完成,耗时(s):{}, imei={}，taskId={}，md5Matched={}", costTime, profile.imei(), packet.taskId(), md5Matched);
            }
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

    /**
     * 客户端异常处理器。
     */
    private static class MockDeviceExceptionHandler extends ChannelInboundHandlerAdapter {

        private final MockDeviceProfile profile;

        private MockDeviceExceptionHandler(MockDeviceProfile profile) {
            this.profile = profile;
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("MockDeviceClient 链路异常，imei={}", profile.imei(), cause);
            ctx.close();
        }
    }

    private void shutdownQuietly() {
        for (Map.Entry<String, Channel> entry : deviceChannels.entrySet()) {
            closeChannelQuietly(entry.getValue(), entry.getKey());
        }
        deviceChannels.clear();

        try {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().syncUninterruptibly();
                workerGroup = null;
            }
        } catch (Exception e) {
            log.warn("关闭 MockDeviceClient workerGroup 异常", e);
        }
        bootstrap = null;

        running = false;
        log.info("MockDeviceClient 已停止");
    }

    public static class MockDeviceControlResponse {

        private Integer totalCount;
        private Integer successCount;
        private Integer skippedCount;
        private String summary;

        public Integer getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(Integer totalCount) {
            this.totalCount = totalCount;
        }

        public Integer getSuccessCount() {
            return successCount;
        }

        public void setSuccessCount(Integer successCount) {
            this.successCount = successCount;
        }

        public Integer getSkippedCount() {
            return skippedCount;
        }

        public void setSkippedCount(Integer skippedCount) {
            this.skippedCount = skippedCount;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }
    }

    private record MockDeviceProfile(String imei, String currentFirmwareVersion, String deviceType) {

        private MockDeviceProfile {
            currentFirmwareVersion = sanitizeCurrentVersion(currentFirmwareVersion);
            deviceType = sanitizeDeviceType(deviceType);
        }

        private static String sanitizeCurrentVersion(String currentFirmwareVersion) {
            return currentFirmwareVersion == null || currentFirmwareVersion.isBlank() ? "v1.0.0" : currentFirmwareVersion;
        }

        private static String sanitizeDeviceType(String deviceType) {
            return deviceType == null || deviceType.isBlank() ? "MOCK_DEVICE" : deviceType;
        }
    }

    private static final class MockDeviceAttributes {
        private static final io.netty.util.AttributeKey<MockDeviceProfile> DEVICE_PROFILE =
                io.netty.util.AttributeKey.valueOf("mock.device.profile");

        private MockDeviceAttributes() {
        }
    }
}
