package com.yef.handler;

import com.yef.protocol.ChannelAttributes;
import com.yef.service.UpgradeExecutor;
import com.yef.session.SessionManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class ExceptionHandler extends ChannelInboundHandlerAdapter {

    private final SessionManager sessionManager;
    private final UpgradeExecutor upgradeExecutor;

    public ExceptionHandler(SessionManager sessionManager, UpgradeExecutor upgradeExecutor) {
        this.sessionManager = sessionManager;
        this.upgradeExecutor = upgradeExecutor;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String imei = ctx.channel().attr(ChannelAttributes.IMEI).get();
        Long deviceId = ctx.channel().attr(ChannelAttributes.DEVICE_ID).get();
        System.out.println("[ExceptionHandler] channel exception, imei=" + imei
                + ", deviceId=" + deviceId + ", cause=" + cause.getMessage());
        upgradeExecutor.pauseIfUpgrading(imei, deviceId);
        sessionManager.remove(ctx.channel());
        ctx.close();
    }
}
