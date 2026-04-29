package com.yef.fota.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.annotation.OperationLog;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.dto.device.DeviceImportResponse;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/devices")
public class DeviceController {


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


    private final DeviceService deviceService;
    private final DeviceGroupService deviceGroupService;
    private final DeviceGroupRelationService deviceGroupRelationService;
    private final FirmwarePackageService firmwarePackageService;
    private final StringRedisTemplate redisTemplate;

    @GetMapping
    public ApiResponse<PageResult<DeviceVO>> page(@RequestParam(defaultValue = "1") long current,
                                                  @RequestParam(defaultValue = "6") long pageSize,
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
     *
     * @param request
     * @return
     */
    @PostMapping
    @OperationLog(action = "CREATE_DEVICE")
    public ApiResponse<DeviceVO> create(@RequestBody @Valid DeviceSaveRequest request) {
        validateImeiUnique(request.getImei(), null);
        DeviceEntity deviceEntity = deviceService.addDevice(request);
        return ApiResponse.ok(toDeviceVO(deviceEntity));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @OperationLog(action = "IMPORT_DEVICE")
    public ApiResponse<DeviceImportResponse> importDevices(@RequestParam("file") MultipartFile file,
                                                           @RequestParam Long deviceGroupId,
                                                           @RequestParam String deviceType,
                                                           @RequestParam(required = false) Long targetFirmwareId) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Excel 文件");
        }
        String fileName = file.getOriginalFilename();
        String lowerFileName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(fileName) || (!lowerFileName.endsWith(".xlsx") && !lowerFileName.endsWith(".xls"))) {
            throw new BusinessException("仅支持上传 .xls 或 .xlsx 格式文件");
        }
        DeviceGroupEntity group = deviceGroupService.getById(deviceGroupId);
        if (group == null) {
            throw new BusinessException("设备分组不存在");
        }
        FirmwarePackageEntity targetFirmware = null;
        if (targetFirmwareId != null) {
            targetFirmware = firmwarePackageService.getById(targetFirmwareId);
            if (targetFirmware == null) {
                throw new BusinessException("目标固件不存在");
            }
            if (StringUtils.hasText(targetFirmware.getDeviceType()) && !targetFirmware.getDeviceType().equals(deviceType)) {
                throw new BusinessException("所选固件与设备类型不匹配");
            }
        }

        DeviceImportResponse response = new DeviceImportResponse();
        DataFormatter formatter = new DataFormatter();
        Set<String> excelImeis = new HashSet<>();
        List<DeviceSaveRequest> requests = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new BusinessException("Excel 内容为空");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            int imeiColumnIndex = findColumnIndex(headerRow, formatter, "imei");
            int deviceNameColumnIndex = findColumnIndex(headerRow, formatter, "设备名称");
            if (imeiColumnIndex < 0) {
                throw new BusinessException("Excel 表头缺少 imei 列");
            }

            int totalRows = 0;
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isRowEmpty(row, formatter)) {
                    continue;
                }
                totalRows++;
                String imei = normalizeCellValue(row.getCell(imeiColumnIndex), formatter);
                String deviceName = deviceNameColumnIndex >= 0
                        ? normalizeCellValue(row.getCell(deviceNameColumnIndex), formatter)
                        : "";

                if (!imei.matches("\\d{8}")) {
                    response.setInvalidImeiCount(response.getInvalidImeiCount() + 1);
                    continue;
                }
                if (!excelImeis.add(imei)) {
                    response.setDuplicateInFileCount(response.getDuplicateInFileCount() + 1);
                    continue;
                }

                DeviceSaveRequest request = new DeviceSaveRequest();
                request.setImei(imei);
                request.setDeviceName(StringUtils.hasText(deviceName) ? deviceName : imei);
                request.setDeviceType(deviceType);
                request.setCurrentFirmwareVersion(null);
                request.setDeviceUpgradeStatus("NO_TASK");
                request.setTargetFirmwareId(targetFirmware == null ? null : targetFirmware.getId());
                request.setDeviceGroupId(deviceGroupId);
                requests.add(request);
            }
            response.setTotalRows(totalRows);
        }

        if (requests.isEmpty()) {
            response.setSummary(buildImportSummary(response));
            return ApiResponse.ok(response);
        }

        Set<String> requestImeis = requests.stream().map(DeviceSaveRequest::getImei).collect(Collectors.toSet());
        Set<String> existsImeis = deviceService.lambdaQuery()
                .in(DeviceEntity::getImei, requestImeis)
                .list()
                .stream()
                .map(DeviceEntity::getImei)
                .collect(Collectors.toSet());

        List<DeviceSaveRequest> importableRequests = requests.stream()
                .filter(request -> !existsImeis.contains(request.getImei()))
                .collect(Collectors.toList());

        response.setDuplicateInDatabaseCount(existsImeis.size());

        for (DeviceSaveRequest request : importableRequests) {
            deviceService.addDevice(request);
        }
        response.setImportedCount(importableRequests.size());
        response.setSummary(buildImportSummary(response));
        return ApiResponse.ok(response);
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

        Long targetFirmwareId = request.getTargetFirmwareId();
        FirmwarePackageEntity packageEntity = firmwarePackageService.getBaseMapper().selectById(targetFirmwareId);
        if(packageEntity==null){
            throw new BusinessException("所选固件不存在");
        }

        request.setId(id);
        deviceService.updateDevice(entity,request);

        return ApiResponse.ok(toDeviceVO(entity));
    }


    /**
     * 删除设备
     *
     * @param id
     * @return
     */
    @DeleteMapping("/{id}")
    @OperationLog(action = "DELETE_DEVICE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        DeviceEntity entity = deviceService.getById(id);
        if (entity == null) {
            return ApiResponse.fail("该设备不存在.");
        }
        validateDeviceEditable(entity);
        boolean deleted = deviceService.removeById(id);
        deviceGroupRelationService.remove(new LambdaQueryWrapper<DeviceGroupRelationEntity>()
                .eq(DeviceGroupRelationEntity::getDeviceId, id));
        if (deleted) {
            //删除设备
            redisTemplate.delete(deviceCacheKey(entity.getImei()));
            //删除在离线状态
            redisTemplate.opsForZSet().remove(DEVICE_ONLINE_ZSET_KEY, String.valueOf(entity.getId()));
            //删除设备升级锁
            String runtimeKey = UPGRADE_RUNTIME_KEY_PREFIX + entity.getImei();
            redisTemplate.delete(runtimeKey);
            //删除设备升级进度条
            String lastProgressKey = runtimeKey + ":lastPushProgress";
            redisTemplate.delete(lastProgressKey);
        }
        return ApiResponse.ok(null);
    }


    private int findColumnIndex(Row headerRow, DataFormatter formatter, String expectedHeader) {
        if (headerRow == null) {
            return -1;
        }
        for (Cell cell : headerRow) {
            String cellValue = normalizeCellValue(cell, formatter).trim();
            if (expectedHeader.equalsIgnoreCase(cellValue)) {
                return cell.getColumnIndex();
            }
        }
        return -1;
    }

    private String normalizeCellValue(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell).trim();
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        int firstCell = row.getFirstCellNum();
        int lastCell = row.getLastCellNum();
        if (firstCell < 0 || lastCell < 0) {
            return true;
        }
        for (int i = firstCell; i < lastCell; i++) {
            if (StringUtils.hasText(normalizeCellValue(row.getCell(i), formatter))) {
                return false;
            }
        }
        return true;
    }

    private String buildImportSummary(DeviceImportResponse response) {
        return String.format("导入完成：总行数 %d，成功 %d，Excel 内重复 %d，数据库重复 %d，无效 IMEI %d",
                response.getTotalRows(),
                response.getImportedCount(),
                response.getDuplicateInFileCount(),
                response.getDuplicateInDatabaseCount(),
                response.getInvalidImeiCount());
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
        //拿到所有设备在线状态
        long now = System.currentTimeMillis();
        Set<String> onlineDeviceIdSet = redisTemplate.opsForZSet()
                .rangeByScore("fota:device:online:zset", now - 60_000L, now);
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
            //在线状态
            boolean online = onlineDeviceIdSet != null && onlineDeviceIdSet.contains(String.valueOf(entity.getId()));
            vo.setIsOnline(online ? 1 : 0);
            //计算是否可升级
            if (vo.getIsBind() == 1 && vo.getIsOnline() == 1 && "NO_TASK".equals(vo.getDeviceUpgradeStatus())) {
                vo.setIsUpgradable("Y");
            }
            DeviceGroupRelationEntity relation = relationMap.get(entity.getId());
            if (relation != null) {
                vo.setDeviceGroupId(relation.getDeviceGroupId());
                DeviceGroupEntity group = groupMap.get(relation.getDeviceGroupId());
                vo.setDeviceGroupName(group == null ? null : group.getDeviceGroupName());
            }
            String runtimeKey = UPGRADE_RUNTIME_KEY_PREFIX + entity.getImei();
            Map<Object, Object> runtimeMap = redisTemplate.opsForHash().entries(runtimeKey);
            if (runtimeMap.containsKey("status")) {
                vo.setDeviceUpgradeStatus(String.valueOf(runtimeMap.get("status")));
            }
            if (runtimeMap.containsKey("progress")) {
                vo.setProgress(Integer.parseInt(String.valueOf(runtimeMap.get("progress"))));
            }
            return vo;
        }).collect(Collectors.toList());
    }


    private String deviceCacheKey(String imei) {
        return DEVICE_CACHE_KEY_PREFIX + imei;
    }


    private DeviceVO toDeviceVO(DeviceEntity entity) {
        return toDeviceVOs(List.of(entity)).get(0);
    }

    /**
     * 安全校验。处于升级环节中的设备，不可以操作
     *
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
