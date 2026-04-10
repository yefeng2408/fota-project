package com.yef.fota.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.annotation.OperationLog;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.dto.device.DeviceSaveRequest;
import com.yef.fota.dto.device.DeviceVO;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.DeviceGroupEntity;
import com.yef.fota.entity.DeviceGroupRelationEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.service.DeviceGroupRelationService;
import com.yef.fota.service.DeviceGroupService;
import com.yef.fota.service.DeviceService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceGroupService deviceGroupService;
    private final DeviceGroupRelationService deviceGroupRelationService;

    @GetMapping
    public ApiResponse<PageResult<DeviceVO>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "10") long pageSize,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Long deviceGroupId) {
        Set<Long> groupDeviceIds = null;
        if (deviceGroupId != null) {
            groupDeviceIds = deviceGroupRelationService.lambdaQuery()
                    .eq(DeviceGroupRelationEntity::getDeviceGroupId, deviceGroupId)
                    .list()
                    .stream()
                    .map(DeviceGroupRelationEntity::getDeviceId)
                    .collect(Collectors.toSet());
            if (groupDeviceIds.isEmpty()) {
                return ApiResponse.ok(new PageResult<>(current, pageSize, 0, Collections.emptyList()));
            }
        }

        Set<Long> finalGroupDeviceIds = groupDeviceIds;
        Page<DeviceEntity> page = deviceService.lambdaQuery()
                .in(finalGroupDeviceIds != null, DeviceEntity::getId, finalGroupDeviceIds)
                .and(StringUtils.hasText(keyword), wrapper -> wrapper
                        .like(DeviceEntity::getImei, keyword)
                        .or()
                        .like(DeviceEntity::getDeviceName, keyword))
                .orderByDesc(DeviceEntity::getId)
                .page(new Page<>(current, pageSize));

        return ApiResponse.ok(new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), toDeviceVOs(page.getRecords())));
    }

    @PostMapping
    @OperationLog(action = "CREATE_DEVICE")
    public ApiResponse<DeviceVO> create(@RequestBody @Valid DeviceSaveRequest request) {
        DeviceEntity entity = new DeviceEntity();
        entity.setImei(request.getImei());
        entity.setDeviceName(request.getDeviceName());
        entity.setFirmwareVersion(request.getFirmwareVersion());
        entity.setCreatedAt(LocalDateTime.now());
        deviceService.save(entity);
        saveRelation(entity.getId(), request.getDeviceGroupId());
        return ApiResponse.ok(toDeviceVO(entity));
    }

    @PutMapping("/{id}")
    @OperationLog(action = "UPDATE_DEVICE")
    public ApiResponse<DeviceVO> update(@PathVariable Long id, @RequestBody @Valid DeviceSaveRequest request) {
        DeviceEntity entity = deviceService.getById(id);
        if (entity == null) {
            throw new BusinessException("设备不存在");
        }
        entity.setImei(request.getImei());
        entity.setDeviceName(request.getDeviceName());
        entity.setFirmwareVersion(request.getFirmwareVersion());
        deviceService.updateById(entity);
        deviceGroupRelationService.remove(new LambdaQueryWrapper<DeviceGroupRelationEntity>().eq(DeviceGroupRelationEntity::getDeviceId, id));
        saveRelation(id, request.getDeviceGroupId());
        return ApiResponse.ok(toDeviceVO(entity));
    }

    @DeleteMapping("/{id}")
    @OperationLog(action = "DELETE_DEVICE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deviceService.removeById(id);
        deviceGroupRelationService.remove(new LambdaQueryWrapper<DeviceGroupRelationEntity>().eq(DeviceGroupRelationEntity::getDeviceId, id));
        return ApiResponse.ok(null);
    }

    private void saveRelation(Long deviceId, Long deviceGroupId) {
        DeviceGroupEntity group = deviceGroupService.getById(deviceGroupId);
        if (group == null) {
            throw new BusinessException("设备分组不存在");
        }
        DeviceGroupRelationEntity relation = new DeviceGroupRelationEntity();
        relation.setDeviceId(deviceId);
        relation.setDeviceGroupId(deviceGroupId);
        relation.setCreatedAt(LocalDateTime.now());
        deviceGroupRelationService.save(relation);
    }

    private List<DeviceVO> toDeviceVOs(List<DeviceEntity> entities) {
        Map<Long, DeviceGroupRelationEntity> relationMap = deviceGroupRelationService.list().stream()
                .collect(Collectors.toMap(DeviceGroupRelationEntity::getDeviceId, Function.identity(), (a, b) -> a));
        Map<Long, DeviceGroupEntity> groupMap = deviceGroupService.list().stream()
                .collect(Collectors.toMap(DeviceGroupEntity::getId, Function.identity(), (a, b) -> a));
        return entities.stream().map(entity -> {
            DeviceVO vo = new DeviceVO();
            vo.setId(entity.getId());
            vo.setImei(entity.getImei());
            vo.setDeviceName(entity.getDeviceName());
            vo.setFirmwareVersion(entity.getFirmwareVersion());
            vo.setCreatedAt(entity.getCreatedAt());
            vo.setOnlineStatus("UNKNOWN");
            DeviceGroupRelationEntity relation = relationMap.get(entity.getId());
            if (relation != null) {
                vo.setDeviceGroupId(relation.getDeviceGroupId());
                DeviceGroupEntity group = groupMap.get(relation.getDeviceGroupId());
                vo.setDeviceGroupName(group == null ? null : group.getDeviceGroupName());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private DeviceVO toDeviceVO(DeviceEntity entity) {
        return toDeviceVOs(List.of(entity)).get(0);
    }
}
