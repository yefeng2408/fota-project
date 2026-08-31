package com.yef.protocol;

import com.yef.util.Crc16Utils;
import com.yef.util.ProtocolBodyUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FotaPacketFrameTest {

    @Test
    void calculateCrc16ShouldMatchLegacyByteArrayFlow() {
        byte version = 0x01;
        byte messageType = FotaProtocolConstants.MSG_ACK;
        String imei = "12345678";
        long timestamp = 1_745_512_345_678L;
        int seqId = 1024;
        byte[] bodyBytes = new byte[]{0x11, 0x22, 0x33, 0x44, 0x55};

        ByteBuf body = Unpooled.wrappedBuffer(bodyBytes);
        try {
            byte[] payload = ProtocolBodyUtils.buildCrcPayload(
                    version,
                    bodyBytes.length,
                    imei,
                    timestamp,
                    seqId,
                    messageType,
                    bodyBytes);

            int expected = Crc16Utils.calculate(payload);
            int actual = ProtocolBodyUtils.calculateCrc16(
                    version,
                    body.readableBytes(),
                    imei,
                    timestamp,
                    seqId,
                    messageType,
                    body);

            assertEquals(expected, actual);
            assertEquals(0, body.readerIndex());
        } finally {
            body.release();
        }
    }

    @Test
    void releaseShouldDelegateToBodyBuffer() {
        ByteBuf body = Unpooled.buffer(4).writeInt(1234);
        FotaPacketFrame frame = new FotaPacketFrame((byte) 0x01, body.readableBytes(), "12345678",
                1L, 1, (byte) 0x03, body, 0x1234);

        assertEquals(1, frame.refCnt());
        assertTrue(frame.release());
        assertEquals(0, body.refCnt());
    }
}
