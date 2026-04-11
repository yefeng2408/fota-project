package com.yef.codec;

import com.yef.protocol.FotaPacketFrame;
import com.yef.protocol.FotaProtocolConstants;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

public class FotaFrameDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (in.readableBytes() >= FotaProtocolConstants.FRAME_MIN_LENGTH) {
            int headIndex = findHead(in);
            if (headIndex < 0) {
                in.skipBytes(in.readableBytes());
                return;
            }
            if (headIndex > in.readerIndex()) {
                in.skipBytes(headIndex - in.readerIndex());
            }
            if (in.readableBytes() < FotaProtocolConstants.FRAME_MIN_LENGTH) {
                return;
            }

            in.markReaderIndex();
            in.skipBytes(1);
            byte version = in.readByte();
            int bodyLength = in.readInt();
            byte messageType = in.readByte();

            if (bodyLength < 0 || bodyLength > FotaProtocolConstants.MAX_BODY_LENGTH) {
                in.resetReaderIndex();
                in.skipBytes(1);
                continue;
            }

            int remainingLength = bodyLength + 2 + 1;
            if (in.readableBytes() < remainingLength) {
                in.resetReaderIndex();
                return;
            }

            byte[] body = new byte[bodyLength];
            in.readBytes(body);
            int crc16 = in.readUnsignedShort();
            byte tail = in.readByte();
            if (tail != FotaProtocolConstants.TAIL) {
                in.resetReaderIndex();
                in.skipBytes(1);
                continue;
            }

            out.add(new FotaPacketFrame(version, messageType, body, crc16));
        }
    }

    private int findHead(ByteBuf in) {
        for (int i = in.readerIndex(); i < in.writerIndex(); i++) {
            if (in.getByte(i) == FotaProtocolConstants.HEAD) {
                return i;
            }
        }
        return -1;
    }
}
