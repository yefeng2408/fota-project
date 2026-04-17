package com.yef.service;

import com.yef.dto.PlatformUpgradeRequest;
import com.yef.exception.FotaProtocolException;
import com.yef.protocol.UpgradeRequestMessage;
import com.yef.session.DeviceSession;
import com.yef.session.SessionManager;
import io.netty.channel.Channel;
import org.springframework.stereotype.Service;

/**
 * @description: TODO
 * @author: 叶丰
 * @date: 2026/04/16 09:43
 */
@Service
public class GatewayUpgradeDispatchService {

    private final SessionManager sessionManager;

    public GatewayUpgradeDispatchService(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void sendUpgradeRequest(PlatformUpgradeRequest req) {
        DeviceSession session = sessionManager.getByImei(req.getImei());
        if (session == null || session.getChannel() == null || !session.getChannel().isActive()) {
            throw new FotaProtocolException("设备不在线，无法下发升级请求");
        }

        UpgradeRequestMessage message = new UpgradeRequestMessage(
                req.getImei(),
                req.getTaskId(),
                req.getFirmwareId(),
                req.getFirmwareNameLen(),
                req.getFirmwareName(),
                req.getFirmwareVersionLen(),
                req.getFirmwareVersionName(),
                req.getChunkCount(),
                req.getChunkSize(),
                req.getFileSize(),
                hexMd5ToBytes(req.getMd5())
        );
        Channel channel = session.getChannel();
        channel.writeAndFlush(message);
    }

    private byte[] hexMd5ToBytes(String md5) {
        if (md5 == null || md5.length() != 32) {
            throw new FotaProtocolException("md5 must be 32 hex chars");
        }
        byte[] bytes = new byte[16];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(md5.charAt(i * 2), 16);
            int low = Character.digit(md5.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new FotaProtocolException("md5 contains non-hex chars");
            }
            bytes[i] = (byte) ((high << 4) + low);
        }
        return bytes;
    }

   /* public void sendCancelUpgradeRequest(GatewayCancelUpgradeRequest request) {
        DeviceSession session = sessionManager.getByImei(request.getImei());
        if (session == null || session.getChannel() == null || !session.getChannel().isActive()) {
            throw new GatewayBusinessException("设备不在线，无法下发取消升级指令");
        }
        // TODO: 组装 CancelUpgradeMessage(0x87)，然后 writeAndFlush
    }*/
}
