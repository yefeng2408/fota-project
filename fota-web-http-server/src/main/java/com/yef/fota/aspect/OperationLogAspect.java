package com.yef.fota.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yef.fota.annotation.OperationLog;
import com.yef.fota.auth.AuthContext;
import com.yef.fota.entity.OperateLogEntity;
import com.yef.fota.service.OperateLogService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

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
        Object[] args = joinPoint.getArgs();
        List<Object> safeArgs = Arrays.stream(args)
                .filter(arg -> !(arg instanceof MultipartFile))
                .collect(Collectors.toList());
        try {
            OperateLogEntity logEntity = new OperateLogEntity();
            logEntity.setUserId(AuthContext.getUserId());
            logEntity.setAction(operationLog.action());
            logEntity.setCreatedAt(LocalDateTime.now());
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String path = attributes == null ? "" : attributes.getRequest().getRequestURI();
            logEntity.setDetail(path + " | " + objectMapper.writeValueAsString(safeArgs));
            operateLogService.save(logEntity);
        } catch (Exception ex) {
            log.warn("Failed to persist operation log", ex);
        }
        return result;
    }
}
