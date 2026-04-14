package com.yef.codec;

import com.yef.protocol.FotaProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;

public class FotaFrameDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (in.readableBytes() >= FotaProtocol.MIN_FRAME_LENGTH) {
            int headIndex = findHead(in);
            if (headIndex < 0) {
                in.skipBytes(in.readableBytes());
                return;
            }
            if (headIndex > in.readerIndex()) {
                in.skipBytes(headIndex - in.readerIndex());
            }
            if (in.readableBytes() < FotaProtocol.MIN_FRAME_LENGTH) {
                return;
            }

            in.markReaderIndex();
            in.skipBytes(1);
            byte version = in.readByte();
            int bodyLength = in.readInt();
            String imei = FotaProtocol.readFixedImei(in);
            long timestamp = in.readLong();
            int seqId = in.readUnsignedShort();
            byte messageType = in.readByte();
            if (bodyLength < 0 || bodyLength > FotaProtocol.MAX_BODY_LENGTH) {
                in.resetReaderIndex();
                in.skipBytes(1);
                continue;
            }
            if (in.readableBytes() < bodyLength + 3) {
                in.resetReaderIndex();
                return;
            }
            byte[] body = new byte[bodyLength];
            in.readBytes(body);
            int crc16 = in.readUnsignedShort();
            byte tail = in.readByte();
            if (tail != FotaProtocol.TAIL) {
                in.resetReaderIndex();
                in.skipBytes(1);
                continue;
            }
            out.add(new FotaProtocol.Frame(version, imei, timestamp, seqId, messageType, body, crc16));
        }
    }

    private int findHead(ByteBuf in) {
        for (int i = in.readerIndex(); i < in.writerIndex(); i++) {
            if (in.getByte(i) == FotaProtocol.HEAD) {
                return i;
            }
        }
        return -1;
    }
}
