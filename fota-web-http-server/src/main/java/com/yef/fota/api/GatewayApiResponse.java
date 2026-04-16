package com.yef.fota.api;

import lombok.Data;

/**
 * @description: 网关统一响应结构
 * @author: 叶丰
 * @date: 2026/04/15 22:29
 */
@Data
public class GatewayApiResponse<T> {

    private Integer code;
    private String message;
    private T data;

    public boolean success() {
        return code != null && code == 0;
    }

}