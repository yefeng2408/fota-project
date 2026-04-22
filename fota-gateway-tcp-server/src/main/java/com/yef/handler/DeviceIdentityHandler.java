package com.yef.handler;

import com.yef.protocol.ChannelAttributes;
import com.yef.protocol.FotaMessage;
import com.yef.service.DeviceKeepOnlineService;
import com.yef.session.DeviceSession;
import com.yef.session.SessionManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class DeviceIdentityHandler extends ChannelInboundHandlerAdapter {

    private final SessionManager sessionManager;
    private final DeviceKeepOnlineService deviceOnlineService;

    public DeviceIdentityHandler(SessionManager sessionManager, DeviceKeepOnlineService deviceOnlineService) {
        this.sessionManager = sessionManager;
        this.deviceOnlineService = deviceOnlineService;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FotaMessage)) {
            ctx.fireChannelRead(msg);
            return;
        }

        FotaMessage message = (FotaMessage) msg;
        String imei = message.imei();
        if (imei == null || imei.isBlank()) {
            System.out.println("[DeviceIdentityHandler] message without imei, close channel");
            ctx.close();
            return;
        }

        String currentImei = ctx.channel().attr(ChannelAttributes.IMEI).get();

        Long deviceId = deviceOnlineService.getDeviceId(imei);
        if (currentImei == null || !currentImei.equals(imei)) {

            ctx.channel().attr(ChannelAttributes.IMEI).set(imei);
            ctx.channel().attr(ChannelAttributes.DEVICE_ID).set(deviceId);

            sessionManager.bind(imei, deviceId, ctx.channel());

            //只在首次连接调用
            deviceOnlineService.onDeviceFirstConnect(deviceId);

        } else {
            sessionManager.touch(ctx.channel());
        }
        //每次消息都刷新心跳
        deviceOnlineService.refreshHeartbeat(deviceId);

        if (message.getTaskId() != null) {
            ctx.channel().attr(ChannelAttributes.CURRENT_TASK_ID).set(message.getTaskId());
        }
        ctx.fireChannelRead(msg);
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        DeviceSession deviceSession = sessionManager.getByChannel(ctx.channel());

        if(deviceSession!=null){
            sessionManager.remove(ctx.channel());
        }
        sessionManager.remove(ctx.channel());
        ctx.fireChannelInactive();
    }
}
