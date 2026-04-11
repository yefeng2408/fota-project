package com.yef.codec;

import com.yef.protocol.AckMessage;
import com.yef.protocol.DeviceRegisterMessage;
import com.yef.protocol.FailMessage;
import com.yef.protocol.FotaPacketFrame;
import com.yef.protocol.FotaProtocolConstants;
import com.yef.protocol.FotaProtocolException;
import com.yef.protocol.UpgradePacketMessage;
import com.yef.protocol.UpgradeRequestMessage;
import com.yef.util.Crc16Utils;
import com.yef.util.ProtocolBodyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

public class FotaMessageDecoder extends MessageToMessageDecoder<FotaPacketFrame> {

    @Override
    protected void decode(ChannelHandlerContext ctx, FotaPacketFrame frame, List<Object> out) {
        byte[] crcPayload = ProtocolBodyUtils.buildCrcPayload(frame.getVersion(), frame.getMessageType(), frame.getBody());
        int calculatedCrc16 = Crc16Utils.calculate(crcPayload);
        if (calculatedCrc16 != frame.getCrc16()) {
            throw new FotaProtocolException("crc16 mismatch, expected=" + frame.getCrc16() + ", calculated=" + calculatedCrc16);
        }

        ByteBuf body = Unpooled.wrappedBuffer(frame.getBody());
        try {
            switch (frame.getMessageType()) {
                case FotaProtocolConstants.MSG_DEVICE_REGISTER:
                    out.add(decodeRegister(body));
                    break;
                case FotaProtocolConstants.MSG_UPGRADE_REQUEST:
                    out.add(decodeUpgradeRequest(body));
                    break;
                case FotaProtocolConstants.MSG_UPGRADE_PACKET:
                    out.add(decodeUpgradePacket(body));
                    break;
                case FotaProtocolConstants.MSG_ACK:
                    out.add(decodeAck(body));
                    break;
                case FotaProtocolConstants.MSG_FAIL:
                    out.add(decodeFail(body));
                    break;
                default:
                    throw new FotaProtocolException("unsupported message type: " + frame.getMessageType());
            }
        } finally {
            body.release();
        }
    }

    private DeviceRegisterMessage decodeRegister(ByteBuf body) {
        String imei = ProtocolBodyUtils.readUtf8WithShortLength(body);
        String firmwareVersion = ProtocolBodyUtils.readUtf8WithShortLength(body);
        String deviceType = ProtocolBodyUtils.readUtf8WithShortLength(body);
        return new DeviceRegisterMessage(imei, firmwareVersion, deviceType);
    }

    private UpgradeRequestMessage decodeUpgradeRequest(ByteBuf body) {
        String imei = ProtocolBodyUtils.readImei(body);
        long taskId = body.readLong();
        long firmwareId = body.readLong();
        int totalPacket = body.readInt();
        int chunkSize = body.readInt();
        long fileSize = body.readLong();
        byte[] md5 = new byte[16];
        body.readBytes(md5);
        return new UpgradeRequestMessage(imei, taskId, firmwareId, totalPacket, chunkSize, fileSize, md5);
    }

    private UpgradePacketMessage decodeUpgradePacket(ByteBuf body) {
        String imei = ProtocolBodyUtils.readImei(body);
        long taskId = body.readLong();
        int packetNo = body.readInt();
        int totalPacket = body.readInt();
        int chunkLength = body.readInt();
        if (chunkLength < 0 || body.readableBytes() < chunkLength) {
            throw new FotaProtocolException("invalid chunk length: " + chunkLength);
        }
        byte[] chunkData = new byte[chunkLength];
        body.readBytes(chunkData);
        return new UpgradePacketMessage(imei, taskId, packetNo, totalPacket, chunkData);
    }

    private AckMessage decodeAck(ByteBuf body) {
        String imei = ProtocolBodyUtils.readImei(body);
        long taskId = body.readLong();
        int packetNo = body.readInt();
        byte status = body.readByte();
        return new AckMessage(imei, taskId, packetNo, status);
    }

    private FailMessage decodeFail(ByteBuf body) {
        String imei = ProtocolBodyUtils.readImei(body);
        long taskId = body.readLong();
        int packetNo = body.readInt();
        int errorCode = body.readUnsignedShort();
        return new FailMessage(imei, taskId, packetNo, errorCode);
    }
}
