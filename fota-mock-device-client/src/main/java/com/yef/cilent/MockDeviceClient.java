package com.yef.cilent;

import com.yef.codec.FotaFrameDecoder;
import com.yef.codec.FotaMessageDecoder;
import com.yef.codec.FotaMessageEncoder;
import com.yef.handler.MockDeviceDispatchHandler;
import com.yef.protocol.FotaProtocol;
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
import io.netty.handler.timeout.IdleStateHandler;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import io.netty.util.AttributeKey;
import lombok.Data;
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

    private final List<GatewayEndpoint> gatewayEndpoints;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    private final MockDeviceDispatchHandler mockDeviceDispatchHandler;

    private volatile boolean running;
    private EventLoopGroup workerGroup;
    private Bootstrap bootstrap;
    private final Map<String, Channel> deviceChannels = new ConcurrentHashMap<>();


    public MockDeviceClient(
            @Value("${netty.device-gateway-server.host:127.0.0.1}") String configuredHost,
            @Value("${netty.device-gateway-server.port:7611}") int configuredPort,
            @Value("${netty.device-gateway-server.endpoints:}") String configuredEndpoints,
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate stringRedisTemplate,
            MockDeviceDispatchHandler mockDeviceDispatchHandler) {

        this.gatewayEndpoints = resolveGatewayEndpoints(configuredHost, configuredPort, configuredEndpoints);
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        //共享的 业务进站handler
        this.mockDeviceDispatchHandler = mockDeviceDispatchHandler;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        log.info("准备启动 MockDeviceClient 控制器，候选网关地址: {}", gatewayEndpoints);
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
                        //去除掉客户端的idleStateHandler，但保留服务端的idleStateHandler。
                        // 服务端的90s读空闲事件依赖于客户端的 MockDeviceConnectHandler channelActive建立连接后 startHeartbeatTask去定时发送心跳
                        // 为什么这么做？因为如果设备处于长时间的分包过程，则不会触发 IdleStateHandler 事件。
                        //pipeline.addLast("idleStateHandler", new IdleStateHandler(0, 60, 0, TimeUnit.SECONDS));
                        pipeline.addLast("lengthFieldFrameDecoder", new LengthFieldBasedFrameDecoder(
                                FotaProtocol.MAX_FRAME_LENGTH,
                                FotaProtocol.LENGTH_FIELD_OFFSET,
                                FotaProtocol.LENGTH_FIELD_LENGTH,
                                FotaProtocol.LENGTH_ADJUSTMENT,
                                FotaProtocol.INITIAL_BYTES_TO_STRIP));
                        pipeline.addLast("fotaFrameDecoder", new FotaFrameDecoder());
                        pipeline.addLast("fotaMessageDecoder", new FotaMessageDecoder());
                        pipeline.addLast("fotaMessageEncoder", new FotaMessageEncoder());
                        pipeline.addLast("mockDeviceConnectHandler", new MockDeviceConnectHandler(
                                stringRedisTemplate,
                                mockDeviceDispatchHandler,
                                profile));
                        pipeline.addLast("mockDeviceDispatchHandler", mockDeviceDispatchHandler);
                        pipeline.addLast("mockDeviceExceptionHandler", new MockDeviceExceptionHandler(profile));
                    }
                });
        return bootstrap;
    }

    private boolean connectDevice(MockDeviceProfile profile) {
        //选择需要连接的网关实例
        GatewayEndpoint endpoint = selectGatewayEndpoint();
        try {
            ChannelFuture future = bootstrap.clone()
                    //创建这个 Channel 时，把该设备 profile 绑定到这个 Channel 的 attribute 上
                    .attr(MockDeviceAttributes.DEVICE_PROFILE, profile)
                    .connect(endpoint.host(), endpoint.port())
                    .sync();
            Channel channel = future.channel();
            deviceChannels.put(profile.imei(), channel);
            channel.closeFuture().addListener(listenerFuture -> {
                deviceChannels.remove(profile.imei(), channel);
                log.info("MockDevice 连接已关闭，imei={}", profile.imei());
            });
            log.info("MockDevice 已上线，imei={}，gateway={}:{}", profile.imei(), endpoint.host(), endpoint.port());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("MockDevice 启动被中断，imei={}，gateway={}:{}", profile.imei(), endpoint.host(), endpoint.port(), e);
            return false;
        } catch (Exception e) {
            log.error("MockDevice 上线失败，imei={}，gateway={}:{}", profile.imei(), endpoint.host(), endpoint.port(), e);
            return false;
        }
    }


    private GatewayEndpoint selectGatewayEndpoint() {
        if (gatewayEndpoints.size() == 1) {
            return gatewayEndpoints.get(0);
        }
        return gatewayEndpoints.get(ThreadLocalRandom.current().nextInt(gatewayEndpoints.size()));
    }

    private List<GatewayEndpoint> resolveGatewayEndpoints(String configuredHost,
                                                          int configuredPort,
                                                          String configuredEndpoints) {
        List<GatewayEndpoint> endpoints = new ArrayList<>();
        if (configuredEndpoints != null && !configuredEndpoints.isBlank()) {
            for (String item : configuredEndpoints.split(",")) {
                GatewayEndpoint endpoint = parseGatewayEndpoint(item);
                if (endpoint != null) {
                    endpoints.add(endpoint);
                }
            }
        }
        if (endpoints.isEmpty()) {
            endpoints.add(new GatewayEndpoint(configuredHost, configuredPort));
        }
        return List.copyOf(endpoints);
    }

    private GatewayEndpoint parseGatewayEndpoint(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        int separatorIndex = normalized.lastIndexOf(':');
        if (separatorIndex <= 0 || separatorIndex == normalized.length() - 1) {
            throw new IllegalArgumentException("invalid gateway endpoint: " + value + ", expected host:port");
        }
        String host = normalized.substring(0, separatorIndex).trim();
        int port = Integer.parseInt(normalized.substring(separatorIndex + 1).trim());
        return new GatewayEndpoint(host, port);
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

        private static final Logger log = LoggerFactory.getLogger(MockDeviceConnectHandler.class);
        private final MockDeviceProfile profile;
        private final StringRedisTemplate redisTemplate;
        private final MockDeviceDispatchHandler mockDeviceDispatchHandler;

        private ScheduledFuture<?> heartbeatFuture;

        private MockDeviceConnectHandler(StringRedisTemplate redisTemplate,
                                         MockDeviceDispatchHandler mockDeviceDispatchHandler,
                                         MockDeviceProfile profile) {
            this.redisTemplate = redisTemplate;
            this.mockDeviceDispatchHandler = mockDeviceDispatchHandler;
            this.profile = profile;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {

            String runtimeKey = "mock-dev:upgrade:runtime:" + profile.imei();
            Boolean upgrading = redisTemplate.hasKey(runtimeKey);
            startHeartbeatTask(ctx);

            if (Boolean.TRUE.equals(upgrading)) {
                ctx.writeAndFlush(new FotaProtocol.Heartbeat(profile.imei()));
                mockDeviceDispatchHandler.recoverPendingUpgradeResult(ctx, profile.imei());
            } else {
                log.info("MockDevice 建立连接成功，imei={}，准备发送 0x10 DeviceBootUpMessage", profile.imei());
            }

            super.channelActive(ctx);
        }


        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            stopHeartbeatTask();
            super.channelInactive(ctx);
        }


        private void startHeartbeatTask(ChannelHandlerContext ctx) {
            //随机起始心跳时间。防止大量的设备在同一时间发送心跳,导致 EventLoop 某一瞬间被心跳风暴打爆。 0 <= initialDelay < 60
            //long initialDelay = ThreadLocalRandom.current().nextLong(0, 10);
            heartbeatFuture = ctx.executor().scheduleAtFixedRate(() -> {
                if (!ctx.channel().isActive()) {
                    return;
                }

                ctx.writeAndFlush(new FotaProtocol.Heartbeat(profile.imei()));
                log.debug("MockDeviceConnectHandler ctx.executor().scheduleAtFixedRate 定时发送 0x05 Heartbeat, imei={}", profile.imei());
            }, 0, 60, TimeUnit.SECONDS);
        }

        private void stopHeartbeatTask() {
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(false);
                heartbeatFuture = null;
            }
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

    @Data
    public static class MockDeviceControlResponse {

        private Integer totalCount;
        private Integer successCount;
        private Integer skippedCount;
        private String summary;
    }

    public record MockDeviceProfile(String imei, String currentFirmwareVersion, String deviceType) {

        public MockDeviceProfile {
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

    public record GatewayEndpoint(String host, int port) {
        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    public static final class MockDeviceAttributes {
        public static final AttributeKey<MockDeviceProfile> DEVICE_PROFILE =
                AttributeKey.valueOf("mock.device.profile");

        private MockDeviceAttributes() {
        }
    }
}
