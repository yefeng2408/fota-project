package com.yef.fota.api;

import com.yef.fota.common.ApiResponse;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @description:
 * @author: 叶丰
 * @date: 2026/4/15 22:09
 */

public interface GatewayUpgradeApi {

    @PostExchange("/send-upgrade-request")
    ApiResponse<Void> sendUpgradeRequest(@RequestBody GatewayUpgradeRequest request);

    @PostExchange("/send-cancel-request")
    ApiResponse<Void> sendCancelRequest(@RequestBody GatewayCancelUpgradeRequest request);
}