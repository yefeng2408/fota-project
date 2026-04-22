package com.yef.fota.service.impl;

import com.yef.fota.dto.mock.DeviceGroupMockControlResponse;
import com.yef.fota.dto.mock.MockDeviceControlRequest;
import com.yef.fota.dto.mock.MockDeviceControlResponse;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.DeviceGroupRelationEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.service.DeviceGroupRelationService;
import com.yef.fota.service.DeviceService;
import com.yef.fota.service.MockDeviceControlService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class MockDeviceControlServiceImpl implements MockDeviceControlService {

    private final DeviceGroupRelationService deviceGroupRelationService;
    private final DeviceService deviceService;
    private final RestTemplate restTemplate;
    private final String mockDeviceBaseUrl;

    public MockDeviceControlServiceImpl(DeviceGroupRelationService deviceGroupRelationService,
                                        DeviceService deviceService,
                                        RestTemplate restTemplate,
                                        @Value("${mock.device.http.url}") String mockDeviceBaseUrl) {
        this.deviceGroupRelationService = deviceGroupRelationService;
        this.deviceService = deviceService;
        this.restTemplate = restTemplate;
        this.mockDeviceBaseUrl = mockDeviceBaseUrl;
    }

    @Override
    public DeviceGroupMockControlResponse simulateOnline(Long groupId) {
        return controlGroup(groupId, "/internal/mock-devices/online");
    }

    @Override
    public DeviceGroupMockControlResponse simulateOffline(Long groupId) {
        return controlGroup(groupId, "/internal/mock-devices/offline");
    }

    private DeviceGroupMockControlResponse controlGroup(Long groupId, String path) {
        List<String> imeiList = resolveImeisByGroupId(groupId);

        MockDeviceControlRequest request = new MockDeviceControlRequest();
        request.setImeiList(imeiList);

        MockDeviceControlResponse remoteResponse;
        try {
            remoteResponse = restTemplate.postForObject(mockDeviceBaseUrl + path, request, MockDeviceControlResponse.class);
        } catch (RestClientException e) {
            throw new BusinessException("模拟设备服务调用失败，请确认 mock-device-client 已启动");
        }
        if (remoteResponse == null) {
            throw new BusinessException("模拟设备服务返回为空");
        }

        DeviceGroupMockControlResponse response = new DeviceGroupMockControlResponse();
        response.setGroupId(groupId);
        response.setTotalCount(remoteResponse.getTotalCount());
        response.setSuccessCount(remoteResponse.getSuccessCount());
        response.setSkippedCount(remoteResponse.getSkippedCount());
        response.setSummary(remoteResponse.getSummary());
        return response;
    }

    private List<String> resolveImeisByGroupId(Long groupId) {
        List<Long> deviceIds = deviceGroupRelationService.lambdaQuery()
                .eq(DeviceGroupRelationEntity::getDeviceGroupId, groupId)
                .list()
                .stream()
                .map(DeviceGroupRelationEntity::getDeviceId)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(deviceIds)) {
            throw new BusinessException("当前设备组下没有设备");
        }

        List<String> imeiList = deviceService.lambdaQuery()
                .in(DeviceEntity::getId, deviceIds)
                .list()
                .stream()
                .map(DeviceEntity::getImei)
                .filter(imei -> imei != null && imei.matches("\\d{8}"))
                .collect(Collectors.toList());
        List<String> distinctImeis = new LinkedHashSet<>(imeiList).stream().collect(Collectors.toList());
        if (distinctImeis.isEmpty()) {
            throw new BusinessException("当前设备组下没有可用设备");
        }
        return distinctImeis;
    }
}
