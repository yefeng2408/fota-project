package com.yef.fota.dto.user;

import java.util.List;
import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSaveRequest {

    private Long id;

    @NotBlank
    private String username;

    @NotBlank
    private String phone;

    private String password;

    private List<Long> deviceGroupIds;
}
