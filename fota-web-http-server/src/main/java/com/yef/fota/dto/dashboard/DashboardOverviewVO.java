package com.yef.fota.dto.dashboard;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardOverviewVO {

    private Long totalDevices;
    private Long onlineDevices;
    private Long successTasks;
    private Long failedTasks;
    private List<Map<String, Object>> recentTasks;
    private List<Map<String, Object>> logTrends;
}
