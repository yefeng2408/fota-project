package com.yef.fota.api.service;

import com.yef.fota.api.GatewayApiResponse;
import com.yef.fota.api.client.WebPlatformApiClient;
import com.yef.fota.api.dto.PlatformCancelUpgradeRequest;
import com.yef.fota.api.dto.PlatformUpgradeRequest;
import com.yef.fota.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @description: 指令下发服务。由平台下发指令到设备网关
 * @author: 叶丰
 * @date: 2026/04/15 22:34
 */
@Service
public class PlatformCommandService {

    private final WebPlatformApiClient platformApiClient;

    public PlatformCommandService(@Autowired WebPlatformApiClient gatewayApiClient) {
        this.platformApiClient = gatewayApiClient;
    }

    public GatewayApiResponse<Void> sendUpgradeRequest(PlatformUpgradeRequest request) {
        GatewayApiResponse<Void> response = platformApiClient.sendUpgradeRequest(request);
        if (response == null || !response.success()) {
            throw new BusinessException("调用网关下发升级请求失败: " +
                    (response == null ? "response is null" : response.getMessage()));
        }
        return response;
    }

    public GatewayApiResponse<Void> sendCancelUpgradeRequest(PlatformCancelUpgradeRequest request) {
        GatewayApiResponse<Void> response = platformApiClient.sendCancelUpgradeRequest(request);
        if (response == null || !response.success()) {
            throw new BusinessException("调用网关下发取消升级失败: " +
                    (response == null ? "response is null" : response.getMessage()));
        }
        return response;
    }
}