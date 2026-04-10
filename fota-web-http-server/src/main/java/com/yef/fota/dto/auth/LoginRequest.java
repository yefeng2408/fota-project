package com.yef.fota.dto.auth;

import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    @NotBlank
    private String phone;

    @NotBlank
    private String password;
}
