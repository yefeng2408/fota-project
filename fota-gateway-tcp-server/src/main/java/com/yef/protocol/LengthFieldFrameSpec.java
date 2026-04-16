package com.yef.protocol;

/**
 * @description: 基于LengthFieldBasedFrameDecoder 长度与解码相关常量
 * @author: 叶丰
 * @date: 2026/4/15 16:59
 */
public class LengthFieldFrameSpec {

    /**
     * 最大 body 长度限制
     * <p>
     * 说明：
     * 1. 仅限制业务数据体 body 的最大长度
     * 2. 不包含固定头、crc16、tail
     */
    public static final int MAX_BODY_LENGTH = 1024 * 1024;

    /**
     * 最小完整帧长度
     * <p>
     * 计算：
     * 固定头 25 byte + crc16 2 byte + tail 1 byte = 28 byte
     * <p>
     * 说明：
     * 当 body 长度为 0 时，完整帧最小长度就是 28
     */
    public static final int FRAME_MIN_LENGTH = 28;

    /**
     * 最大完整帧长度
     * <p>
     * 计算：
     * 最大完整帧长度 = 最小帧长度 + 最大 body 长度
     */
    public static final int MAX_FRAME_LENGTH = FRAME_MIN_LENGTH + MAX_BODY_LENGTH;

    /**
     * LengthFieldBasedFrameDecoder 参数：
     * length 字段起始偏移量
     * <p>
     * 说明：
     * 1. head    占 1 byte
     * 2. version 占 1 byte
     * 3. 所以 length 从第 3 个字节开始，offset = 2
     */
    public static final int LENGTH_FIELD_OFFSET = 2;

    /**
     * LengthFieldBasedFrameDecoder 参数：
     * length 字段长度为 4 byte
     */
    public static final int LENGTH_FIELD_LENGTH = 4;

    /**
     * LengthFieldBasedFrameDecoder 参数：
     * 因为 length 字段只表示 body 长度，
     * 所以还需要补上 length 字段之后的固定头字段 + crc16 + tail
     * <p>
     * 补偿长度计算：
     * - imei        8 byte
     * - timestamp   8 byte
     * - seqId       2 byte
     * - messageType 1 byte
     * - crc16       2 byte
     * - tail        1 byte
     * <p>
     * 合计：
     * 8 + 8 + 2 + 1 + 2 + 1 = 22
     */
    public static final int LENGTH_ADJUSTMENT = 8 + 8 + 2 + 1 + 2 + 1;

    /**
     * LengthFieldBasedFrameDecoder 参数：
     * 不剥离任何字节，保持完整帧向后传递
     */
    public static final int INITIAL_BYTES_TO_STRIP = 0;

}