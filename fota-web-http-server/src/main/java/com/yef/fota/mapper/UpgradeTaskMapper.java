package com.yef.fota.mapper;

import com.yef.fota.entity.UpgradeTaskEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 设备升级任务表 Mapper 接口
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Mapper
public interface UpgradeTaskMapper extends BaseMapper<UpgradeTaskEntity> {


    /**
     * 逻辑删除设备升级任务纪录
     * @param deviceId
     * @return
     */
    @Update("update upgrade_task set is_delete='1' where device_id = #{deviceId}")
    int deleteUpgradeTask(Long deviceId);

    /**
     * 批量逻辑删除
     * @param deviceIds
     * @return
     */
    @Update("<script>" +
            "update upgrade_task set is_delete='1' where device_id in " +
            "<foreach collection='deviceIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int deleteBatchUpgradeTask(List<Long> deviceIds);

    @Select("select * from upgrade_task" +
            " where imei = #{imei}" +
            " and task_status in ('UPGRADE_REQUESTED','UPGRADING','WAIT_RESULT')" +
            " order by id desc limit 1")
    UpgradeTaskEntity  selectUpgradingTaskByImei(String imei);


    @Update("update upgrade_task set start_time=#{startTime} where task_id=#{taskId}")
    int updateTaskStartTime(@Param("taskId") Long taskId, @Param("startTime") LocalDateTime startTime);


    List<UpgradeTaskEntity> selectRunnableTasks(int limit);


    int casToRequested(Long id);


    int countWaitingTask();


    void updateRetry(
            @Param("id") Long id,
            @Param("retryCount") int retryCount,
            @Param("nextRetryAt") LocalDateTime nextRetryAt,
            @Param("error") String error
    );

    void markFail(
            @Param("id") Long id,
            @Param("errorMsg") String errorMsg
    );

}
