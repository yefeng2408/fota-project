package com.yef.controller;

import com.yef.ApiResponse;
import com.yef.dto.PlatformCancelUpgradeRequest;
import com.yef.dto.PlatformUpgradeRequest;
import com.yef.service.GatewayUpgradeDispatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @description: 网关暴露给web-http-server的接口，用于平台下发指令
 * @author: 叶丰
 * @date: 2026/04/16 09:42
 */

@RestController
@RequestMapping("/internal/device")
@RequiredArgsConstructor
public class GatewayUpgradeController {


    private final GatewayUpgradeDispatchService gatewayUpgradeDispatchService;

    /**
     * 网关接收平台下发的0x81请求升级指令
     *
     * @param request
     * @return
     */
    @PostMapping(
            value = "/upgrade-request",
            consumes = "application/json",
            produces = "application/json"
    )
    public ApiResponse<Void> sendUpgradeRequest(@RequestBody PlatformUpgradeRequest request) {
        gatewayUpgradeDispatchService.sendUpgradeRequest(request);
        return ApiResponse.ok();
    }


    /**
     * 网关接收平台下发的0x87取消升级指令
     *
     * @param request
     * @return
     */
    @PostMapping(
            value = "/cancel-request",
            consumes = "application/json",
            produces = "application/json"
    )
    public ApiResponse<Void> sendCancelRequest(@RequestBody PlatformCancelUpgradeRequest request) {
        gatewayUpgradeDispatchService.sendCancelUpgradeRequest(request);
        return ApiResponse.ok();
    }


}