package com.yef.handler;

import com.yef.protocol.AckMessage;
import com.yef.protocol.ChannelAttributes;
import com.yef.protocol.DeviceRegisterMessage;
import com.yef.protocol.FailMessage;
import com.yef.service.UpgradeExecutor;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class UpgradeDispatchHandler extends SimpleChannelInboundHandler<Object> {

    private final UpgradeExecutor upgradeExecutor;

    public UpgradeDispatchHandler(UpgradeExecutor upgradeExecutor) {
        this.upgradeExecutor = upgradeExecutor;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        Long deviceId = ctx.channel().attr(ChannelAttributes.DEVICE_ID).get();
        if (msg instanceof DeviceRegisterMessage) {
            upgradeExecutor.onDeviceRegistered((DeviceRegisterMessage) msg, deviceId);
        } else if (msg instanceof AckMessage) {
            upgradeExecutor.handleAck((AckMessage) msg, deviceId);
        } else if (msg instanceof FailMessage) {
            upgradeExecutor.handleFail((FailMessage) msg, deviceId);
        } else {
            System.out.println("[UpgradeDispatchHandler] ignored inbound message: " + msg);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.READER_IDLE) {
            String imei = ctx.channel().attr(ChannelAttributes.IMEI).get();
            Long deviceId = ctx.channel().attr(ChannelAttributes.DEVICE_ID).get();
            System.out.println("[UpgradeDispatchHandler] reader idle, close channel, imei=" + imei + ", deviceId=" + deviceId);
            upgradeExecutor.pauseIfUpgrading(imei, deviceId);
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String imei = ctx.channel().attr(ChannelAttributes.IMEI).get();
        Long deviceId = ctx.channel().attr(ChannelAttributes.DEVICE_ID).get();
        upgradeExecutor.pauseIfUpgrading(imei, deviceId);
        super.channelInactive(ctx);
    }
}
