package com.yef.fota.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yef.fota.annotation.OperationLog;
import com.yef.fota.auth.AuthContext;
import com.yef.fota.entity.OperateLogEntity;
import com.yef.fota.service.OperateLogService;
import java.time.LocalDateTime;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperateLogService operateLogService;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            OperateLogEntity logEntity = new OperateLogEntity();
            logEntity.setUserId(AuthContext.getUserId());
            logEntity.setAction(operationLog.action());
            logEntity.setCreatedAt(LocalDateTime.now());
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String path = attributes == null ? "" : attributes.getRequest().getRequestURI();
            logEntity.setDetail(path + " | " + objectMapper.writeValueAsString(Arrays.stream(joinPoint.getArgs()).limit(3).toArray()));
            operateLogService.save(logEntity);
        } catch (Exception ex) {
            log.warn("Failed to persist operation log", ex);
        }
        return result;
    }
}
