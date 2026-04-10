package com.yef.fota.mapper;

import com.yef.fota.entity.UserDeviceGroupEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 用户与设备组权限关系表 Mapper 接口
 * </p>
 *
 * @author yef
 * @since 2026-04-10
 */
@Mapper
public interface UserDeviceGroupMapper extends BaseMapper<UserDeviceGroupEntity> {

}
