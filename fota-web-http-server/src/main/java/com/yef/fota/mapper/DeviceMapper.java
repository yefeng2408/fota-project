package com.yef.fota.mapper;

import com.yef.fota.entity.DeviceEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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

    @Select("select * from device where imei=#{imei}")
    DeviceEntity selectDeviceByImei(@Param("imei") String imei);

    @Update("""
            UPDATE device
            SET device_upgrade_status = #{status},
                status_version = #{version},
                status_event_time = #{eventTime},
                updated_at = NOW()
            WHERE imei = #{imei}
              AND status_version < #{version}
            """)
    int updateUpgradeStatusIfNewer(@Param("imei") String imei,
                                   @Param("status") String status,
                                   @Param("version") Long version,
                                   @Param("eventTime") Long eventTime);


    @Select({
            "<script>",
            "SELECT imei",
            "FROM device",
            "WHERE id IN",
            "<foreach collection='deviceIds' item='deviceId' open='(' separator=',' close=')'>",
            "#{deviceId}",
            "</foreach>",
            "</script>"
    })
    List<String> getImeiList(@Param("deviceIds") List<Long> deviceIds);


    @Update("""
            UPDATE device
            SET device_upgrade_status = 'FAIL'
            WHERE imei = #{imei}
            """)
    void updateDeviceUpgradeStatusFail(@Param("imei") String imei);

}
