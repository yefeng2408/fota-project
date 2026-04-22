package com.yef.fota.service;

import com.yef.fota.dto.mock.DeviceGroupMockControlResponse;

public interface MockDeviceControlService {

    DeviceGroupMockControlResponse simulateOnline(Long groupId);

    DeviceGroupMockControlResponse simulateOffline(Long groupId);
}
