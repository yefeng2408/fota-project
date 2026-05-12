package com.yef.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @description:设备升级中，主动断开连接，推送掉线状态
 * @author: 叶丰
 * @date: 2026/5/12 15:16
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DisconnectEventRequest {
    private String imei;
    private Long taskId;
    private String upgradeStatus;
}