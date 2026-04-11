package com.yef.service;

import com.yef.protocol.FotaMessage;
import com.yef.session.DeviceSession;
import com.yef.session.SessionManager;
import org.springframework.stereotype.Service;

@Service
public class PacketSender {

    private final SessionManager sessionManager;

    public PacketSender(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public boolean sendToDevice(String imei, FotaMessage message) {
        DeviceSession session = sessionManager.getByImei(imei);
        if (session == null || session.getChannel() == null || !session.getChannel().isActive()) {
            System.out.println("[PacketSender] device offline, imei=" + imei);
            return false;
        }
        session.getChannel().writeAndFlush(message);
        return true;
    }
}
