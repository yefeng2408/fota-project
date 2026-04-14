package com.yef.codec;

import com.yef.protocol.FotaProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.concurrent.atomic.AtomicInteger;

public class FotaMessageEncoder extends MessageToByteEncoder<FotaProtocol.Message> {

    private static final AtomicInteger SEQ = new AtomicInteger();

    @Override
    protected void encode(ChannelHandlerContext ctx, FotaProtocol.Message msg, ByteBuf out) {
        byte[] body = encodeBody(msg);
        long timestamp = System.currentTimeMillis();
        int seqId = SEQ.updateAndGet(value -> value >= 65535 ? 1 : value + 1);
        byte[] crcPayload = FotaProtocol.buildCrcPayload(FotaProtocol.VERSION, body.length, msg.imei(), timestamp,
                seqId, msg.messageType(), body);
        int crc16 = FotaProtocol.crc16(crcPayload);
        out.writeByte(FotaProtocol.HEAD);
        out.writeByte(FotaProtocol.VERSION);
        out.writeInt(body.length);
        FotaProtocol.writeFixedImei(out, msg.imei());
        out.writeLong(timestamp);
        out.writeShort(seqId);
        out.writeByte(msg.messageType());
        out.writeBytes(body);
        out.writeShort(crc16);
        out.writeByte(FotaProtocol.TAIL);
    }

    private byte[] encodeBody(FotaProtocol.Message msg) {
        ByteBuf body = Unpooled.buffer();
        try {
            if (msg instanceof FotaProtocol.DeviceBootUp bootUp) {
                FotaProtocol.writeString(body, bootUp.firmwareVersion());
                FotaProtocol.writeString(body, bootUp.deviceType());
            } else if (msg instanceof FotaProtocol.Heartbeat) {
                // empty body
            } else if (msg instanceof FotaProtocol.Ack ack) {
                body.writeLong(ack.taskId());
                body.writeInt(ack.packetNo());
                body.writeByte(ack.ackType());
            } else if (msg instanceof FotaProtocol.Fail fail) {
                body.writeLong(fail.taskId());
                body.writeInt(fail.packetNo());
                body.writeShort(fail.errorCode());
            } else if (msg instanceof FotaProtocol.UpgradeResult result) {
                FotaProtocol.writeFixedImei(body, result.imei());
                body.writeLong(result.taskId());
                body.writeByte(result.result());
                body.writeShort(result.errorCode());
                body.writeInt(result.costTime());
            }
            byte[] bytes = new byte[body.readableBytes()];
            body.readBytes(bytes);
            return bytes;
        } finally {
            body.release();
        }
    }
}
