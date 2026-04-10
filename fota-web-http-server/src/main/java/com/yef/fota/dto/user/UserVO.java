package com.yef.fota.dto.user;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserVO {

    private Long id;
    private String username;
    private String phone;
    private List<Long> deviceGroupIds;
    private LocalDateTime createdAt;
}
