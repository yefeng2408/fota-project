package com.yef.cilent;

import com.yef.codec.FotaFrameDecoder;
import com.yef.codec.FotaMessageDecoder;
import com.yef.codec.FotaMessageEncoder;
import com.yef.protocol.FotaProtocol;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private final String configuredImei;

    private volatile boolean running;

    private final MinioClient minioClient;
    @Value("${minio.device-bucket-name}")
    private String minioBucket;

    /**
     * 客户端只需要一个 workerGroup
     */
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public MockDeviceClient(
            @Value("${netty.device-gateway-server.host:127.0.0.1}") String configuredHost,
            @Value("${netty.device-gateway-server.port:7611}") int configuredPort,
            @Value("${mock.device.imei:66666666}") String configuredImei,
            MinioClient minioClient) {
        this.configuredHost = configuredHost;
        this.configuredPort = configuredPort;
        this.configuredImei = configuredImei;
        this.minioClient = minioClient;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        log.info("准备启动 MockDeviceClient，目标网关地址: {}:{}", configuredHost, configuredPort);
        this.workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
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

                            pipeline.addLast("mockDeviceConnectHandler", new MockDeviceConnectHandler(configuredImei));
                            pipeline.addLast("mockDeviceDispatchHandler", new MockDeviceDispatchHandler(configuredImei,minioClient,minioBucket));
                            pipeline.addLast("mockDeviceExceptionHandler", new MockDeviceExceptionHandler());
                        }
                    });

            ChannelFuture future = bootstrap.connect(configuredHost, configuredPort).sync();
            this.serverChannel = future.channel();
            this.running = true;

            log.info("MockDeviceClient 连接网关成功，remoteAddress={}", serverChannel.remoteAddress());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdownQuietly();
            throw new IllegalStateException("MockDeviceClient 启动被中断", e);
        } catch (Exception e) {
            shutdownQuietly();
            throw new IllegalStateException("MockDeviceClient 启动失败", e);
        }
    }

    @Override
    public synchronized void stop() {
        if (!running && serverChannel == null && workerGroup == null) {
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
        // 尽量在 Spring 容器后期启动，保证相关 Bean 都已就绪
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

    /**
     * 连接建立后负责发送设备上线通知。
     */
    private static class MockDeviceConnectHandler extends ChannelInboundHandlerAdapter {
        private final String deviceImei;

        private MockDeviceConnectHandler(String deviceImei) {
            this.deviceImei = deviceImei;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("MockDevice 建立连接成功，imei={}，准备发送 0x10 DeviceBootUpMessage", deviceImei);
            //写死设备假数据
            String firmwareVersion = "v1.0.0";

            ctx.writeAndFlush(new FotaProtocol.DeviceBootUpDTO(deviceImei, firmwareVersion, "MOCK_DEVICE"));

            super.channelActive(ctx);
        }
    }

    /**
     * 模拟设备业务分发处理器。
     */
    private static class MockDeviceDispatchHandler extends ChannelInboundHandlerAdapter {

        private final String deviceImei;
        private UpgradeContext upgradeContext;
        private MinioClient minioClient;
        private String minioBucket;

        private MockDeviceDispatchHandler(String deviceImei,
                                          MinioClient minioClient,
                                          String minioBucket) {
            this.deviceImei = deviceImei;
            this.minioClient = minioClient;
            this.minioBucket = minioBucket;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            log.info("MockDevice 收到网关消息，imei={}，msg={}", deviceImei, msg);

            //handle 0x83
            if (msg instanceof FotaProtocol.PlatformAckDTO ack) {
                log.info("MockDevice 收到平台ACK，taskId={}，refMessageType={}，ackStatus={}，reasonCode={}",
                        ack.taskId(), ack.refMessageType(), ack.ackStatus(), ack.reasonCode());
                return;
            }

            //handle 0x81
            if (msg instanceof FotaProtocol.UpgradeRequestDTO request) {
                handleUpgradeRequest(ctx, request);
                return;
            }

            //handle 0x82
            if (msg instanceof FotaProtocol.UpgradePacketDTO packet) {
                /**
                 * 收包完成。做以下几件事情：
                 *              step1-> merge
                 *              step2-> check md5
                 *              step2-> 上发 UpgradeResult(0x06)
                 *              step3-> 清空上下文
                 */
                handleUpgradePacket(ctx, packet);
                //模拟设备处理耗时。也是为了更好地测试 观察分包过程
                Thread.sleep(10);
                return;
            }
            //handle 0x87
            if (msg instanceof FotaProtocol.CancelUpgradeDTO cancelUpgrade) {
                ctx.writeAndFlush(new FotaProtocol.Ack(deviceImei, cancelUpgrade.taskId(), 0, FotaProtocol.ACK_TYPE_CANCEL));
                //交给GC回收。避免占用内存
                upgradeContext = null;
                return;
            }
            super.channelRead(ctx, msg);
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof IdleStateEvent idleStateEvent
                    && idleStateEvent.state() == IdleState.WRITER_IDLE) {
                log.info("MockDevice 触发写空闲，imei={}，准备发送 0x05 Heartbeat", deviceImei);

                ctx.writeAndFlush(new FotaProtocol.Heartbeat(deviceImei));
            }
            super.userEventTriggered(ctx, evt);
        }

        /**
         * handle 0x81
         */
        private void handleUpgradeRequest(ChannelHandlerContext ctx, FotaProtocol.UpgradeRequestDTO request) {
            if (upgradeContext != null && upgradeContext.taskId != request.taskId()) {
                ctx.writeAndFlush(new FotaProtocol.Fail(deviceImei, request.taskId(), 0, 1));
                return;
            }
            upgradeContext = new UpgradeContext(request.taskId(), request.firmwareName(), request.firmwareVersionName(), request.totalPacket(), request.md5(), System.currentTimeMillis(), new TreeMap<>());
            ctx.writeAndFlush(new FotaProtocol.Ack(deviceImei, request.taskId(), 0, FotaProtocol.ACK_TYPE_UPGRADE_REQUEST));
            log.info("MockDevice 已接受升级请求，taskId={}，totalPacket={}", request.taskId(), request.totalPacket());
        }


        /**
         * handle 0x82
         */
        private void handleUpgradePacket(ChannelHandlerContext ctx, FotaProtocol.UpgradePacketDTO packet) {
            if (upgradeContext == null || upgradeContext.taskId() != packet.taskId()) {
                ctx.writeAndFlush(new FotaProtocol.Fail(deviceImei, packet.taskId(), packet.packetNo(), 2));
                return;
            }
            if (packet.packetNo() <= 0 || packet.packetNo() > upgradeContext.totalPacket()) {
                ctx.writeAndFlush(new FotaProtocol.Fail(deviceImei, packet.taskId(), packet.packetNo(), 3));
                return;
            }
            //将收到的0x82指令中的每一个固件分包数据写入临时内存
            upgradeContext.chunks().putIfAbsent(packet.packetNo(), packet.chunkData());
            ctx.writeAndFlush(new FotaProtocol.Ack(deviceImei, packet.taskId(), packet.packetNo(), FotaProtocol.ACK_TYPE_PACKET));
            log.info(">>>>>>>>>>>>>>>>>MockDevice 已接收分包，taskId={}，packetNo={}/{}", packet.taskId(), packet.packetNo(), packet.totalPacket());

            if (upgradeContext.chunks().size() == upgradeContext.totalPacket()) {
                byte[] firmware = merge(upgradeContext);
                //对比md5
                boolean md5Matched = Arrays.equals(FotaProtocol.md5(firmware), upgradeContext.expectedMd5());
                //模拟mcu写入flush 耗时场景
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                int costTime = (int) ((System.currentTimeMillis() - upgradeContext.startTime()) / 1000);
                ctx.writeAndFlush(new FotaProtocol.UpgradeResultDTO(deviceImei, packet.taskId(),
                        md5Matched ? (byte) 0 : (byte) 1, md5Matched ? 0 : 4, costTime));

                log.info("MockDevice 分包接收完成，taskId={}，md5Matched={}", packet.taskId(), md5Matched);
                DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
                String versionName = upgradeContext.firmwareVersionName();
                String firmwareName = upgradeContext.firmwareName();
                String objectName = "firmware/" + dateFormat.format(new Date()) + "/" + versionName + "/" + firmwareName;
                //写入minio
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
                    log.error("设备侧固件上传minio失败，taskId={}", packet.taskId(), e);
                }

                /*
                 * 当前 mock client 会将所有分包暂存在内存中，升级完成后需要释放上下文引用，
                 * 让 chunks 中缓存的 byte[] 分片后续可以被 GC 回收，避免长时间持有导致堆内存膨胀。
                 * 注意：upgradeContext = null 只是解除引用，并不会立即触发 GC。
                 *
                 * 如果后续一个进程模拟大量设备并发升级，内存占用约等于：
                 * 并发设备数 * 固件大小 + 分片 byte[] / TreeMap 节点 / key 等对象开销。
                 * 例如 500 台设备同时接收 3MB 固件，原始分片数据约 1.5GB，
                 * 加上对象与 Map 节点开销后，实际堆占用可能明显更高。
                 */
                upgradeContext = null;
                //模拟设备 mcu写入耗时
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                // ctx.close();
            }
        }

        //合并成字节流
        private byte[] merge(UpgradeContext context) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            context.chunks().forEach((packetNo, data) -> outputStream.writeBytes(data));
            return outputStream.toByteArray();
        }

        /**
         * 存储分包数据的上下文
         */

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

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("MockDeviceClient 链路异常", cause);
            ctx.close();
        }
    }

    private void shutdownQuietly() {
        try {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
                serverChannel = null;
            }
        } catch (Exception e) {
            log.warn("关闭 MockDeviceClient channel 异常", e);
        }

        try {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().syncUninterruptibly();
                workerGroup = null;
            }
        } catch (Exception e) {
            log.warn("关闭 MockDeviceClient workerGroup 异常", e);
        }

        running = false;
        log.info("MockDeviceClient 已停止");
    }
}
