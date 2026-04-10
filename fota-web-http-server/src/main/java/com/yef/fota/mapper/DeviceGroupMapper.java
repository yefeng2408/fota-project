package com.yef.fota.mapper;

import com.yef.fota.entity.DeviceGroupEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 设备分组表（树结构） Mapper 接口
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Mapper
public interface DeviceGroupMapper extends BaseMapper<DeviceGroupEntity> {

}
