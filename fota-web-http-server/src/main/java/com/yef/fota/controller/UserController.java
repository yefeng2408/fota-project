package com.yef.fota.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yef.fota.annotation.OperationLog;
import com.yef.fota.auth.AuthContext;
import com.yef.fota.auth.JwtTokenProvider;
import com.yef.fota.common.ApiResponse;
import com.yef.fota.common.PageResult;
import com.yef.fota.dto.auth.LoginRequest;
import com.yef.fota.dto.auth.LoginResponse;
import com.yef.fota.dto.user.UserSaveRequest;
import com.yef.fota.dto.user.UserVO;
import com.yef.fota.entity.UserDeviceGroupEntity;
import com.yef.fota.entity.UserEntity;
import com.yef.fota.exception.BusinessException;
import com.yef.fota.service.UserDeviceGroupService;
import com.yef.fota.service.UserService;
import com.yef.fota.util.PasswordUtils;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class UserController {

    private final UserService userService;
    private final UserDeviceGroupService userDeviceGroupService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/auth/login")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        UserEntity user = userService.lambdaQuery()
                .eq(UserEntity::getPhone, request.getPhone())
                .one();
        if (user == null || !PasswordUtils.sha256(request.getPassword()).equals(user.getPassword())) {
            throw new BusinessException("手机号或密码错误");
        }
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        return ApiResponse.ok(new LoginResponse(token, user.getId(), user.getUsername()));
    }

    @PostMapping("/auth/register")
    public ApiResponse<UserVO> register(@RequestBody @Valid UserSaveRequest request) {
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException("注册时密码不能为空");
        }
        return ApiResponse.ok(saveUser(null, request));
    }

    @GetMapping("/auth/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.ok(Map.of(
                "userId", AuthContext.getUserId(),
                "username", AuthContext.getUsername()
        ));
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<UserVO>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long pageSize,
                                                @RequestParam(required = false) String keyword) {
        Page<UserEntity> page = userService.lambdaQuery()
                .and(StringUtils.hasText(keyword), wrapper -> wrapper.like(UserEntity::getUsername, keyword)
                        .or()
                        .like(UserEntity::getPhone, keyword))
                .page(new Page<>(current, pageSize));
        List<UserVO> records = page.getRecords().stream().map(this::toUserVO).collect(Collectors.toList());
        return ApiResponse.ok(new PageResult<>(page.getCurrent(), page.getSize(), page.getTotal(), records));
    }

    @PostMapping("/users")
    @OperationLog(action = "CREATE_USER")
    public ApiResponse<UserVO> create(@RequestBody @Valid UserSaveRequest request) {
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException("新增用户时密码不能为空");
        }
        return ApiResponse.ok(saveUser(null, request));
    }

    @PutMapping("/users/{id}")
    @OperationLog(action = "UPDATE_USER")
    public ApiResponse<UserVO> update(@PathVariable Long id, @RequestBody @Valid UserSaveRequest request) {
        return ApiResponse.ok(saveUser(id, request));
    }

    @DeleteMapping("/users/{id}")
    @OperationLog(action = "DELETE_USER")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.removeById(id);
        userDeviceGroupService.remove(new LambdaQueryWrapper<UserDeviceGroupEntity>().eq(UserDeviceGroupEntity::getUserId, id));
        return ApiResponse.ok(null);
    }

    private UserVO saveUser(Long id, UserSaveRequest request) {
        UserEntity existsByPhone = userService.lambdaQuery()
                .eq(UserEntity::getPhone, request.getPhone())
                .ne(id != null, UserEntity::getId, id)
                .one();
        if (existsByPhone != null) {
            throw new BusinessException("手机号已存在");
        }

        UserEntity entity = id == null ? new UserEntity() : userService.getById(id);
        if (entity == null) {
            throw new BusinessException("用户不存在");
        }
        entity.setUsername(request.getUsername());
        entity.setPhone(request.getPhone());
        if (StringUtils.hasText(request.getPassword())) {
            entity.setPassword(PasswordUtils.sha256(request.getPassword()));
        } else if (id == null) {
            throw new BusinessException("密码不能为空");
        }
        if (id == null) {
            entity.setCreatedAt(LocalDateTime.now());
            userService.save(entity);
        } else {
            userService.updateById(entity);
        }

        userDeviceGroupService.remove(new LambdaQueryWrapper<UserDeviceGroupEntity>().eq(UserDeviceGroupEntity::getUserId, entity.getId()));
        List<Long> groupIds = request.getDeviceGroupIds() == null ? Collections.emptyList() : request.getDeviceGroupIds();
        for (Long groupId : groupIds) {
            UserDeviceGroupEntity relation = new UserDeviceGroupEntity();
            relation.setUserId(entity.getId());
            relation.setDeviceGroupId(groupId);
            relation.setRole("OPERATOR");
            relation.setCreatedAt(LocalDateTime.now());
            userDeviceGroupService.save(relation);
        }
        return toUserVO(entity);
    }

    private UserVO toUserVO(UserEntity entity) {
        UserVO vo = new UserVO();
        vo.setId(entity.getId());
        vo.setUsername(entity.getUsername());
        vo.setPhone(entity.getPhone());
        vo.setCreatedAt(entity.getCreatedAt());
        List<Long> groupIds = userDeviceGroupService.list(new LambdaQueryWrapper<UserDeviceGroupEntity>()
                        .eq(UserDeviceGroupEntity::getUserId, entity.getId()))
                .stream()
                .map(UserDeviceGroupEntity::getDeviceGroupId)
                .collect(Collectors.toList());
        vo.setDeviceGroupIds(groupIds);
        return vo;
    }
}
