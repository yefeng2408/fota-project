package com.yef.codec;

import com.yef.exception.FotaProtocolException;
import com.yef.protocol.AckMessage;
import com.yef.protocol.CancelUpgradeMessage;
import com.yef.protocol.DeviceBootUpMessage;
import com.yef.protocol.FailMessage;
import com.yef.protocol.FotaMessage;
import com.yef.protocol.FotaProtocolConstants;
import com.yef.protocol.HeartbeatMessage;
import com.yef.protocol.UpgradePacketMessage;
import com.yef.protocol.UpgradeRequestMessage;
import com.yef.protocol.UpgradeResultMessage;
import com.yef.util.Crc16Utils;
import com.yef.util.ProtocolBodyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class FotaMessageEncoder extends MessageToByteEncoder<FotaMessage> {

    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger(0);

    @Override
    protected void encode(ChannelHandlerContext ctx, FotaMessage msg, ByteBuf out) {
        byte[] body = encodeBody(msg);
        byte messageType = msg.getMessageType();
        long timestamp = System.currentTimeMillis();
        int seqId = SEQ.updateAndGet(value -> value >= 65535 ? 1 : value + 1);
        byte[] crcPayload = ProtocolBodyUtils.buildCrcPayload(FotaProtocolConstants.VERSION, body.length,
                msg.getImei(), timestamp, seqId, messageType, body);
        int crc16 = Crc16Utils.calculate(crcPayload);

        out.writeByte(FotaProtocolConstants.HEAD);
        out.writeByte(FotaProtocolConstants.VERSION);
        out.writeInt(body.length);
        ProtocolBodyUtils.writeFixedImei(out, msg.getImei());
        out.writeLong(timestamp);
        out.writeShort(seqId);
        out.writeByte(messageType);
        out.writeBytes(body);
        out.writeShort(crc16);
        out.writeByte(FotaProtocolConstants.TAIL);
    }

    private byte[] encodeBody(FotaMessage msg) {
        ByteBuf body = Unpooled.buffer();
        try {
            if (msg instanceof DeviceBootUpMessage) {
                DeviceBootUpMessage message = (DeviceBootUpMessage) msg;
                ProtocolBodyUtils.writeUtf8WithShortLength(body, message.getFirmwareVersion());
                ProtocolBodyUtils.writeUtf8WithShortLength(body, message.getDeviceType());
            } else if (msg instanceof HeartbeatMessage) {
                // Heartbeat body is empty.
            } else if (msg instanceof UpgradeRequestMessage) {
                UpgradeRequestMessage message = (UpgradeRequestMessage) msg;
                body.writeLong(message.getTaskId());
                body.writeLong(message.getFirmwareId());
                body.writeInt(message.getTotalPacket());
                body.writeInt(message.getChunkSize());
                body.writeLong(message.getFileSize());
                body.writeBytes(message.getMd5());
            } else if (msg instanceof UpgradePacketMessage) {
                UpgradePacketMessage message = (UpgradePacketMessage) msg;
                byte[] chunkData = message.getChunkData();
                body.writeLong(message.getTaskId());
                body.writeInt(message.getPacketNo());
                body.writeInt(message.getTotalPacket());
                body.writeInt(chunkData.length);
                body.writeBytes(chunkData);
            } else if (msg instanceof AckMessage) {
                AckMessage message = (AckMessage) msg;
                body.writeLong(message.getTaskId());
                body.writeInt(message.getPacketNo());
                body.writeByte(message.getAckType());
            } else if (msg instanceof FailMessage) {
                FailMessage message = (FailMessage) msg;
                body.writeLong(message.getTaskId());
                body.writeInt(message.getPacketNo());
                body.writeShort(message.getErrorCode());
            } else if (msg instanceof UpgradeResultMessage) {
                UpgradeResultMessage message = (UpgradeResultMessage) msg;
                ProtocolBodyUtils.writeFixedImei(body, message.getImei());
                body.writeLong(message.getTaskId());
                body.writeByte(message.getResult());
                body.writeShort(message.getErrorCode());
                body.writeInt(message.getCostTime());
            } else if (msg instanceof CancelUpgradeMessage) {
                CancelUpgradeMessage message = (CancelUpgradeMessage) msg;
                ProtocolBodyUtils.writeFixedImei(body, message.getImei());
                body.writeLong(message.getTaskId());
                body.writeByte(message.getReason());
            } else {
                throw new FotaProtocolException("unsupported outbound message: " + msg.getClass().getName());
            }
            return ProtocolBodyUtils.toByteArray(body);
        } finally {
            body.release();
        }
    }
}
