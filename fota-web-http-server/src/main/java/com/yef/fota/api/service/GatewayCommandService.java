package com.yef.fota.api.service;

import com.yef.fota.api.GatewayApiResponse;
import com.yef.fota.api.client.GatewayApiClient;
import com.yef.fota.api.dto.GatewayCancelUpgradeRequest;
import com.yef.fota.api.dto.GatewayUpgradeRequest;
import com.yef.fota.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @description: 指令下发服务
 * @author: 叶丰
 * @date: 2026/04/15 22:34
 */
@Service
public class GatewayCommandService {

    private final GatewayApiClient gatewayApiClient;

    public GatewayCommandService(@Autowired GatewayApiClient gatewayApiClient) {
        this.gatewayApiClient = gatewayApiClient;
    }

    public void sendUpgradeRequest(GatewayUpgradeRequest request) {
        GatewayApiResponse<Void> response = gatewayApiClient.sendUpgradeRequest(request);
        if (response == null || !response.success()) {
            throw new BusinessException("调用网关下发升级请求失败: " +
                    (response == null ? "response is null" : response.getMessage()));
        }
    }

    public void sendCancelUpgradeRequest(GatewayCancelUpgradeRequest request) {
        GatewayApiResponse<Void> response = gatewayApiClient.sendCancelUpgradeRequest(request);
        if (response == null || !response.success()) {
            throw new BusinessException("调用网关下发取消升级失败: " +
                    (response == null ? "response is null" : response.getMessage()));
        }
    }
}