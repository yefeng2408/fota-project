package com.yef.handler;

import com.yef.protocol.ChannelAttributes;
import com.yef.service.DeviceKeepAliveService;
import com.yef.session.SessionManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ChannelHandler.Sharable
public class ExceptionHandler extends ChannelInboundHandlerAdapter {

    private final SessionManager sessionManager;
    private final DeviceKeepAliveService deviceKeepAliveService;

    public ExceptionHandler(SessionManager sessionManager,
                            DeviceKeepAliveService deviceKeepAliveService) {
        this.sessionManager = sessionManager;
        this.deviceKeepAliveService = deviceKeepAliveService;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String imei = ctx.channel().attr(ChannelAttributes.IMEI).get();
        Long deviceId = ctx.channel().attr(ChannelAttributes.DEVICE_ID).get();
        log.warn("[ExceptionHandler] channel exception, imei:{}, cause:{}", imei, cause);

        if (deviceId != null) {
            deviceKeepAliveService.onDeviceOffline(deviceId);
        }
        sessionManager.remove(ctx.channel());

        ctx.close();
    }
}
