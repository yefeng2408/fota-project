package com.yef.codec;

import com.yef.protocol.FotaProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

public class FotaMessageDecoder extends MessageToMessageDecoder<FotaProtocol.Frame> {

    @Override
    protected void decode(ChannelHandlerContext ctx, FotaProtocol.Frame frame, List<Object> out) {
        byte[] crcPayload = FotaProtocol.buildCrcPayload(frame.version(), frame.body().length, frame.imei(),
                frame.timestamp(), frame.seqId(), frame.messageType(), frame.body());
        int calculated = FotaProtocol.crc16(crcPayload);
        if (calculated != frame.crc16()) {
            throw new IllegalArgumentException("crc16 mismatch, expected=" + frame.crc16() + ", calculated=" + calculated);
        }

        ByteBuf body = Unpooled.wrappedBuffer(frame.body());
        try {
            if (frame.messageType() == FotaProtocol.UPGRADE_REQUEST) {
                long taskId = body.readLong();
                long firmwareId = body.readLong();

                String firmwareName = FotaProtocol.readStringWithByteLength(body);
                String firmwareVersion = FotaProtocol.readStringWithByteLength(body);

                int totalPacket = body.readInt();
                int chunkSize = body.readInt();
                long fileSize = body.readLong();
                byte[] md5 = new byte[16];
                body.readBytes(md5);
                FotaProtocol.UpgradeRequestDTO upgradeRequestDTO = new FotaProtocol.UpgradeRequestDTO(frame.imei(), taskId, firmwareId, firmwareName, firmwareVersion, totalPacket, chunkSize, fileSize, md5);
                out.add(upgradeRequestDTO);
            } else if (frame.messageType() == FotaProtocol.UPGRADE_PACKET) {
                long taskId = body.readLong();
                int packetNo = body.readInt();
                int totalPacket = body.readInt();
                int chunkLength = body.readInt();
                byte[] chunkData = new byte[chunkLength];
                body.readBytes(chunkData);
                out.add(new FotaProtocol.UpgradePacketDTO(frame.imei(), taskId, packetNo, totalPacket, chunkData));
            } else if (frame.messageType() == FotaProtocol.PLATFORM_ACK) {
                long taskId = body.readLong();
                byte refMessageType = body.readByte();
                byte ackStatus = body.readByte();
                byte reasonCode = body.readByte();
                out.add(new FotaProtocol.PlatformAckDTO(frame.imei(), taskId, refMessageType, ackStatus, reasonCode));
            } else if (frame.messageType() == FotaProtocol.CANCEL_UPGRADE) {
                long taskId = body.readLong();
                byte reason = body.readByte();
                out.add(new FotaProtocol.CancelUpgradeDTO(frame.imei(), taskId, reason));
            }
        } finally {
            body.release();
        }
    }
}
