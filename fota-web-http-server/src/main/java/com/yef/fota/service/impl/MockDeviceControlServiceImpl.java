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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class MockDeviceControlServiceImpl implements MockDeviceControlService {

    private final DeviceGroupRelationService deviceGroupRelationService;
    private final DeviceService deviceService;
    private final RestTemplate restTemplate;
    private final List<String> mockDeviceBaseUrls;

    public MockDeviceControlServiceImpl(DeviceGroupRelationService deviceGroupRelationService,
                                        DeviceService deviceService,
                                        RestTemplate restTemplate,
                                        @Value("${mock.device.http.url}") String mockDeviceBaseUrl,
                                        @Value("${mock.device.http.urls:}") String mockDeviceBaseUrls) {
        this.deviceGroupRelationService = deviceGroupRelationService;
        this.deviceService = deviceService;
        this.restTemplate = restTemplate;
        this.mockDeviceBaseUrls = resolveMockDeviceBaseUrls(mockDeviceBaseUrl, mockDeviceBaseUrls);
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
        // key:机器ip  value:这一批设备imei
        Map<String, List<String>> shards = shardImeis(imeiList);

        int totalCount = 0;
        int successCount = 0;
        int skippedCount = 0;
        List<String> summaries = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : shards.entrySet()) {
            String mockDeviceBaseUrl = entry.getKey();
            List<String> shardImeis = entry.getValue();
            if (CollectionUtils.isEmpty(shardImeis)) {
                continue;
            }

            MockDeviceControlRequest request = new MockDeviceControlRequest();
            request.setImeiList(shardImeis);

            MockDeviceControlResponse remoteResponse;
            try {
                remoteResponse = restTemplate.postForObject(mockDeviceBaseUrl + path, request, MockDeviceControlResponse.class);
            } catch (RestClientException e) {
                throw new BusinessException("模拟设备服务调用失败，请确认 mock-device-client 已启动, url=" + mockDeviceBaseUrl);
            }
            if (remoteResponse == null) {
                throw new BusinessException("模拟设备服务返回为空, url=" + mockDeviceBaseUrl);
            }
            totalCount += nullToZero(remoteResponse.getTotalCount());
            successCount += nullToZero(remoteResponse.getSuccessCount());
            skippedCount += nullToZero(remoteResponse.getSkippedCount());
            summaries.add(String.format(Locale.ROOT,
                    "%s --> total=%d, success=%d, skipped=%d",
                    mockDeviceBaseUrl,
                    nullToZero(remoteResponse.getTotalCount()),
                    nullToZero(remoteResponse.getSuccessCount()),
                    nullToZero(remoteResponse.getSkippedCount())));
        }

        DeviceGroupMockControlResponse response = new DeviceGroupMockControlResponse();
        response.setGroupId(groupId);
        response.setTotalCount(totalCount);
        response.setSuccessCount(successCount);
        response.setSkippedCount(skippedCount);
        response.setSummary(String.join("; ", summaries));
        return response;
    }

    private Map<String, List<String>> shardImeis(List<String> imeiList) {
        Map<String, List<String>> shards = new LinkedHashMap<>();
        for (String baseUrl : mockDeviceBaseUrls) {
            shards.put(baseUrl, new ArrayList<>());
        }

        for (String imei : imeiList) {
            int index = Math.floorMod(imei.hashCode(), mockDeviceBaseUrls.size());
            shards.get(mockDeviceBaseUrls.get(index)).add(imei);
        }
        return shards;
    }

    private List<String> resolveMockDeviceBaseUrls(String singleUrl, String multiUrls) {
        List<String> result = new ArrayList<>();
        if (StringUtils.hasText(multiUrls)) {
            for (String item : multiUrls.split(",")) {
                String normalized = normalizeBaseUrl(item);
                if (StringUtils.hasText(normalized)) {
                    result.add(normalized);
                }
            }
        }

        if (result.isEmpty()) {
            String normalizedSingleUrl = normalizeBaseUrl(singleUrl);
            if (StringUtils.hasText(normalizedSingleUrl)) {
                result.add(normalizedSingleUrl);
            }
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("mock device service url cannot be blank");
        }
        return result;
    }

    private String normalizeBaseUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
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
