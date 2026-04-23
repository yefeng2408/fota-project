package com.yef.service;

import com.yef.dto.PlatformUpgradeRequest;
import com.yef.exception.FotaProtocolException;
import com.yef.protocol.UpgradeRequestMessage;
import com.yef.session.DeviceSession;
import com.yef.session.SessionManager;
import io.netty.channel.Channel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * @description: TODO
 * @author: 叶丰
 * @date: 2026/04/16 09:43
 */
@Service
public class GatewayUpgradeDispatchService {

    /**
     * 升级运行态 设备基础信息 设备网关所用的key，用于分包过程中的【高频写操作】
     */
    private static final String UPGRADE_RUNTIME_KEY_PREFIX = "fota:upgrade:runtime:";

    private final SessionManager sessionManager;
    private final StringRedisTemplate redisTemplate;
    private final DeviceUpgradeLockService deviceUpgradeLockService;

    public GatewayUpgradeDispatchService(SessionManager sessionManager,
                                         StringRedisTemplate redisTemplate,
                                         DeviceUpgradeLockService deviceUpgradeLockService) {
        this.sessionManager = sessionManager;
        this.redisTemplate = redisTemplate;
        this.deviceUpgradeLockService = deviceUpgradeLockService;
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
                req.getTotalPacket(),
                req.getChunkSize(),
                req.getFileSize(),
                hexMd5ToBytes(req.getMd5())
        );
        Channel channel = session.getChannel();
        channel.writeAndFlush(message);
        //网关收到平台下发0x81消息。初始化记录分包的key【fota:upgrade:runtime:{imei}】
        Map<String, Object> runtimeHash = new HashMap<>();
        runtimeHash.put("imei", nullToEmpty(req.getImei()));
        runtimeHash.put("taskId", nullToEmpty(String.valueOf(req.getTaskId())));
        runtimeHash.put("lockToken", nullToEmpty(req.getLockToken()));
        runtimeHash.put("packetNo", String.valueOf(0));
        runtimeHash.put("status", "UPGRADE_REQUESTED");
        runtimeHash.put("totalPacket", nullToEmpty(String.valueOf(req.getTotalPacket())));
        runtimeHash.put("chunkSize", nullToEmpty(String.valueOf(req.getChunkSize())));
        runtimeHash.put("startAt",String.valueOf(System.currentTimeMillis()));
        runtimeHash.put("endAt",String.valueOf(0));
        runtimeHash.put("progress",String.valueOf(0));
        runtimeHash.put("fileSize", String.valueOf(nullToEmpty(req.getFileSize())));
        runtimeHash.put("md5", nullToEmpty(req.getMd5()));
        runtimeHash.put("bucketName",req.getBucketName());
        runtimeHash.put("objectName",req.getObjectName());
        runtimeHash.put("targetFirmwareVersion", req.getFirmwareVersionName());

        redisTemplate.opsForHash().putAll(UPGRADE_RUNTIME_KEY_PREFIX+req.getImei(),runtimeHash);
        deviceUpgradeLockService.markActive(req.getImei());

        /**
         * 网关级别的key，用于分包过程中的【高频写操作】
         * TODO fota:upgrade:runtime:{imei}
         * taskId=90001
         * status=UPGRADING
         * currentPacketNo=128
         * ackedPacketCount=128
         * totalPacket=3000
         * chunkSize=1024
         * progress=4
         * lastPacketAt=1710000000000
         * packetTime=1710000000000
         * version=2
         * startedAt=1710000000000
         * firmwareId=5001
         * fileSize=4500000
         * md5=54ccbea961b3df0f19b99c8c4...
         * seqId=208
         */
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


    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
