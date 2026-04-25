package com.yef.codec;

import com.yef.exception.FotaProtocolException;
import com.yef.protocol.*;
import com.yef.protocol.outMsg.DeviceBootUpMessageAck;
import com.yef.util.Crc16Utils;
import com.yef.util.ProtocolBodyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * @description: 网关出站编码
 * @author: 叶丰
 * @date: 2026/4/17 18:22
 */
public class FotaMessageEncoder extends MessageToByteEncoder<FotaMessage> {

    private static final java.util.concurrent.atomic.AtomicInteger SEQ = new java.util.concurrent.atomic.AtomicInteger(0);

    @Override
    protected void encode(ChannelHandlerContext ctx, FotaMessage msg, ByteBuf out) {
        byte[] body = encodeBody(msg);
        byte messageType = msg.getMessageType();
        long timestamp = System.currentTimeMillis();
        int seqId = SEQ.updateAndGet(value -> value >= 65535 ? 1 : value + 1);
        byte[] crcPayload = ProtocolBodyUtils.buildCrcPayload(FotaProtocolConstants.VERSION, body.length,
                msg.imei(), timestamp, seqId, messageType, body);
        int crc16 = Crc16Utils.calculate(crcPayload);

        out.writeByte(FotaProtocolConstants.HEAD);
        out.writeByte(FotaProtocolConstants.VERSION);
        out.writeInt(body.length);
        ProtocolBodyUtils.writeFixedImei(out, msg.imei());
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
            if (msg instanceof DeviceBootUpMessageAck) {
                DeviceBootUpMessageAck message = (DeviceBootUpMessageAck) msg;
                body.writeLong(message.taskId());
                body.writeByte(message.refMessageType());
                body.writeByte(message.ackStatus());
                body.writeByte(message.reasonCode());
            } else if (msg instanceof UpgradeRequestMessage) {
                UpgradeRequestMessage message = (UpgradeRequestMessage) msg;
                byte[] md5 = message.getMd5();
                if (md5.length != 16) {
                    throw new FotaProtocolException("upgrade request md5 must be 16 bytes, actual=" + md5.length);
                }
                body.writeLong(message.getTaskId());
                body.writeLong(message.getFirmwareId());
                ProtocolBodyUtils.writeUtf8WithByteLength(body, message.getFirmwareName());
                ProtocolBodyUtils.writeUtf8WithByteLength(body, message.getFirmwareVersionName());
                body.writeInt(message.getTotalPacket());
                body.writeInt(message.getChunkSize());
                body.writeLong(message.getFileSize());
                body.writeBytes(md5);
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
            }  else if (msg instanceof CancelUpgradeMessage) {
                CancelUpgradeMessage message = (CancelUpgradeMessage) msg;
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
