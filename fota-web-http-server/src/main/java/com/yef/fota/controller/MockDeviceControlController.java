package com.yef.fota.controller;

import com.yef.fota.annotation.OperationLog;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.dto.mock.DeviceGroupMockControlRequest;
import com.yef.fota.dto.mock.DeviceGroupMockControlResponse;
import com.yef.fota.service.MockDeviceControlService;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/mock-devices")
public class MockDeviceControlController {

    private final MockDeviceControlService mockDeviceControlService;

    @PostMapping("/online")
    @OperationLog(action = "MOCK_DEVICE_ONLINE")
    public ApiResponse<DeviceGroupMockControlResponse> online(@RequestBody @Valid DeviceGroupMockControlRequest request) {
        return ApiResponse.ok(mockDeviceControlService.simulateOnline(request.getGroupId()));
    }

    @PostMapping("/offline")
    @OperationLog(action = "MOCK_DEVICE_OFFLINE")
    public ApiResponse<DeviceGroupMockControlResponse> offline(@RequestBody @Valid DeviceGroupMockControlRequest request) {
        return ApiResponse.ok(mockDeviceControlService.simulateOffline(request.getGroupId()));
    }
}
