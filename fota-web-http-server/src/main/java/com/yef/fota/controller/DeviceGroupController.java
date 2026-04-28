package com.yef.fota.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yef.fota.annotation.OperationLog;
import com.yef.fota.auth.AuthContext;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.dto.devicegroup.DeviceGroupSaveRequest;
import com.yef.fota.dto.devicegroup.DeviceGroupTreeVO;
import com.yef.fota.entity.DeviceGroupEntity;
import com.yef.fota.entity.DeviceGroupRelationEntity;
import com.yef.fota.entity.UserDeviceGroupEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.service.DeviceGroupRelationService;
import com.yef.fota.service.DeviceGroupService;
import com.yef.fota.service.DeviceService;
import com.yef.fota.service.UserDeviceGroupService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/device-groups")
public class DeviceGroupController {

    /**
     * 升级运行态 设备基础信息 设备网关所用的key，用于分包过程中的【高频写操作】   前缀拼接IMEI
     */
    private static final String UPGRADE_RUNTIME_KEY_PREFIX = "fota:upgrade:runtime:";
    /**
     * 设备基础信息 web服务所使用的key【低频更新】   前缀拼接IMEI
     */
    private static final String DEVICE_CACHE_KEY_PREFIX = "fota:device:";
    /**
     * 在线状态
     */
    private static final String DEVICE_ONLINE_ZSET_KEY = "fota:device:online:zset";
    private final DeviceGroupService deviceGroupService;
    private final DeviceGroupRelationService deviceGroupRelationService;
    private final DeviceService deviceService;
    private final UserDeviceGroupService userDeviceGroupService;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/tree")
    public ApiResponse<List<DeviceGroupTreeVO>> tree() {
        List<DeviceGroupEntity> groups = deviceGroupService.lambdaQuery()
                .orderByAsc(DeviceGroupEntity::getParentId, DeviceGroupEntity::getId)
                .list();
        Map<Long, Long> countMap = deviceGroupRelationService.list().stream()
                .collect(Collectors.groupingBy(DeviceGroupRelationEntity::getDeviceGroupId, Collectors.counting()));
        Map<Long, DeviceGroupTreeVO> voMap = new HashMap<>();
        for (DeviceGroupEntity group : groups) {
            DeviceGroupTreeVO vo = new DeviceGroupTreeVO();
            vo.setId(group.getId());
            vo.setParentId(group.getParentId());
            vo.setLabel(group.getDeviceGroupName());
            vo.setCreatedAt(group.getCreatedAt());
            vo.setDeviceCount(countMap.getOrDefault(group.getId(), 0L).intValue());
            voMap.put(group.getId(), vo);
        }
        List<DeviceGroupTreeVO> roots = new ArrayList<>();
        for (DeviceGroupTreeVO vo : voMap.values()) {
            if (vo.getParentId() == null || vo.getParentId() == 0) {
                roots.add(vo);
            } else {
                DeviceGroupTreeVO parent = voMap.get(vo.getParentId());
                if (parent != null) {
                    parent.getChildren().add(vo);
                } else {
                    roots.add(vo);
                }
            }
        }
        roots.sort(Comparator.comparing(DeviceGroupTreeVO::getId));
        return ApiResponse.ok(roots);
    }

    @PostMapping
    @OperationLog(action = "CREATE_DEVICE_GROUP")
    public ApiResponse<DeviceGroupEntity> create(@RequestBody @Valid DeviceGroupSaveRequest request) {
        DeviceGroupEntity entity = new DeviceGroupEntity();
        entity.setDeviceGroupName(request.getDeviceGroupName());
        entity.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        entity.setCreatedBy(AuthContext.getUserId());
        entity.setCreatedAt(LocalDateTime.now());
        deviceGroupService.save(entity);
        return ApiResponse.ok(entity);
    }

    @PutMapping("/{id}")
    @OperationLog(action = "UPDATE_DEVICE_GROUP")
    public ApiResponse<DeviceGroupEntity> update(@PathVariable Long id, @RequestBody @Valid DeviceGroupSaveRequest request) {
        DeviceGroupEntity entity = deviceGroupService.getById(id);
        if (entity == null) {
            throw new BusinessException("设备分组不存在");
        }
        entity.setDeviceGroupName(request.getDeviceGroupName());
        entity.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        deviceGroupService.updateById(entity);
        return ApiResponse.ok(entity);
    }

    @DeleteMapping("/{id}")
    @OperationLog(action = "DELETE_DEVICE_GROUP")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (deviceGroupService.getById(id) == null) {
            throw new BusinessException("设备分组不存在");
        }

        Set<Long> allGroupIds = collectSubGroupIds(id);
        List<DeviceGroupRelationEntity> relations = deviceGroupRelationService.lambdaQuery()
                .in(DeviceGroupRelationEntity::getDeviceGroupId, allGroupIds)
                .list();
        List<Long> deviceIds = relations.stream().map(DeviceGroupRelationEntity::getDeviceId).collect(Collectors.toList());

        deviceGroupRelationService.remove(new LambdaQueryWrapper<DeviceGroupRelationEntity>().in(DeviceGroupRelationEntity::getDeviceGroupId, allGroupIds));
        userDeviceGroupService.remove(new LambdaQueryWrapper<UserDeviceGroupEntity>().in(UserDeviceGroupEntity::getDeviceGroupId, allGroupIds));
        deviceGroupService.removeByIds(allGroupIds);

        for(Long deleteId : deviceIds) {
            redisTemplate.opsForZSet().remove(DEVICE_ONLINE_ZSET_KEY, String.valueOf(deleteId));
        }

        if(deviceIds!=null&&deviceIds.size()>0){
            List<String> imeiByDeviceIds = deviceService.getImeiByDeviceIds(deviceIds);
            if (!deviceIds.isEmpty()) {
                deviceService.removeByIds(deviceIds);
            }
            for (String imei : imeiByDeviceIds) {
                //删除缓存数据
                redisTemplate.delete(deviceCacheKey(imei));
                redisTemplate.delete(UPGRADE_RUNTIME_KEY_PREFIX+imei);
            }
        }

        return ApiResponse.ok(null);
    }


    private String deviceCacheKey(String imei) {
        return DEVICE_CACHE_KEY_PREFIX + imei;
    }


    private Set<Long> collectSubGroupIds(Long rootId) {
        List<DeviceGroupEntity> groups = deviceGroupService.list();
        Set<Long> ids = new java.util.HashSet<>();
        ids.add(rootId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (DeviceGroupEntity group : groups) {
                if (group.getParentId() != null && ids.contains(group.getParentId()) && ids.add(group.getId())) {
                    changed = true;
                }
            }
        }
        return ids;
    }
}
