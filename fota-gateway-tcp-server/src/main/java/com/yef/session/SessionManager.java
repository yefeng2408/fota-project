package com.yef.session;

import com.yef.service.DeviceKeepOnlineService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * @description: 连接管理
 * @author: 叶丰
 * @date: 2026/4/23 09:43
 */
@Slf4j
@Component
public class SessionManager {

    /**
     * session 绑定是协议层事件，不是 TCP 建连事件
     * IMEI -> Session
     */
    private final Map<String, DeviceSession> sessionByImei = new ConcurrentHashMap<>();

    /**
     * session 绑定是协议层事件，不是 TCP 建连事件
     * 平台主动下发指令：ChannelId -> Session
     */
    private final Map<ChannelId, DeviceSession> sessionByChannelId = new ConcurrentHashMap<>();


    private final DeviceKeepOnlineService deviceKeepOnlineService;

    public SessionManager(DeviceKeepOnlineService deviceKeepOnlineService) {
        this.deviceKeepOnlineService = deviceKeepOnlineService;
    }


    /**
     * 绑定终端和连接
     * <p>
     * 场景：
     * 1. 平台收到设备ack 合法 消息后，解析出 IMEI号码
     * 2. 将 IMEI号码 和当前 Channel 绑定
     * <p>
     * 规则：
     * 当设备再次上报消息时，如果还是同一个 channel，那么说明终端没有发生重连，这时可以继续沿用当前连接，只需要更新活跃时间。
     * 如果是不同的 channel，说明终端很可能发生了重连或连接切换。此时 bind() 会让新连接接管该终端的 session，并关闭旧连接。
     * 关闭旧连接不一定是因为它在 TCP 层已经彻底不可用，而是因为在业务层我们只允许一个终端保留一条主连接，避免后续下发和状态维护发生混乱。
     */
    public void bind(String imei, Long deviceId, Channel channel) {
        if (imei == null || imei.isBlank()) {
            throw new IllegalArgumentException("imei cannot be blank");
        }
        if (channel == null) {
            throw new IllegalArgumentException("channel cannot be null");
        }

        DeviceSession oldSession = sessionByImei.get(imei);
        if (oldSession != null) {
            // 1. 如果就是同一条连接，并且旧连接仍然活跃，直接沿用旧连接并刷新活跃时间即可
            Channel oldChannel = oldSession.getChannel();
            if (oldChannel == channel && oldChannel.isActive()) {
                oldSession.setLastActiveTime(System.currentTimeMillis());
                return;
            }
            // 2. 如果 oldSession 存在，但旧连接不是当前连接，则关闭旧连接
            if (oldSession.getChannel() != null && oldSession.getChannel() != channel) {
                if (oldChannel.isActive()) {
                    oldChannel.close();
                }
                sessionByChannelId.remove(oldChannel.id());
            }
        }

        // 3. 建立新绑定
        long now = System.currentTimeMillis();
        DeviceSession newSession = new DeviceSession();
        newSession.setImei(imei);
        newSession.setDeviceId(deviceId);
        newSession.setChannel(channel);
        newSession.setConnectTime(now);
        newSession.setLastActiveTime(now);

        sessionByImei.put(imei, newSession);
        sessionByChannelId.put(channel.id(), newSession);

    }

    /**
     * 根据设备imei获取 session
     */
    public DeviceSession getByImei(String imei) {
        return sessionByImei.get(imei);
    }


    /**
     * 根据 channel 获取 session
     */
    public DeviceSession getByChannel(Channel channel) {
        if (channel == null) {
            return null;
        }
        return sessionByChannelId.get(channel.id());
    }

    /**
     * 刷新活跃时间
     */
    public void touch(Channel channel) {
        if (channel == null) {
            return;
        }

        DeviceSession session = sessionByChannelId.get(channel.id());
        if (session != null) {
            session.setLastActiveTime(System.currentTimeMillis());
        }
    }

    /**
     * 根据 channel 移除 session
     * <p>
     * 场景：
     * - channelInactive
     * - exceptionCaught
     * - idle 超时关闭
     */
    public void remove(Channel channel) {

        if (channel == null) {
            return;
        }
        DeviceSession session = sessionByChannelId.remove(channel.id());
        if (session != null) {
            //先置为离线
            deviceKeepOnlineService.onDeviceOffline(session.getDeviceId());
            //再清理session
            sessionByImei.remove(session.getImei());

            log.info("[SessionManager] remove session, imei:{}", session.getImei());
        }
    }



}
