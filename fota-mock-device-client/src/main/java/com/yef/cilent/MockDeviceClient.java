package com.yef.cilent;

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
import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
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
            MinioClient minioClient) {
        this.configuredHost = configuredHost;
        this.configuredPort = configuredPort;
        this.jdbcTemplate = jdbcTemplate;
        this.minioClient = minioClient;
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
                        pipeline.addLast("mockDeviceConnectHandler", new MockDeviceConnectHandler(profile));
                        pipeline.addLast("mockDeviceDispatchHandler",
                                new MockDeviceDispatchHandler(profile, minioClient, minioBucket));
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
            log.info("MockDevice 已上线，imei={}，remoteAddress={}", profile.imei(), channel.remoteAddress());
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

        private MockDeviceConnectHandler(MockDeviceProfile profile) {
            this.profile = profile;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("MockDevice 建立连接成功，imei={}，准备发送 0x10 DeviceBootUpMessage", profile.imei());
            ctx.writeAndFlush(new FotaProtocol.DeviceBootUpDTO(
                    profile.imei(),
                    profile.currentFirmwareVersion(),
                    profile.deviceType()
            ));
            super.channelActive(ctx);
        }
    }

    /**
     * 模拟设备业务分发处理器。
     */
    private static class MockDeviceDispatchHandler extends ChannelInboundHandlerAdapter {

        private final MockDeviceProfile profile;
        private final MinioClient minioClient;
        private final String minioBucket;
        private UpgradeContext upgradeContext;

        private MockDeviceDispatchHandler(MockDeviceProfile profile,
                                          MinioClient minioClient,
                                          String minioBucket) {
            this.profile = profile;
            this.minioClient = minioClient;
            this.minioBucket = minioBucket;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            log.info("MockDevice 收到网关消息，imei={}，msg={}", profile.imei(), msg);

            if (msg instanceof FotaProtocol.PlatformAckDTO ack) {
                log.info("MockDevice 收到平台ACK，taskId={}，refMessageType={}，ackStatus={}，reasonCode={}",
                        ack.taskId(), ack.refMessageType(), ack.ackStatus(), ack.reasonCode());
                return;
            }

            if (msg instanceof FotaProtocol.UpgradeRequestDTO request) {
                handleUpgradeRequest(ctx, request);
                return;
            }

            if (msg instanceof FotaProtocol.UpgradePacketDTO packet) {
                handleUpgradePacket(ctx, packet);
                Thread.sleep(10);
                return;
            }

            if (msg instanceof FotaProtocol.CancelUpgradeDTO cancelUpgrade) {
                ctx.writeAndFlush(new FotaProtocol.Ack(profile.imei(), cancelUpgrade.taskId(), 0, FotaProtocol.ACK_TYPE_CANCEL));
                upgradeContext = null;
                return;
            }
            super.channelRead(ctx, msg);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent idleStateEvent
                    && idleStateEvent.state() == IdleState.WRITER_IDLE) {
                log.info("MockDevice 触发写空闲，imei={}，准备发送 0x05 Heartbeat", profile.imei());
                ctx.writeAndFlush(new FotaProtocol.Heartbeat(profile.imei()));
            }
            super.userEventTriggered(ctx, evt);
        }

        private void handleUpgradeRequest(ChannelHandlerContext ctx, FotaProtocol.UpgradeRequestDTO request) {
            if (upgradeContext != null && upgradeContext.taskId != request.taskId()) {
                ctx.writeAndFlush(new FotaProtocol.Fail(profile.imei(), request.taskId(), 0, 1));
                return;
            }
            upgradeContext = new UpgradeContext(
                    request.taskId(),
                    request.firmwareName(),
                    request.firmwareVersionName(),
                    request.totalPacket(),
                    request.md5(),
                    System.currentTimeMillis(),
                    new TreeMap<>()
            );
            ctx.writeAndFlush(new FotaProtocol.Ack(profile.imei(), request.taskId(), 0, FotaProtocol.ACK_TYPE_UPGRADE_REQUEST));
            log.info("MockDevice 已接受升级请求，imei={}，taskId={}，totalPacket={}",
                    profile.imei(), request.taskId(), request.totalPacket());
        }

        private void handleUpgradePacket(ChannelHandlerContext ctx, FotaProtocol.UpgradePacketDTO packet) {
            if (upgradeContext == null || upgradeContext.taskId() != packet.taskId()) {
                ctx.writeAndFlush(new FotaProtocol.Fail(profile.imei(), packet.taskId(), packet.packetNo(), 2));
                return;
            }
            if (packet.packetNo() <= 0 || packet.packetNo() > upgradeContext.totalPacket()) {
                ctx.writeAndFlush(new FotaProtocol.Fail(profile.imei(), packet.taskId(), packet.packetNo(), 3));
                return;
            }
            upgradeContext.chunks().putIfAbsent(packet.packetNo(), packet.chunkData());
            ctx.writeAndFlush(new FotaProtocol.Ack(profile.imei(), packet.taskId(), packet.packetNo(), FotaProtocol.ACK_TYPE_PACKET));
            log.info(">>>>>>>>>>>>>>>>>MockDevice 已接收分包，imei={}，taskId={}，packetNo={}/{}",
                    profile.imei(), packet.taskId(), packet.packetNo(), packet.totalPacket());

            if (upgradeContext.chunks().size() == upgradeContext.totalPacket()) {
                byte[] firmware = merge(upgradeContext);
                boolean md5Matched = Arrays.equals(FotaProtocol.md5(firmware), upgradeContext.expectedMd5());
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                int costTime = (int) ((System.currentTimeMillis() - upgradeContext.startTime()) / 1000);
                ctx.writeAndFlush(new FotaProtocol.UpgradeResultDTO(
                        profile.imei(),
                        packet.taskId(),
                        md5Matched ? (byte) 0 : (byte) 1,
                        md5Matched ? 0 : 4,
                        costTime
                ));

                log.info("MockDevice 分包接收完成，imei={}，taskId={}，md5Matched={}",
                        profile.imei(), packet.taskId(), md5Matched);
                DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                String versionName = upgradeContext.firmwareVersionName();
                String firmwareName = upgradeContext.firmwareName();
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
                } catch (Exception e) {
                    log.error("设备侧固件上传minio失败，imei={}，taskId={}", profile.imei(), packet.taskId(), e);
                }

                upgradeContext = null;
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }

        private byte[] merge(UpgradeContext context) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            context.chunks().forEach((packetNo, data) -> outputStream.writeBytes(data));
            return outputStream.toByteArray();
        }

        private record UpgradeContext(
                long taskId,
                String firmwareName,
                String firmwareVersionName,
                int totalPacket,
                byte[] expectedMd5,
                long startTime,
                Map<Integer, byte[]> chunks
        ) {
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
