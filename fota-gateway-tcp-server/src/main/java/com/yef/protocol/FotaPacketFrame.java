package com.yef.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCounted;

/**
 *  协议帧对象
 * @param version
 * @param imei
 * @param timestamp
 * @param seqId
 * @param messageType
 * @param body
 * @param crc16
 */
public record FotaPacketFrame(
        byte version,
        long bodyLength,
        String imei,
        long timestamp,
        int seqId,
        byte messageType,
        ByteBuf body,
        int crc16) implements ReferenceCounted {

    public FotaPacketFrame(byte version, long bodyLength, String imei, long timestamp,
                           int seqId, byte messageType, ByteBuf body, int crc16) {
        this.version = version;
        this.bodyLength = bodyLength;
        this.imei = imei;
        this.timestamp = timestamp;
        this.seqId = seqId;
        this.messageType = messageType;
        this.body = body == null ? Unpooled.EMPTY_BUFFER : body;
        this.crc16 = crc16;
    }

    @Override
    public int refCnt() {
        return body.refCnt();
    }

    @Override
    public FotaPacketFrame retain() {
        body.retain();
        return this;
    }

    @Override
    public FotaPacketFrame retain(int increment) {
        body.retain(increment);
        return this;
    }

    @Override
    public FotaPacketFrame touch() {
        body.touch();
        return this;
    }

    @Override
    public FotaPacketFrame touch(Object hint) {
        body.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return body.release();
    }

    @Override
    public boolean release(int decrement) {
        return body.release(decrement);
    }
}
