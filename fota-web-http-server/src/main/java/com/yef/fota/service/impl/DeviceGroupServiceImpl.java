package com.yef.fota.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yef.fota.entity.DeviceGroupEntity;
import com.yef.fota.entity.DeviceGroupRelationEntity;
import com.yef.fota.entity.UserDeviceGroupEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.mapper.DeviceMapper;
import com.yef.fota.mapper.DeviceGroupMapper;
import com.yef.fota.mapper.UpgradeTaskMapper;
import com.yef.fota.service.DeviceGroupRelationService;
import com.yef.fota.service.DeviceGroupService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yef.fota.service.UserDeviceGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * <p>
 * 设备分组表（树结构） 服务实现类
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Service
@RequiredArgsConstructor
public class DeviceGroupServiceImpl extends ServiceImpl<DeviceGroupMapper, DeviceGroupEntity> implements DeviceGroupService {

    /**
     * 升级运行态 设备基础信息 设备网关所用的key，用于分包过程中的【高频写操作】 +imei
     */
    private static final String UPGRADE_RUNTIME_KEY_PREFIX = "fota:upgrade:runtime:";
    /**
     * 设备基础信息 web服务所使用的key【低频更新】 +imei
     */
    private static final String DEVICE_CACHE_KEY_PREFIX = "fota:device:";
    /**
     * 在线状态
     */
    private static final String DEVICE_ONLINE_ZSET_KEY = "fota:device:online:zset";

    private static final String SEMAPHORE_KEY = "fota:upgrade:holders";

    private static final int BATCH_SIZE = 900;

    private final DeviceGroupRelationService deviceGroupRelationService;
    private final UserDeviceGroupService userDeviceGroupService;
    private final DeviceMapper deviceMapper;
    private final UpgradeTaskMapper upgradeTaskMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroupCascade(Long id) {
        if (this.getById(id) == null) {
            throw new BusinessException("设备分组不存在");
        }

        Set<Long> allGroupIds = collectSubGroupIds(id);
        List<Long> deviceIds = listDeviceIdsByGroupIds(allGroupIds);
        List<String> imeis = listImeisByDeviceIds(deviceIds);

        removeGroupRelations(allGroupIds);
        removeUserGroupRelations(allGroupIds);
        forEachBatch(new ArrayList<>(allGroupIds), this::removeByIds);

        removeUpgradeTasks(deviceIds);
        removeDevices(deviceIds);
        removeDeviceRedisCache(deviceIds, imeis);
    }

    private Set<Long> collectSubGroupIds(Long rootId) {
        List<DeviceGroupEntity> groups = this.lambdaQuery()
                .select(DeviceGroupEntity::getId, DeviceGroupEntity::getParentId)
                .list();
        Map<Long, List<Long>> childrenByParentId = groups.stream()
                .filter(group -> group.getParentId() != null)
                .collect(Collectors.groupingBy(
                        DeviceGroupEntity::getParentId,
                        Collectors.mapping(DeviceGroupEntity::getId, Collectors.toList())));

        Set<Long> groupIds = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        groupIds.add(rootId);
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long parentId = queue.poll();
            for (Long childId : childrenByParentId.getOrDefault(parentId, List.of())) {
                if (groupIds.add(childId)) {
                    queue.add(childId);
                }
            }
        }
        return groupIds;
    }

    private List<Long> listDeviceIdsByGroupIds(Set<Long> groupIds) {
        Set<Long> deviceIds = new HashSet<>();
        forEachBatch(new ArrayList<>(groupIds), batch -> deviceGroupRelationService.lambdaQuery()
                .select(DeviceGroupRelationEntity::getDeviceId)
                .in(DeviceGroupRelationEntity::getDeviceGroupId, batch)
                .list()
                .stream()
                .map(DeviceGroupRelationEntity::getDeviceId)
                .filter(Objects::nonNull)
                .forEach(deviceIds::add));
        return new ArrayList<>(deviceIds);
    }

    private List<String> listImeisByDeviceIds(List<Long> deviceIds) {
        List<String> imeis = new ArrayList<>();
        forEachBatch(deviceIds, batch -> imeis.addAll(deviceMapper.getImeiList(batch)));
        return imeis;
    }

    private void removeGroupRelations(Set<Long> groupIds) {
        forEachBatch(new ArrayList<>(groupIds), batch -> deviceGroupRelationService.remove(
                new LambdaQueryWrapper<DeviceGroupRelationEntity>()
                        .in(DeviceGroupRelationEntity::getDeviceGroupId, batch)));
    }

    private void removeUserGroupRelations(Set<Long> groupIds) {
        forEachBatch(new ArrayList<>(groupIds), batch -> userDeviceGroupService.remove(
                new LambdaQueryWrapper<UserDeviceGroupEntity>()
                        .in(UserDeviceGroupEntity::getDeviceGroupId, batch)));
    }

    private void removeUpgradeTasks(List<Long> deviceIds) {
        forEachBatch(deviceIds, upgradeTaskMapper::deleteBatchUpgradeTask);
    }

    private void removeDevices(List<Long> deviceIds) {
        forEachBatch(deviceIds, deviceMapper::deleteBatchIds);
    }

    private void removeDeviceRedisCache(List<Long> deviceIds, List<String> imeis) {
        forEachBatch(deviceIds, batch -> redisTemplate.opsForZSet().remove(
                DEVICE_ONLINE_ZSET_KEY,
                batch.stream().map(String::valueOf).toArray()));

        forEachBatch(imeis, batch -> {
            List<String> keys = new ArrayList<>(batch.size() * 3);
            for (String imei : batch) {
                String runtimeKey = UPGRADE_RUNTIME_KEY_PREFIX + imei;
                keys.add(deviceCacheKey(imei));
                keys.add(runtimeKey);
                keys.add(runtimeKey + ":lastPushProgress");
            }
            redisTemplate.delete(keys);
            redisTemplate.opsForSet().remove(SEMAPHORE_KEY, batch.toArray());
        });
    }

    private String deviceCacheKey(String imei) {
        return DEVICE_CACHE_KEY_PREFIX + imei;
    }

    private <T> void forEachBatch(List<T> values, Consumer<List<T>> consumer) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (int i = 0; i < values.size(); i += BATCH_SIZE) {
            consumer.accept(values.subList(i, Math.min(i + BATCH_SIZE, values.size())));
        }
    }
}
