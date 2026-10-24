package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.PageResult;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.model.vo.admin.AdminUserVO;
import com.example.api_service.service.AdminUserManageService;
import com.example.api_service.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserManageServiceImpl implements AdminUserManageService {

    private static final int STATUS_BANNED = 0;

    private final SysUserService sysUserService;

    @Override
    public PageResult<AdminUserVO> list(long page, long size, String keyword, Integer status) {
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<SysUser> result = sysUserService.lambdaQuery()
                .and(trimmedKeyword != null, wrapper -> wrapper
                        .like(SysUser::getUsername, trimmedKeyword)
                        .or()
                        .like(SysUser::getEmail, trimmedKeyword))
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getCreateTime)
                .page(new Page<>(page, size));

        List<AdminUserVO> records = result.getRecords().stream()
                .map(AdminUserVO::from)
                .toList();
        return PageResult.of(records, result.getTotal(),
                (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long operatorId, Long userId, Integer status) {
        SysUser user = getExistingUser(userId);
        if (operatorId.equals(userId) && Integer.valueOf(STATUS_BANNED).equals(status)) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "不能封禁当前登录的管理员账号");
        }
        if (Integer.valueOf(status).equals(user.getStatus())) {
            return;
        }
        user.setStatus(status);
        sysUserService.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserVO recharge(Long userId, long amount) {
        getExistingUser(userId);
        if (!sysUserService.addQuota(userId, amount)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return AdminUserVO.from(getExistingUser(userId));
    }

    private SysUser getExistingUser(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }
}
