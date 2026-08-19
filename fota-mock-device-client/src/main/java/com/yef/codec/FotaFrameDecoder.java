package com.yef.codec;

import com.yef.protocol.FotaProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;

@Slf4j
@Component
@ChannelHandler.Sharable
public class FotaFrameDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int frameLength = in.readableBytes();
        if (frameLength < FotaProtocol.MIN_FRAME_LENGTH) {
            throw new IllegalArgumentException("frame too short: " + frameLength);
        }

        byte head = in.readByte();
        if (head != FotaProtocol.HEAD) {
            throw new IllegalArgumentException("invalid frame head: " + String.format("0x%02X", head));
        }

        byte version = in.readByte();
        int bodyLength = in.readInt();
        if (bodyLength < 0 || bodyLength > FotaProtocol.MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("invalid body length: " + bodyLength);
        }

        int expectedFrameLength = FotaProtocol.MIN_FRAME_LENGTH + bodyLength;
        if (frameLength != expectedFrameLength) {
            throw new IllegalArgumentException("frame length mismatch, expected=" + expectedFrameLength + ", actual=" + frameLength);
        }

        String imei = FotaProtocol.readFixedImei(in);
        long timestamp = in.readLong();
        int seqId = in.readUnsignedShort();
        byte messageType = in.readByte();
        byte[] body = new byte[bodyLength];
        in.readBytes(body);
        int crc16 = in.readUnsignedShort();
        byte tail = in.readByte();
        if (tail != FotaProtocol.TAIL) {
            throw new IllegalArgumentException("invalid frame tail: " + String.format("0x%02X", tail));
        }

        out.add(new FotaProtocol.Frame(version, imei, timestamp, seqId, messageType, body, crc16));
    }
}
