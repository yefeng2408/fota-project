package com.yef.controller;

import com.yef.cilent.MockDeviceClient;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * web服务调用mock-device服务的接口。模拟设备上下线
 */
@RestController
@RequestMapping("/internal/mock-devices")
public class MockDeviceControlController {

    private final MockDeviceClient mockDeviceClient;

    public MockDeviceControlController(MockDeviceClient mockDeviceClient) {
        this.mockDeviceClient = mockDeviceClient;
    }

    @PostMapping("/online")
    public MockDeviceClient.MockDeviceControlResponse online(@RequestBody MockDeviceControlRequest request) {
        return mockDeviceClient.onlineDevices(request == null ? List.of() : request.getImeiList());
    }

    @PostMapping("/offline")
    public MockDeviceClient.MockDeviceControlResponse offline(@RequestBody MockDeviceControlRequest request) {
        return mockDeviceClient.offlineDevices(request == null ? List.of() : request.getImeiList());
    }

    @Getter
    @Setter
    public static class MockDeviceControlRequest {
        private List<String> imeiList;

        public List<String> getImeiList() {
            return CollectionUtils.isEmpty(imeiList) ? List.of() : imeiList;
        }
    }
}
