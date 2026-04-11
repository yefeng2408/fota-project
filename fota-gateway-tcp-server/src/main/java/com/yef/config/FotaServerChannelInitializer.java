package com.yef.config;

import com.yef.codec.FotaFrameDecoder;
import com.yef.codec.FotaMessageDecoder;
import com.yef.codec.FotaMessageEncoder;
import com.yef.handler.DeviceIdentityHandler;
import com.yef.handler.ExceptionHandler;
import com.yef.handler.UpgradeDispatchHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.stereotype.Component;

@Component
public class FotaServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final DeviceIdentityHandler deviceIdentityHandler;
    private final UpgradeDispatchHandler upgradeDispatchHandler;
    private final ExceptionHandler exceptionHandler;

    public FotaServerChannelInitializer(DeviceIdentityHandler deviceIdentityHandler,
                                        UpgradeDispatchHandler upgradeDispatchHandler,
                                        ExceptionHandler exceptionHandler) {
        this.deviceIdentityHandler = deviceIdentityHandler;
        this.upgradeDispatchHandler = upgradeDispatchHandler;
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("idleStateHandler", new IdleStateHandler(90, 0, 0));
        pipeline.addLast("fotaFrameDecoder", new FotaFrameDecoder());
        pipeline.addLast("fotaMessageDecoder", new FotaMessageDecoder());
        pipeline.addLast("fotaMessageEncoder", new FotaMessageEncoder());
        pipeline.addLast("deviceIdentityHandler", deviceIdentityHandler);
        pipeline.addLast("upgradeDispatchHandler", upgradeDispatchHandler);
        pipeline.addLast("exceptionHandler", exceptionHandler);
    }
}
