package com.yef.codec;

import com.yef.exception.FotaProtocolException;
import com.yef.protocol.AckMessage;
import com.yef.protocol.DeviceBootUpMessage;
import com.yef.protocol.FailMessage;
import com.yef.protocol.FotaPacketFrame;
import com.yef.protocol.FotaProtocolConstants;
import com.yef.protocol.HeartbeatMessage;
import com.yef.protocol.UpgradeResultMessage;
import com.yef.util.ProtocolBodyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

/**
 * @description: 处理各个业务类型的上行消息
 * @author: 叶丰
 * @date: 2026/4/11 22:48
 */
public class FotaMessageDecoder extends MessageToMessageDecoder<FotaPacketFrame> {

    @Override
    protected void decode(ChannelHandlerContext ctx, FotaPacketFrame frame, List<Object> out) {
        int calculatedCrc16 = ProtocolBodyUtils.calculateCrc16(
                frame.version(),
                Math.toIntExact(frame.bodyLength()),
                frame.imei(),
                frame.timestamp(),
                frame.seqId(),
                frame.messageType(),
                frame.body());
        if (calculatedCrc16 != frame.crc16()) {
            throw new FotaProtocolException("crc16 mismatch, expected=" + frame.crc16() + ", calculated=" + calculatedCrc16);
        }

        ByteBuf body = frame.body().duplicate();
        switch (frame.messageType()) {
            //0x10
            case FotaProtocolConstants.MSG_DEVICE_BOOT_UP:
                out.add(decodeBootUp(frame, body));
                break;
            //0x05
            case FotaProtocolConstants.MSG_HEARTBEAT:
                out.add(new HeartbeatMessage(frame.imei()));
                break;
            //0x06
            case FotaProtocolConstants.MSG_UPGRADE_RESULT:
                out.add(decodeUpgradeResult(frame, body));
                break;
            //0x03
            case FotaProtocolConstants.MSG_ACK:
                out.add(decodeAck(frame, body));
                break;
            //0x04
            case FotaProtocolConstants.MSG_FAIL:
                out.add(decodeFail(frame, body));
                break;
            default:
                throw new FotaProtocolException("unsupported message type: " + frame.messageType());
        }
    }

    private DeviceBootUpMessage decodeBootUp(FotaPacketFrame frame, ByteBuf body) {
        String firmwareVersion = ProtocolBodyUtils.readUtf8WithByteLength(body);
        String deviceType = ProtocolBodyUtils.readUtf8WithByteLength(body);
        return new DeviceBootUpMessage(frame.imei(), firmwareVersion, deviceType);
    }

    private AckMessage decodeAck(FotaPacketFrame frame, ByteBuf body) {
        long taskId = body.readLong();
        int packetNo = body.readInt();
        byte ackType = body.readByte();
        return new AckMessage(frame.imei(), taskId, packetNo, ackType);
    }

    private FailMessage decodeFail(FotaPacketFrame frame, ByteBuf body) {
        long taskId = body.readLong();
        int packetNo = body.readInt();
        int errorCode = body.readUnsignedByte();
        return new FailMessage(frame.imei(), taskId, packetNo, errorCode);
    }


    private UpgradeResultMessage decodeUpgradeResult(FotaPacketFrame frame, ByteBuf body) {
        long taskId = body.readLong();
        byte result = body.readByte();
        int errorCode = body.readUnsignedByte();
        int costTime = body.readInt();
        return new UpgradeResultMessage(frame.imei(), taskId, result, errorCode, costTime);
    }
}
