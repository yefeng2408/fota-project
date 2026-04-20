package com.yef.handler;

import com.yef.protocol.AckMessage;
import com.yef.protocol.ChannelAttributes;
import com.yef.protocol.DeviceBootUpMessage;
import com.yef.protocol.FailMessage;
import com.yef.protocol.HeartbeatMessage;
import com.yef.protocol.UpgradeResultMessage;
import com.yef.service.UpgradeExecutor;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.springframework.stereotype.Component;

/**
 * @description: 统一写出站消息
 * @author: 叶丰
 * @date: 2026/4/17 18:24
 */
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
        if (msg instanceof DeviceBootUpMessage) {
            upgradeExecutor.onDeviceBootUp((DeviceBootUpMessage) msg, deviceId);
        } else if (msg instanceof HeartbeatMessage) {
            System.out.println("[UpgradeDispatchHandler] heartbeat ignored, deviceId=" + deviceId);
        } else if (msg instanceof AckMessage) {
            AckMessage ack = (AckMessage) msg;
            System.out.println("[UpgradeDispatchHandler] device ACK ignored, imei=" + ack.imei()
                    + ", deviceId=" + deviceId
                    + ", taskId=" + ack.getTaskId()
                    + ", packetNo=" + ack.getPacketNo());
            if(ack.getAckType()==1 || ack.getAckType()==2){
                //收到0x81的应答。开始对固件进行分包下发0x82消息
                upgradeExecutor.sendSpiltPacket(ack);
            }
        } else if (msg instanceof FailMessage) {
            FailMessage fail = (FailMessage) msg;
            System.out.println("[UpgradeDispatchHandler] device FAIL ignored, imei=" + fail.imei()
                    + ", deviceId=" + deviceId
                    + ", taskId=" + fail.getTaskId()
                    + ", packetNo=" + fail.getPacketNo()
                    + ", errorCode=" + fail.getErrorCode());
        } else if (msg instanceof UpgradeResultMessage) {
            upgradeExecutor.handleUpgradeResult((UpgradeResultMessage) msg, deviceId);
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
