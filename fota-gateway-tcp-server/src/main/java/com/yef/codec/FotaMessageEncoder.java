package com.yef.codec;

import com.yef.protocol.AckMessage;
import com.yef.protocol.DeviceRegisterMessage;
import com.yef.protocol.FailMessage;
import com.yef.protocol.FotaMessage;
import com.yef.protocol.FotaProtocolConstants;
import com.yef.protocol.FotaProtocolException;
import com.yef.protocol.UpgradePacketMessage;
import com.yef.protocol.UpgradeRequestMessage;
import com.yef.util.Crc16Utils;
import com.yef.util.ProtocolBodyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class FotaMessageEncoder extends MessageToByteEncoder<FotaMessage> {

    @Override
    protected void encode(ChannelHandlerContext ctx, FotaMessage msg, ByteBuf out) {
        byte[] body = encodeBody(msg);
        byte messageType = msg.getMessageType();
        byte[] crcPayload = ProtocolBodyUtils.buildCrcPayload(FotaProtocolConstants.VERSION, messageType, body);
        int crc16 = Crc16Utils.calculate(crcPayload);

        out.writeByte(FotaProtocolConstants.HEAD);
        out.writeByte(FotaProtocolConstants.VERSION);
        out.writeInt(body.length);
        out.writeByte(messageType);
        out.writeBytes(body);
        out.writeShort(crc16);
        out.writeByte(FotaProtocolConstants.TAIL);
    }

    private byte[] encodeBody(FotaMessage msg) {
        ByteBuf body = Unpooled.buffer();
        try {
            if (msg instanceof DeviceRegisterMessage) {
                DeviceRegisterMessage message = (DeviceRegisterMessage) msg;
                ProtocolBodyUtils.writeUtf8WithShortLength(body, message.getImei());
                ProtocolBodyUtils.writeUtf8WithShortLength(body, message.getFirmwareVersion());
                ProtocolBodyUtils.writeUtf8WithShortLength(body, message.getDeviceType());
            } else if (msg instanceof UpgradeRequestMessage) {
                UpgradeRequestMessage message = (UpgradeRequestMessage) msg;
                ProtocolBodyUtils.writeImei(body, message.getImei());
                body.writeLong(message.getTaskId());
                body.writeLong(message.getFirmwareId());
                body.writeInt(message.getTotalPacket());
                body.writeInt(message.getChunkSize());
                body.writeLong(message.getFileSize());
                body.writeBytes(message.getMd5());
            } else if (msg instanceof UpgradePacketMessage) {
                UpgradePacketMessage message = (UpgradePacketMessage) msg;
                byte[] chunkData = message.getChunkData();
                ProtocolBodyUtils.writeImei(body, message.getImei());
                body.writeLong(message.getTaskId());
                body.writeInt(message.getPacketNo());
                body.writeInt(message.getTotalPacket());
                body.writeInt(chunkData.length);
                body.writeBytes(chunkData);
            } else if (msg instanceof AckMessage) {
                AckMessage message = (AckMessage) msg;
                ProtocolBodyUtils.writeImei(body, message.getImei());
                body.writeLong(message.getTaskId());
                body.writeInt(message.getPacketNo());
                body.writeByte(message.getStatus());
            } else if (msg instanceof FailMessage) {
                FailMessage message = (FailMessage) msg;
                ProtocolBodyUtils.writeImei(body, message.getImei());
                body.writeLong(message.getTaskId());
                body.writeInt(message.getPacketNo());
                body.writeShort(message.getErrorCode());
            } else {
                throw new FotaProtocolException("unsupported outbound message: " + msg.getClass().getName());
            }
            return ProtocolBodyUtils.toByteArray(body);
        } finally {
            body.release();
        }
    }
}
