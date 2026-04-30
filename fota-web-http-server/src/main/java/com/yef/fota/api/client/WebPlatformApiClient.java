package com.yef.fota.api.client;

import com.dtflys.forest.annotation.BaseRequest;
import com.dtflys.forest.annotation.Body;
import com.dtflys.forest.annotation.JSONBody;
import com.dtflys.forest.annotation.Post;
import com.yef.fota.api.GatewayApiResponse;
import com.yef.fota.api.dto.PlatformCancelUpgradeRequest;
import com.yef.fota.api.dto.PlatformUpgradeRequest;

/**
 * @description: 平台下发指令给网关，再有网关出站发消息给设备
 * @author: 叶丰
 * @date: 2026/04/15 22:31
 */
@BaseRequest(baseURL = "${baseUrl}")
public interface WebPlatformApiClient {

    /**
     * 平台下发 0x81 UpgradeRequest
     */
    @Post(
            url = "/internal/device/upgrade-request",
            contentType = "application/json",
            headers = {"Accept: application/json"}
    )
    GatewayApiResponse<Void> sendUpgradeRequest(@JSONBody PlatformUpgradeRequest request);

    /**
     * 平台下发 0x87 CancelUpgrade
     */
    @Post(
            url = "/internal/device/cancel-request",
            contentType = "application/json",
            headers = {"Accept: application/json"}
    )
    GatewayApiResponse<Void> sendCancelUpgradeRequest(@JSONBody PlatformCancelUpgradeRequest request);
}