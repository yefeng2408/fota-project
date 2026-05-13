package com.yef.handler;

import com.yef.producer.DeviceUpgradeEventPushClient;
import com.yef.protocol.ChannelAttributes;
import com.yef.protocol.FotaMessage;
import com.yef.req.DisconnectEventRequest;
import com.yef.req.EntryUpgradingEventRequest;
import com.yef.service.DeviceKeepOnlineService;
import com.yef.service.UpgradeExecutor;
import com.yef.session.DeviceSession;
import com.yef.session.SessionManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@ChannelHandler.Sharable
public class DeviceIdentityHandler extends ChannelInboundHandlerAdapter {

    private final SessionManager sessionManager;
    private final DeviceKeepOnlineService deviceOnlineService;
    private final DeviceUpgradeEventPushClient deviceUpgradeEventPushClient;
    private final StringRedisTemplate redisTemplate;

    public DeviceIdentityHandler(SessionManager sessionManager,
                                 DeviceKeepOnlineService deviceOnlineService,
                                 DeviceUpgradeEventPushClient deviceUpgradeEventPushClient,
                                 StringRedisTemplate redisTemplate) {
        this.sessionManager = sessionManager;
        this.deviceOnlineService = deviceOnlineService;
        this.deviceUpgradeEventPushClient = deviceUpgradeEventPushClient;
        this.redisTemplate = redisTemplate;
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
        log.warn(">>>>>>>>>>>>>>>设备主动断开[DeviceIdentityHandler] close channel, imei={}", ctx.channel().attr(ChannelAttributes.IMEI).get());
        DeviceSession deviceSession = sessionManager.getByChannel(ctx.channel());

        if (deviceSession != null) {
            sessionManager.remove(ctx.channel());
        }
        sessionManager.remove(ctx.channel());
        log.info("------->channelInactive|CURRENT_TASK_ID:{}", ctx.channel().attr(ChannelAttributes.CURRENT_TASK_ID).get());
        //存在升级任务中的设备掉线，则推送一次设备掉线的状态事件
        String runtimeKey = UpgradeExecutor.UPGRADE_RUNTIME_KEY_PREFIX + ctx.channel().attr(ChannelAttributes.IMEI).get();
        String status = String.valueOf(redisTemplate.opsForHash().get(runtimeKey, "status"));
        if ("UPGRADING".equals(status) || "UPGRADE_REQUESTED".equals(status)) {
            if (ctx.channel().attr(ChannelAttributes.CURRENT_TASK_ID).get() != null) {
                DisconnectEventRequest request = new DisconnectEventRequest();
                request.setImei(ctx.channel().attr(ChannelAttributes.IMEI).get());
                request.setTaskId(ctx.channel().attr(ChannelAttributes.CURRENT_TASK_ID).get());
                request.setUpgradeStatus("DISCONNECT");
                deviceUpgradeEventPushClient.pushDeviceDisconnectStatus(request);

                redisTemplate.opsForHash().put(runtimeKey, "status", "DISCONNECT");
            }
        }
        ctx.fireChannelInactive();
    }
}
