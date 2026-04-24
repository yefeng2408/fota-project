package com.yef.fota.mapper;

import com.yef.fota.entity.UpgradeTaskEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

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

    @Update("""
        UPDATE upgrade_task
        SET status = #{status},
            status_version = #{version},
            status_event_time = #{eventTime},
            updated_at = NOW()
        WHERE id = #{taskId}
          AND status_version < #{version}
        """)
    int updateTaskStatusIfNewer(@Param("taskId") Long taskId,
                                @Param("status") String status,
                                @Param("version") Long version,
                                @Param("eventTime") Long eventTime);

    @Delete("delete from upgrade_task where task_id=#{taskId}")
    int delByTaskId(@Param("taskId") Long taskId);


    @Select("select * from upgrade_task" +
            " where imei = #{imei}" +
            " and task_status in ('UPGRADE_REQUESTED','UPGRADING','WAIT_RESULT')" +
            " order by id desc limit 1")
    UpgradeTaskEntity  selectUpgradeTask(String imei);


    @Select("select * from upgrade_task")
    List<UpgradeTaskEntity> selectAll();

}
