package com.yef.fota.mapper;

import com.yef.fota.entity.UpgradeTaskEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

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
}
