package com.yef.config;

import com.yef.codec.FotaMessageDecoder;
import com.yef.codec.FotaMessageEncoder;
import com.yef.codec.FotaFrameDecoder;
import com.yef.handler.DeviceIdentityHandler;
import com.yef.handler.ExceptionHandler;
import com.yef.handler.UpgradeDispatchHandler;
import com.yef.protocol.LengthFieldFrameSpec;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.stereotype.Component;

/**
 * @description: 出入站配置
 * @author: 叶丰
 * @date: 2026/4/15 16:07
 */
@Component
public class FotaServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final FotaFrameDecoder fotaFrameDecoder;
    private final FotaMessageDecoder fotaMessageDecoder;
    private final FotaMessageEncoder fotaMessageEncoder;
    private final DeviceIdentityHandler deviceIdentityHandler;
    private final UpgradeDispatchHandler upgradeDispatchHandler;
    private final ExceptionHandler exceptionHandler;

    public FotaServerChannelInitializer(FotaFrameDecoder fotaFrameDecoder,
                                        FotaMessageDecoder fotaMessageDecoder,
                                        FotaMessageEncoder fotaMessageEncoder,
                                        DeviceIdentityHandler deviceIdentityHandler,
                                        UpgradeDispatchHandler upgradeDispatchHandler,
                                        ExceptionHandler exceptionHandler) {
        this.fotaFrameDecoder = fotaFrameDecoder;
        this.fotaMessageDecoder = fotaMessageDecoder;
        this.fotaMessageEncoder = fotaMessageEncoder;
        this.deviceIdentityHandler = deviceIdentityHandler;
        this.upgradeDispatchHandler = upgradeDispatchHandler;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        //如果 180 秒内没有从客户端读到任何数据，就会触发 读空闲 事件
        pipeline.addLast("serverIdleStateHandler", new IdleStateHandler(180, 0, 0));
        pipeline.addLast("lengthFieldFrameDecoder", new LengthFieldBasedFrameDecoder(
                LengthFieldFrameSpec.MAX_FRAME_LENGTH,
                LengthFieldFrameSpec.LENGTH_FIELD_OFFSET,
                LengthFieldFrameSpec.LENGTH_FIELD_LENGTH,
                LengthFieldFrameSpec.LENGTH_ADJUSTMENT,
                LengthFieldFrameSpec.INITIAL_BYTES_TO_STRIP));
        pipeline.addLast("fotaFrameDecoder", fotaFrameDecoder);
        pipeline.addLast("fotaMessageDecoder", fotaMessageDecoder);
        pipeline.addLast("fotaMessageEncoder", fotaMessageEncoder);
        pipeline.addLast("deviceIdentityHandler", deviceIdentityHandler);
        pipeline.addLast("upgradeDispatchHandler", upgradeDispatchHandler);
        pipeline.addLast("exceptionHandler", exceptionHandler);
    }
}
