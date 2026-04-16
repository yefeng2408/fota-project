package com.yef.codec;

import com.yef.exception.FotaProtocolException;
import com.yef.protocol.FotaPacketFrame;
import com.yef.protocol.FotaProtocolConstants;
import com.yef.protocol.LengthFieldFrameSpec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;

/**
 * @description: 将 LengthFieldBasedFrameDecoder 切出来的完整帧解析成协议帧对象
 * @author: 叶丰
 * @date: 2026/4/11 22:46
 */
public class FotaFrameDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        int frameLength = in.readableBytes();
        if (frameLength < LengthFieldFrameSpec.FRAME_MIN_LENGTH) {
            throw new FotaProtocolException("frame too short: " + frameLength);
        }

        byte head = in.readByte();
        if (head != FotaProtocolConstants.HEAD) {
            throw new FotaProtocolException("invalid frame head: " + String.format("0x%02X", head));
        }

        byte version = in.readByte();
        int bodyLength = in.readInt();
        if (bodyLength < 0 || bodyLength > LengthFieldFrameSpec.MAX_BODY_LENGTH) {
            throw new FotaProtocolException("invalid body length: " + bodyLength);
        }

        int expectedFrameLength = LengthFieldFrameSpec.FRAME_MIN_LENGTH + bodyLength;
        if (frameLength != expectedFrameLength) {
            throw new FotaProtocolException("frame length mismatch, expected=" + expectedFrameLength + ", actual=" + frameLength);
        }

        String imei = com.yef.util.ProtocolBodyUtils.readFixedImei(in);
        long timestamp = in.readLong();
        int seqId = in.readUnsignedShort();
        byte messageType = in.readByte();
        byte[] body = new byte[bodyLength];
        in.readBytes(body);
        int crc16 = in.readUnsignedShort();
        byte tail = in.readByte();
        if (tail != FotaProtocolConstants.TAIL) {
            throw new FotaProtocolException("invalid frame tail: " + String.format("0x%02X", tail));
        }

        out.add(new FotaPacketFrame(version, imei, timestamp, seqId, messageType, body, crc16));
    }
}
