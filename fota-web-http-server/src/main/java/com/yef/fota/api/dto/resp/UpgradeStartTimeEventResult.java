package com.yef.fota.api.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @description: 
 * @author: 叶丰
 * @date: 2026/4/21 10:52
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpgradeStartTimeEventResult {
    private Long taskId;
    private LocalDateTime startTime;
}