package com.yef.fota.api.service;

import com.yef.fota.api.GatewayApiResponse;
import com.yef.fota.api.GatewayRoute;
import com.yef.fota.api.dto.PlatformCancelUpgradeRequest;
import com.yef.fota.api.dto.PlatformUpgradeRequest;
import com.yef.fota.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

/**
 * @description: 指令下发服务。由平台下发指令到设备网关
 * @author: 叶丰
 * @date: 2026/04/15 22:34
 */
@Service
public class PlatformCommandService {

    private static final String UPGRADE_REQUEST_PATH = "/internal/device/upgrade-request";
    private static final String CANCEL_REQUEST_PATH = "/internal/device/cancel-request";

    private final GatewayRouteService gatewayRouteService;
    private final RestTemplate restTemplate;

    public PlatformCommandService(GatewayRouteService gatewayRouteService,
                                  RestTemplate restTemplate) {
        this.gatewayRouteService = gatewayRouteService;
        this.restTemplate = restTemplate;
    }

    public GatewayApiResponse<Void> sendUpgradeRequest(PlatformUpgradeRequest request) {
        GatewayApiResponse<Void> response = postToDeviceGateway(request.getImei(), UPGRADE_REQUEST_PATH, request);
        if (response == null || !response.success()) {
            throw new BusinessException("调用网关下发升级请求失败: " +
                    (response == null ? "response is null" : response.getMessage()));
        }
        return response;
    }

    public GatewayApiResponse<Void> sendCancelUpgradeRequest(PlatformCancelUpgradeRequest request) {
        GatewayApiResponse<Void> response = postToDeviceGateway(request.getImei(), CANCEL_REQUEST_PATH, request);
        if (response == null || !response.success()) {
            throw new BusinessException("调用网关下发取消升级失败: " +
                    (response == null ? "response is null" : response.getMessage()));
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private GatewayApiResponse<Void> postToDeviceGateway(String imei, String path, Object body) {
        GatewayRoute route = gatewayRouteService.findRouteByImei(imei);
        if (route == null || !StringUtils.hasText(route.getHttpUrl())) {
            throw new BusinessException("设备不在线或未找到设备所在网关: " + imei);
        }

        String url = route.getHttpUrl() + path;
        try {
            return restTemplate.postForObject(url, body, GatewayApiResponse.class);
        } catch (RestClientException e) {
            throw new BusinessException("调用设备所在网关失败, imei=" + imei
                    + ", gateway=" + route.getHttpUrl()
                    + ", error=" + e.getMessage());
        }
    }
}
