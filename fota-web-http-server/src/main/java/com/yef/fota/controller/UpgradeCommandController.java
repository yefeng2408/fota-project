package com.yef.fota.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.yef.fota.api.dto.GatewayUpgradeRequest;
import com.yef.fota.api.dto.StartUpgradeRequest;
import com.yef.fota.api.service.GatewayCommandService;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.entity.DeviceEntity;
import com.yef.fota.entity.FirmwarePackageEntity;
import com.yef.fota.entity.UpgradeTaskEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.service.DeviceService;
import com.yef.fota.service.FirmwarePackageService;
import com.yef.fota.service.UpgradeTaskService;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDateTime;

/**
 *🟢 3️⃣ ACK确认（messageType = 0x03） 多语义 ACK（Multi-semantic ACK）
 *     taskId        (8 byte)
 *     packetNo      (4 byte)
 *     ackType       (1 byte)
 *
 *                   ackType 类型说明：
 *                       ackType =1        UPGRADE_REQUEST_ACK      （上行消息。对应着下行0x81消息类型的ACK）
 *                       ackType =2        PACKET_ACK               （上行消息。对应着下行0x82消息类型的ACK）
 *                       ackType =4        CANCEL_ACK               （上行消息。对应着下行0x87消息类型的ACK）
 *                       ackType =5        HEARTBEAT                 (上行消息。平台无需回复设备的心跳消息)
 *                   说明：
 *                       - packetNo 在 ackType=1（升级请求ACK）时可为0
 *                       - packetNo 在分包ACK时必须对应具体分包序号
 *                       - PACKET_LAST_ACK 用于通知平台“分包已全部接收完成”，但不代表升级成功
 *
 * @description: 平台指令下发
 * @author: 叶丰
 * @date: 2026/04/16 09:22
 */
@RestController
@RequestMapping("/api/upgrade-task")
@RequiredArgsConstructor
public class UpgradeCommandController {

    private final DeviceService deviceService;
    private final FirmwarePackageService firmwarePackageService;
    private final UpgradeTaskService upgradeTaskService;
    private final GatewayCommandService gatewayCommandService;

    /**
     * 下发0x81请求升级指令
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
        Long taskId = upgradeTaskService.startUpgrade(device, firmware);
        //TODO========
        //这里如何拿到本次0x81的应答ack呢【messageType = 0x03，ackType =1】？
        // 因为我想给页面提示设备给出的应答结果，若设备的ack表示成功接受该指令，我在页面提示：“下发升级指令成功！”
        return ApiResponse.ok("已发起升级请求，等待设备确认.", taskId);
    }







}