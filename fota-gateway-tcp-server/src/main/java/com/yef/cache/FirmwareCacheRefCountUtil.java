package com.yef.cache;

import cn.hutool.extra.spring.SpringUtil;
import org.springframework.context.ApplicationContext;

/**
 * @description:
 * @author: 叶丰
 * @date: 2026/5/7 10:24
 */
public class FirmwareCacheRefCountUtil {


    public static void decrement(Long firmwareId) {
        FirmwareCacheManager firmwareCacheManager = SpringUtil.getBean(FirmwareCacheManager.class);
        if(firmwareCacheManager != null){
            FirmwareCacheHolder firmwareCacheHolder = firmwareCacheManager.get(firmwareId);
            if(firmwareCacheHolder != null){
                firmwareCacheHolder.getRefCount().decrementAndGet();
                firmwareCacheHolder.setLastAccessAt(System.currentTimeMillis());
            }
        }
    }


}