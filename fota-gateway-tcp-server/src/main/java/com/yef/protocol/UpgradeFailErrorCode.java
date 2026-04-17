package com.yef.protocol;

/**
 * @description: 0x04上行消息异常分类。只是列举了一些常见情况，大部分错误类型可能用不上
 * @author: 叶丰
 * @date: 2026/4/15 16:47
 */
public final class UpgradeFailErrorCode {

    /**
     * CRC16 校验失败
     */
    public static final byte FAIL_ERROR_CRC16 = 1;

    /**
     * 设备掉线 / 连接断开
     */
    public static final byte FAIL_ERROR_DEVICE_OFFLINE = 2;

    /**
     * 分包序号异常
     */
    public static final byte FAIL_ERROR_PACKET_NO_INVALID = 3;

    /**
     * 分包数据长度异常
     */
    public static final byte FAIL_ERROR_CHUNK_LENGTH_INVALID = 4;

    /**
     * 设备写入固件异常
     */
    public static final byte FAIL_ERROR_DEVICE_WRITE = 5;

    /**
     * 设备存储空间不足
     */
    public static final byte FAIL_ERROR_NO_SPACE = 6;

    /**
     * 任务不存在或 taskId 无效
     */
    public static final byte FAIL_ERROR_TASK_INVALID = 7;

    /**
     * 固件 MD5 校验失败
     */
    public static final byte FAIL_ERROR_MD5 = 8;

    /**
     * 升级超时
     */
    public static final byte FAIL_ERROR_TIMEOUT = 9;

    /**
     * 未知异常
     */
    public static final byte FAIL_ERROR_UNKNOWN = 10;
}