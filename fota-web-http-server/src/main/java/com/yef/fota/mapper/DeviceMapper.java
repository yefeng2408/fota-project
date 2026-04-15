package com.yef.fota.mapper;

import com.yef.fota.entity.DeviceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 设备表 Mapper 接口
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Mapper
public interface DeviceMapper extends BaseMapper<DeviceEntity> {

    @Update("""
        UPDATE device
        SET device_upgrade_status = #{status},
            status_version = #{version},
            status_event_time = #{eventTime},
            updated_at = NOW()
        WHERE id = #{deviceId}
          AND status_version < #{version}
        """)
    int updateUpgradeStatusIfNewer(@Param("deviceId") Long deviceId,
                                   @Param("status") String status,
                                   @Param("version") Long version,
                                   @Param("eventTime") Long eventTime);

}
