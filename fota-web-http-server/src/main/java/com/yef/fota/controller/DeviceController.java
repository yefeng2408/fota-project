package com.yef.fota.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.annotation.OperationLog;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.dto.device.DeviceSaveRequest;
import com.yef.fota.dto.device.DeviceVO;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.DeviceGroupEntity;
import com.yef.fota.entity.DeviceGroupRelationEntity;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.service.DeviceGroupRelationService;
import com.yef.fota.service.DeviceGroupService;
import com.yef.fota.service.DeviceService;
import com.yef.fota.service.FirmwarePackageService;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    /**
     * 设备在线状态
     */
    private static final String DEVICE_ONLINE_KEY_PREFIX = "fota:device:online:";
    /**
     * 升级运行态 设备基础信息 设备网关所用的key，用于分包过程中的【高频写操作】
     */
    private static final String UPGRADE_RUNTIME_KEY_PREFIX = "fota:upgrade:runtime:";
    /**
     * 设备基础信息 web服务所使用的key【低频更新】
     */
    private static final String DEVICE_CACHE_KEY_PREFIX = "fota:device:";


    private final DeviceService deviceService;
    private final DeviceGroupService deviceGroupService;
    private final DeviceGroupRelationService deviceGroupRelationService;
    private final FirmwarePackageService firmwarePackageService;
    private final StringRedisTemplate redisTemplate;

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

    /**
     * 新增设备
     * @param request
     * @return
     */
    @PostMapping
    @OperationLog(action = "CREATE_DEVICE")
    public ApiResponse<DeviceVO> create(@RequestBody @Valid DeviceSaveRequest request) {
        validateImeiUnique(request.getImei(), null);
        DeviceEntity entity = new DeviceEntity();
        entity.setImei(request.getImei());
        entity.setDeviceName(request.getDeviceName());
        entity.setDeviceType(request.getDeviceType());
        entity.setCurrentFirmwareVersion(request.getCurrentFirmwareVersion());
        entity.setTargetFirmwareId(request.getTargetFirmwareId());
        entity.setIsBind(resolveBindStatus(request.getTargetFirmwareId()));
        entity.setDeviceUpgradeStatus("NO_TASK");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        boolean save = deviceService.save(entity);
        saveRelation(entity.getId(), request.getDeviceGroupId());
        if (save){
            upsertDeviceCache(entity);
        }
        return ApiResponse.ok(toDeviceVO(entity));
    }

    @PutMapping("/{id}")
    @OperationLog(action = "UPDATE_DEVICE")
    public ApiResponse<DeviceVO> update(@PathVariable Long id, @RequestBody @Valid DeviceSaveRequest request) {
        DeviceEntity entity = deviceService.getById(id);
        if (entity == null) {
            throw new BusinessException("设备不存在");
        }

        validateDeviceEditable(entity);
        validateImeiUnique(request.getImei(), id);

        entity.setImei(request.getImei());
        entity.setDeviceName(request.getDeviceName());
        entity.setDeviceType(request.getDeviceType());
        entity.setCurrentFirmwareVersion(request.getCurrentFirmwareVersion());
        entity.setDeviceUpgradeStatus(StringUtils.hasText(request.getDeviceUpgradeStatus()) ? request.getDeviceUpgradeStatus() : entity.getDeviceUpgradeStatus());
        entity.setTargetFirmwareId(request.getTargetFirmwareId());
        entity.setIsBind(resolveBindStatus(request.getTargetFirmwareId()));
        entity.setUpdatedAt(LocalDateTime.now());
        deviceService.update(new LambdaUpdateWrapper<DeviceEntity>()
                .eq(DeviceEntity::getId, id)
                .set(DeviceEntity::getImei, entity.getImei())
                .set(DeviceEntity::getDeviceName, entity.getDeviceName())
                .set(DeviceEntity::getDeviceType, entity.getDeviceType())
                .set(DeviceEntity::getCurrentFirmwareVersion, entity.getCurrentFirmwareVersion())
                .set(DeviceEntity::getDeviceUpgradeStatus, entity.getDeviceUpgradeStatus())
                .set(DeviceEntity::getTargetFirmwareId, entity.getTargetFirmwareId())
                .set(DeviceEntity::getIsBind, entity.getIsBind())
                .set(DeviceEntity::getUpdatedAt, entity.getUpdatedAt()));
        deviceGroupRelationService.remove(new LambdaQueryWrapper<DeviceGroupRelationEntity>().eq(DeviceGroupRelationEntity::getDeviceId, id));
        saveRelation(id, request.getDeviceGroupId());
        upsertDeviceCache(entity);
        return ApiResponse.ok(toDeviceVO(entity));
    }


    /**
     * 删除设备
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    @OperationLog(action = "DELETE_DEVICE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        DeviceEntity entity = deviceService.getById(id);
        if(entity==null){
            return ApiResponse.fail("该设备不存在.");
        }
        validateDeviceEditable(entity);
        boolean deleted = deviceService.removeById(id);
        deviceGroupRelationService.remove(new LambdaQueryWrapper<DeviceGroupRelationEntity>()
                                  .eq(DeviceGroupRelationEntity::getDeviceId, id));
        if(deleted){
            redisTemplate.delete(deviceCacheKey(id));
        }
        return ApiResponse.ok(null);
    }

    private void upsertDeviceCache(DeviceEntity entity) {
        String deviceKey = deviceCacheKey(entity.getId());

        Map<String, String> deviceCache = new HashMap<>();
        deviceCache.put("id", nullToEmpty(entity.getId()));
        deviceCache.put("imei", nullToEmpty(entity.getImei()));
        deviceCache.put("deviceName", nullToEmpty(entity.getDeviceName()));
        deviceCache.put("deviceType", nullToEmpty(entity.getDeviceType()));
        deviceCache.put("currentFirmwareVersion", nullToEmpty(entity.getCurrentFirmwareVersion()));
        deviceCache.put("deviceUpgradeStatus", nullToEmpty(entity.getDeviceUpgradeStatus()));
        deviceCache.put("isBind", nullToEmpty(entity.getIsBind()));

        //对象以hash的形式存储
        redisTemplate.opsForHash().putAll(deviceKey, deviceCache);
    }

    private String deviceCacheKey(Long deviceId) {
        return DEVICE_CACHE_KEY_PREFIX + deviceId;
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    private Integer resolveBindStatus(Long targetFirmwareId) {
        return targetFirmwareId == null ? 0 : 1;
    }

    private void validateImeiUnique(String imei, Long excludeDeviceId) {
        long count = deviceService.lambdaQuery()
                .eq(DeviceEntity::getImei, imei)
                .ne(excludeDeviceId != null, DeviceEntity::getId, excludeDeviceId)
                .count();
        if (count > 0) {
            throw new BusinessException("IMEI已存在，请检查后重新输入");
        }
    }

    private List<DeviceVO> toDeviceVOs(List<DeviceEntity> entities) {
        Map<Long, DeviceGroupRelationEntity> relationMap = deviceGroupRelationService.list().stream()
                .collect(Collectors.toMap(DeviceGroupRelationEntity::getDeviceId, Function.identity(), (a, b) -> a));
        Map<Long, DeviceGroupEntity> groupMap = deviceGroupService.list().stream()
                .collect(Collectors.toMap(DeviceGroupEntity::getId, Function.identity(), (a, b) -> a));
        Set<Long> targetFirmwareIds = entities.stream()
                .map(DeviceEntity::getTargetFirmwareId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, FirmwarePackageEntity> firmwareMap = targetFirmwareIds.isEmpty()
                ? Collections.emptyMap()
                : firmwarePackageService.listByIds(targetFirmwareIds).stream()
                        .collect(Collectors.toMap(FirmwarePackageEntity::getId, Function.identity(), (a, b) -> a));
        return entities.stream().map(entity -> {
            DeviceVO vo = new DeviceVO();
            vo.setId(entity.getId());
            vo.setImei(entity.getImei());
            vo.setDeviceName(entity.getDeviceName());
            vo.setDeviceType(entity.getDeviceType());
            vo.setCurrentFirmwareVersion(entity.getCurrentFirmwareVersion());
            vo.setDeviceUpgradeStatus(entity.getDeviceUpgradeStatus());
            vo.setTargetFirmwareId(entity.getTargetFirmwareId());
            vo.setLastUpgradeTaskId(entity.getLastUpgradeTaskId());
            FirmwarePackageEntity targetFirmware = firmwareMap.get(entity.getTargetFirmwareId());
            if (targetFirmware != null) {
                vo.setTargetFirmwareVersion(targetFirmware.getVersion());
                vo.setTargetFirmwareName(targetFirmware.getFileName());
            }
            vo.setCreatedAt(entity.getCreatedAt());
            vo.setUpdatedAt(entity.getUpdatedAt());
            vo.setIsBind(entity.getIsBind());
            //在线状态查询redis
            String onlineValue = redisTemplate.opsForValue().get(DEVICE_ONLINE_KEY_PREFIX + entity.getImei());
            vo.setIsOnline(Integer.parseInt(onlineValue == null ? "0" : onlineValue));
            //计算是否可升级
            if(vo.getIsBind() ==1 && vo.getIsOnline()==1 && "NO_TASK".equals(vo.getDeviceUpgradeStatus())) {
                vo.setIsUpgradable("Y");
            }
            DeviceGroupRelationEntity relation = relationMap.get(entity.getId());
            if (relation != null) {
                vo.setDeviceGroupId(relation.getDeviceGroupId());
                DeviceGroupEntity group = groupMap.get(relation.getDeviceGroupId());
                vo.setDeviceGroupName(group == null ? null : group.getDeviceGroupName());
            }
            String runtimeKey = UPGRADE_RUNTIME_KEY_PREFIX+entity.getImei();
            Map<Object, Object> runtimeMap = redisTemplate.opsForHash().entries(runtimeKey);
            if(runtimeMap.containsKey("status")) {
                vo.setDeviceUpgradeStatus(String.valueOf(runtimeMap.get("status")));
            }
           if(runtimeMap.containsKey("progress")) {
               vo.setProgress(Integer.parseInt(String.valueOf(runtimeMap.get("progress"))));
           }
            return vo;
        }).collect(Collectors.toList());
    }

    private DeviceVO toDeviceVO(DeviceEntity entity) {
        return toDeviceVOs(List.of(entity)).get(0);
    }

    /**
     * 安全校验。处于升级环节中的设备，不可以操作
     * @param entity
     */
    private void validateDeviceEditable(DeviceEntity entity) {
        String imei = entity.getImei();

        Object status = redisTemplate.opsForHash()
                .get("fota:upgrade:runtime:" + imei, "status");

        if (status != null) {
            String runtimeStatus = String.valueOf(status);
            if (isForbiddenEditStatus(runtimeStatus)) {
                throw new BusinessException("设备升级中，不可编辑！");
            }
        }

        if (isForbiddenEditStatus(entity.getDeviceUpgradeStatus())) {
            throw new BusinessException("设备升级中，不可编辑！");
        }
    }

    //三种状态下，列表列表不能编辑设备信息
    private boolean isForbiddenEditStatus(String status) {
        return "UPGRADE_REQUESTED".equals(status)
                || "UPGRADING".equals(status)
                || "PAUSED".equals(status);
    }
}
