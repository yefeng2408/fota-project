package com.yef.fota.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yef.fota.dto.device.DeviceSaveRequest;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.DeviceGroupEntity;
import com.yef.fota.entity.DeviceGroupRelationEntity;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.mapper.DeviceMapper;
import com.yef.fota.mapper.UpgradeTaskMapper;
import com.yef.fota.service.DeviceGroupRelationService;
import com.yef.fota.service.DeviceGroupService;
import com.yef.fota.service.DeviceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yef.fota.service.FirmwarePackageService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <p>
 * 设备表 服务实现类
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, DeviceEntity> implements DeviceService {


    /**
     * 升级运行态 设备基础信息 设备网关所用的key，用于分包过程中的【高频写操作】 +imei
     */
    private static final String UPGRADE_RUNTIME_KEY_PREFIX = "fota:upgrade:runtime:";
    /**
     * 设备基础信息 web服务所使用的key【低频更新】+imei
     */
    private static final String DEVICE_CACHE_KEY_PREFIX = "fota:device:";

    /**
     * 在线状态
     */
    private static final String DEVICE_ONLINE_ZSET_KEY = "fota:device:online:zset";

    private static final String SEMAPHORE_KEY = "fota:upgrade:holders";

    private final StringRedisTemplate redisTemplate;
    private final DeviceGroupService deviceGroupService;
    private final DeviceGroupRelationService deviceGroupRelationService;
    private final UpgradeTaskMapper upgradeTaskMapper;


    public DeviceServiceImpl(StringRedisTemplate redisTemplate,
                             DeviceGroupService deviceGroupService,
                             DeviceGroupRelationService deviceGroupRelationService,
                             UpgradeTaskMapper upgradeTaskMapper) {
        this.redisTemplate = redisTemplate;
        this.deviceGroupService = deviceGroupService;
        this.deviceGroupRelationService = deviceGroupRelationService;
        this.upgradeTaskMapper = upgradeTaskMapper;
    }

    @Override
    public DeviceEntity getDeviceByImei(String imei) {
        QueryWrapper<DeviceEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("imei", imei);
        return this.baseMapper.selectOne(queryWrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public DeviceEntity addDevice(DeviceSaveRequest request) {
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
        boolean save = this.save(entity);
        saveRelation(entity.getId(), request.getDeviceGroupId());
        if (save) {
            upsertDeviceCache(entity);
        }
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int delteDevice(DeviceEntity entity) {
        int deleted = this.baseMapper.deleteById(entity.getId());
        deviceGroupRelationService.remove(new LambdaQueryWrapper<DeviceGroupRelationEntity>()
                .eq(DeviceGroupRelationEntity::getDeviceId, entity.getId()));
        //将upgrade_task表中的设备更新为逻辑删除
        upgradeTaskMapper.deleteUpgradeTask(entity.getId());
        if (deleted==1) {
            //删除设备
            redisTemplate.delete(deviceCacheKey(entity.getImei()));
            //删除在离线状态
            redisTemplate.opsForZSet().remove(DEVICE_ONLINE_ZSET_KEY, String.valueOf(entity.getId()));
            //删除设备升级过程中的 runtimekey
            String runtimeKey = UPGRADE_RUNTIME_KEY_PREFIX + entity.getImei();
            redisTemplate.delete(runtimeKey);
            //删除设备升级进度条
            String lastProgressKey = runtimeKey + ":lastPushProgress";
            redisTemplate.delete(lastProgressKey);
            //删除占用的信号量
            redisTemplate.opsForSet().remove(SEMAPHORE_KEY, String.valueOf(entity.getImei()));
        }
        return deleted;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateDevice(DeviceEntity entity, DeviceSaveRequest request) {
        Long id = request.getId();
        entity.setImei(request.getImei());
        entity.setDeviceName(request.getDeviceName());
        entity.setDeviceType(request.getDeviceType());
        entity.setCurrentFirmwareVersion(request.getCurrentFirmwareVersion());
        if (!Objects.equals(entity.getTargetFirmwareId(), request.getTargetFirmwareId())) {
            entity.setDeviceUpgradeStatus("NO_TASK");
            //删除旧的升级任务缓存的key
            String runtimeKey = UPGRADE_RUNTIME_KEY_PREFIX + entity.getImei();
            String lastProgressKey =  runtimeKey + ":lastPushProgress";
            redisTemplate.delete(runtimeKey);
            redisTemplate.delete(lastProgressKey);
        }

        entity.setTargetFirmwareId(request.getTargetFirmwareId());
        entity.setIsBind(resolveBindStatus(request.getTargetFirmwareId()));
        entity.setUpdatedAt(LocalDateTime.now());
        this.update(new LambdaUpdateWrapper<DeviceEntity>()
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

    }


    @Override
    public List<String> getImeiByDeviceIds(List<Long> deivceIds) {
        return this.baseMapper.getImeiList(deivceIds);
    }

    private Integer resolveBindStatus(Long targetFirmwareId) {
        return targetFirmwareId == null ? 0 : 1;
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

    public void upsertDeviceCache(DeviceEntity entity) {
        String deviceKey = deviceCacheKey(entity.getImei());

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

    private String deviceCacheKey(String imei) {
        return DEVICE_CACHE_KEY_PREFIX + imei;
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

}
