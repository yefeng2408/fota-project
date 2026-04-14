package com.yef.codec;

import com.yef.protocol.AckMessage;
import com.yef.protocol.CancelUpgradeMessage;
import com.yef.protocol.DeviceBootUpMessage;
import com.yef.protocol.FailMessage;
import com.yef.protocol.FotaPacketFrame;
import com.yef.protocol.FotaProtocolConstants;
import com.yef.protocol.FotaProtocolException;
import com.yef.protocol.HeartbeatMessage;
import com.yef.protocol.UpgradePacketMessage;
import com.yef.protocol.UpgradeRequestMessage;
import com.yef.protocol.UpgradeResultMessage;
import com.yef.util.Crc16Utils;
import com.yef.util.ProtocolBodyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

/**
 * @description:
 * @author: 叶丰
 * @date: 2026/4/11 22:48
 */
public class FotaMessageDecoder extends MessageToMessageDecoder<FotaPacketFrame> {

    @Override
    protected void decode(ChannelHandlerContext ctx, FotaPacketFrame frame, List<Object> out) {

        byte[] crcPayload = ProtocolBodyUtils.buildCrcPayload(frame.getVersion(), frame.getBody().length,
                frame.getImei(), frame.getTimestamp(), frame.getSeqId(), frame.getMessageType(), frame.getBody());
        int calculatedCrc16 = Crc16Utils.calculate(crcPayload);
        if (calculatedCrc16 != frame.getCrc16()) {
            throw new FotaProtocolException("crc16 mismatch, expected=" + frame.getCrc16() + ", calculated=" + calculatedCrc16);
        }

        ByteBuf body = Unpooled.wrappedBuffer(frame.getBody());
        try {
            switch (frame.getMessageType()) {
                case FotaProtocolConstants.MSG_DEVICE_BOOT_UP:
                    out.add(decodeBootUp(frame, body));
                    break;
                case FotaProtocolConstants.MSG_HEARTBEAT:
                    out.add(new HeartbeatMessage(frame.getImei()));
                    break;
                case FotaProtocolConstants.MSG_UPGRADE_RESULT:
                    out.add(decodeUpgradeResult(frame, body));
                    break;
                case FotaProtocolConstants.MSG_CANCEL_UPGRADE:
                    out.add(decodeCancel(frame, body));
                    break;
                case FotaProtocolConstants.MSG_UPGRADE_REQUEST:
                    out.add(decodeUpgradeRequest(frame, body));
                    break;
                case FotaProtocolConstants.MSG_UPGRADE_PACKET:
                    out.add(decodeUpgradePacket(frame, body));
                    break;
                case FotaProtocolConstants.MSG_ACK:
                    out.add(decodeAck(frame, body));
                    break;
                case FotaProtocolConstants.MSG_FAIL:
                    out.add(decodeFail(frame, body));
                    break;
                default:
                    throw new FotaProtocolException("unsupported message type: " + frame.getMessageType());
            }
        } finally {
            body.release();
        }
    }

    private DeviceBootUpMessage decodeBootUp(FotaPacketFrame frame, ByteBuf body) {
        String firmwareVersion = ProtocolBodyUtils.readUtf8WithShortLength(body);
        String deviceType = ProtocolBodyUtils.readUtf8WithShortLength(body);
        return new DeviceBootUpMessage(frame.getImei(), firmwareVersion, deviceType);
    }

    private UpgradeRequestMessage decodeUpgradeRequest(FotaPacketFrame frame, ByteBuf body) {
        long taskId = body.readLong();
        long firmwareId = body.readLong();
        int totalPacket = body.readInt();
        int chunkSize = body.readInt();
        long fileSize = body.readLong();
        byte[] md5 = new byte[16];
        body.readBytes(md5);
        return new UpgradeRequestMessage(frame.getImei(), taskId, firmwareId, totalPacket, chunkSize, fileSize, md5);
    }

    private UpgradePacketMessage decodeUpgradePacket(FotaPacketFrame frame, ByteBuf body) {
        long taskId = body.readLong();
        int packetNo = body.readInt();
        int totalPacket = body.readInt();
        int chunkLength = body.readInt();
        if (chunkLength < 0 || body.readableBytes() < chunkLength) {
            throw new FotaProtocolException("invalid chunk length: " + chunkLength);
        }
        byte[] chunkData = new byte[chunkLength];
        body.readBytes(chunkData);
        return new UpgradePacketMessage(frame.getImei(), taskId, packetNo, totalPacket, chunkData);
    }

    private AckMessage decodeAck(FotaPacketFrame frame, ByteBuf body) {
        long taskId = body.readLong();
        int packetNo = body.readInt();
        byte ackType = body.readByte();
        return new AckMessage(frame.getImei(), taskId, packetNo, ackType);
    }

    private FailMessage decodeFail(FotaPacketFrame frame, ByteBuf body) {
        long taskId = body.readLong();
        int packetNo = body.readInt();
        int errorCode = body.readUnsignedShort();
        return new FailMessage(frame.getImei(), taskId, packetNo, errorCode);
    }

    private UpgradeResultMessage decodeUpgradeResult(FotaPacketFrame frame, ByteBuf body) {
        String bodyImei = ProtocolBodyUtils.readFixedImei(body);
        long taskId = body.readLong();
        byte result = body.readByte();
        int errorCode = body.readUnsignedShort();
        int costTime = body.readInt();
        return new UpgradeResultMessage(bodyImei == null || bodyImei.isBlank() ? frame.getImei() : bodyImei,
                taskId, result, errorCode, costTime);
    }

    private CancelUpgradeMessage decodeCancel(FotaPacketFrame frame, ByteBuf body) {
        String bodyImei = ProtocolBodyUtils.readFixedImei(body);
        long taskId = body.readLong();
        byte reason = body.readByte();
        return new CancelUpgradeMessage(bodyImei == null || bodyImei.isBlank() ? frame.getImei() : bodyImei, taskId, reason);
    }
}
