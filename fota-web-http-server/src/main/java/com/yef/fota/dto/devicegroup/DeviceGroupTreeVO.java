package com.yef.fota.dto.devicegroup;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeviceGroupTreeVO {

    private Long id;
    private String label;
    private Long parentId;
    private Integer deviceCount;
    private LocalDateTime createdAt;
    private List<DeviceGroupTreeVO> children = new ArrayList<>();
}
