package com.yef.fota.controller;

import com.yef.fota.api.dto.CancelUpgradeRequest;
import com.yef.fota.api.dto.StartUpgradeRequest;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.entity.UpgradeTaskEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.service.DeviceService;
import com.yef.fota.service.FirmwarePackageService;
import com.yef.fota.service.UpgradeTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Arrays;
import java.util.List;

/**
 *--------------------------- 平台下行（Platform → Device） --------------------
 * ---------------------------------------------------------------------------
 * | messageType | 名称              | 说明                                     |
 * ---------------------------------------------------------------------------
 * |             |                   | 开始升级指令                              |
 * |             |                   | 平台触发升级流程，下发固件元信息             |
 * | 0x81        |  UpgradeRequest   | 设备收到后需返回 ACK(ackType=1)            |
 * |             |                   | 平台收到 ACK 后，状态进入 UPGRADING         |
 * ---------------------------------------------------------------------------
 * |             |                   | 取消升级指令                              |
 * | 0x87        | CancelUpgrade     | 用户手动触发取消升级                       |
 * |             |                   | 设备收到后应停止升级并返回 ACK(ackType=4)   |
 * ----------------------------------------------------------------------------|
 *
 * @description: 平台指令下发
 * @author: 叶丰
 * @date: 2026/04/16 09:22
 */
@RestController
@RequestMapping("/api/upgrade-task")
@RequiredArgsConstructor
public class UpgradeCommandController {
    /**
     * 升级状态
     */
    private final static List<String> UPGRADING_STATE = Arrays.asList("UPGRADE_REQUESTED,UPGRADING,WAIT_RESULT".split(","));
    /**
     * 升级运行态 设备基础信息 设备网关所用的key，用于分包过程中的【高频写操作】   前缀拼接IMEI
     */
    private static final String UPGRADE_RUNTIME_KEY_PREFIX = "fota:upgrade:runtime:";

    private final DeviceService deviceService;
    private final FirmwarePackageService firmwarePackageService;
    private final UpgradeTaskService upgradeTaskService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 开始升级。 下发0x81请求升级指令
     * @param request
     * @return
     */
    @PostMapping("/start")
    public ApiResponse<Long> startUpgrade(@RequestBody StartUpgradeRequest request) {

        DeviceEntity device = deviceService.getDeviceByImei(request.getImei());
        if (device == null) {
            throw new BusinessException("设备不存在");
        }

        if (device.getIsBind() == null || device.getIsBind() != 1) {
            throw new BusinessException("设备未绑定固件，不可升级");
        }
        if (!"NO_TASK".equals(device.getDeviceUpgradeStatus())) {
            throw new BusinessException("设备当前状态不可升级");
        }

        FirmwarePackageEntity firmware = firmwarePackageService.getById(device.getTargetFirmwareId());
        if (firmware == null) {
            throw new BusinessException("目标固件不存在");
        }
        if (!StringUtils.hasText(firmware.getFileName())) {
            throw new IllegalArgumentException("固件文件名不能为空");
        }
        if (!StringUtils.hasText(firmware.getVersion())) {
            throw new IllegalArgumentException("固件版本号不能为空");
        }

        /*if (upgradeTaskService.countWaitingTask() >1000) {
            throw new BusinessException("当前处于等待升级的设备数量过多，请稍后升级");
        }*/

        if (!"NO_TASK".equals(device.getDeviceUpgradeStatus())) {
            throw new BusinessException("设备当前存在升级任务，请勿重复发起升级操作");
        }
        Long taskId = upgradeTaskService.startUpgrade(device, firmware);
        //这里如何拿到本次0x81的应答ack呢【messageType = 0x03，ackType =1】？
        // 因为我想给页面提示设备给出的应答结果，若设备的ack表示成功接受该指令，我在页面提示：“下发升级指令成功！”
        //TODO 异步消息等设备0x03上行至网关，再由网关调用web服务接口，然后web推送websocket
        return ApiResponse.ok("已发起升级请求，等待设备确认.", taskId);
    }


    /**
     * 开始升级。 下发0x81请求升级指令
     * @param request
     * @return
     */
    @PostMapping("/cancel")
    public ApiResponse<Long> cancelUpgrade(@RequestBody CancelUpgradeRequest request) {

        DeviceEntity device = deviceService.getDeviceByImei(request.getImei());
        if (device == null) {
            throw new BusinessException("设备不存在");
        }

        UpgradeTaskEntity upgradeTask = upgradeTaskService.selectUpgradingTask(device.getImei());

        Object obj = redisTemplate.opsForHash().get(UPGRADE_RUNTIME_KEY_PREFIX + request.getImei(), "status");
        if(upgradeTask ==null || obj==null
                || org.apache.commons.lang3.StringUtils.isBlank(String.valueOf(obj))
                || !UPGRADING_STATE.contains(String.valueOf(obj))) {
            throw new BusinessException("设备未处于升级状态");
        }
        request.setTaskId(upgradeTask.getTaskId());
        upgradeTaskService.cancelUpgrade(request);
        return ApiResponse.ok("已发取消级请求，等待设备确认.",null);
    }




}