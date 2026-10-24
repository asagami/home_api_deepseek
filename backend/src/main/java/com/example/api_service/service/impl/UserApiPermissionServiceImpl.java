package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.PageResult;
import com.example.api_service.mapper.UserApiPermissionMapper;
import com.example.api_service.model.dto.permission.AdminPermissionRow;
import com.example.api_service.model.dto.permission.EndpointMarketRow;
import com.example.api_service.model.dto.permission.PermissionApplyRequest;
import com.example.api_service.model.dto.permission.PermissionAuditRequest;
import com.example.api_service.model.entity.ApiEndpointMeta;
import com.example.api_service.model.entity.UserApiPermission;
import com.example.api_service.model.enums.ApiPermissionStatus;
import com.example.api_service.model.enums.PermissionAuditAction;
import com.example.api_service.model.vo.permission.AdminPermissionVO;
import com.example.api_service.model.vo.permission.EndpointMarketVO;
import com.example.api_service.model.vo.permission.UserPermissionVO;
import com.example.api_service.service.ApiEndpointMetaService;
import com.example.api_service.service.UserApiPermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserApiPermissionServiceImpl
        extends ServiceImpl<UserApiPermissionMapper, UserApiPermission>
        implements UserApiPermissionService {

    private final ApiEndpointMetaService apiEndpointMetaService;

    @Override
    public List<EndpointMarketVO> market(Long userId) {
        List<EndpointMarketRow> rows = baseMapper.selectMarketForUser(userId);
        return rows.stream()
                .map(EndpointMarketVO::from)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserPermissionVO apply(Long userId, PermissionApplyRequest request) {
        ApiEndpointMeta endpoint = apiEndpointMetaService.getById(request.endpointId());
        if (endpoint == null) {
            throw new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND);
        }

        UserApiPermission existing = lambdaQuery()
                .eq(UserApiPermission::getUserId, userId)
                .eq(UserApiPermission::getEndpointId, request.endpointId())
                .one();
        String reason = request.applyReason().trim();

        if (existing == null) {
            UserApiPermission permission = new UserApiPermission();
            permission.setUserId(userId);
            permission.setEndpointId(request.endpointId());
            permission.setApplyReason(reason);
            permission.setStatus(ApiPermissionStatus.PENDING.getCode());
            permission.setCustomDiscount(BigDecimal.ONE);
            try {
                save(permission);
            } catch (DuplicateKeyException ex) {
                throw new BusinessException(ErrorCode.PERMISSION_ALREADY_PENDING);
            }
            return UserPermissionVO.from(permission);
        }

        if (ApiPermissionStatus.PENDING.matches(existing.getStatus())) {
            throw new BusinessException(ErrorCode.PERMISSION_ALREADY_PENDING);
        }
        if (ApiPermissionStatus.APPROVED.matches(existing.getStatus())) {
            throw new BusinessException(ErrorCode.PERMISSION_ALREADY_APPROVED);
        }

        // 已驳回或已收回：复用原记录重新提交，避免撞上 (user_id, endpoint_id, is_deleted) 唯一键
        boolean updated = lambdaUpdate()
                .eq(UserApiPermission::getId, existing.getId())
                .eq(UserApiPermission::getStatus, existing.getStatus())
                .set(UserApiPermission::getApplyReason, reason)
                .set(UserApiPermission::getStatus, ApiPermissionStatus.PENDING.getCode())
                .set(UserApiPermission::getCustomDiscount, BigDecimal.ONE)
                .set(UserApiPermission::getAuditRemark, null)
                .set(UserApiPermission::getAuditorId, null)
                .set(UserApiPermission::getAuditTime, null)
                .update();
        if (!updated) {
            throw new BusinessException(ErrorCode.CONFLICT, "申请状态已变更，请刷新后重试");
        }
        return UserPermissionVO.from(getById(existing.getId()));
    }

    @Override
    public PageResult<AdminPermissionVO> adminList(long page, long size, Integer status, String keyword) {
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<AdminPermissionRow> pageRequest = new Page<>(page, size);
        List<AdminPermissionRow> rows = baseMapper.selectAdminPage(pageRequest, status, trimmedKeyword);

        List<AdminPermissionVO> records = rows.stream()
                .map(AdminPermissionVO::from)
                .toList();
        return PageResult.of(records, pageRequest.getTotal(), (int) page, (int) size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserPermissionVO audit(Long operatorId, Long permissionId, PermissionAuditRequest request) {
        UserApiPermission permission = getById(permissionId);
        if (permission == null) {
            throw new BusinessException(ErrorCode.PERMISSION_RECORD_NOT_FOUND);
        }

        int currentStatus = permission.getStatus();
        int targetStatus = resolveTargetStatus(request.action(), currentStatus);
        String remark = StringUtils.hasText(request.auditRemark())
                ? request.auditRemark().trim()
                : null;
        if (request.action() == PermissionAuditAction.REJECT && remark == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "驳回申请时必须填写审批意见");
        }

        var update = lambdaUpdate()
                .eq(UserApiPermission::getId, permissionId)
                .eq(UserApiPermission::getStatus, currentStatus)
                .set(UserApiPermission::getStatus, targetStatus)
                .set(UserApiPermission::getAuditorId, operatorId)
                .set(UserApiPermission::getAuditTime, LocalDateTime.now())
                .set(UserApiPermission::getAuditRemark, remark);
        if (request.action() == PermissionAuditAction.APPROVE) {
            BigDecimal discount = request.customDiscount() != null
                    ? request.customDiscount()
                    : (permission.getCustomDiscount() == null
                    ? BigDecimal.ONE
                    : permission.getCustomDiscount());
            update.set(UserApiPermission::getCustomDiscount, discount);
        }

        if (!update.update()) {
            throw new BusinessException(ErrorCode.CONFLICT, "申请状态已变更，请刷新后重试");
        }
        log.debug("权限审批完成: permissionId={}, action={}, operatorId={}",
                permissionId, request.action(), operatorId);
        return UserPermissionVO.from(getById(permissionId));
    }

    @Override
    public UserApiPermission getApprovedPermission(Long userId, Long endpointId) {
        return lambdaQuery()
                .eq(UserApiPermission::getUserId, userId)
                .eq(UserApiPermission::getEndpointId, endpointId)
                .eq(UserApiPermission::getStatus, ApiPermissionStatus.APPROVED.getCode())
                .one();
    }

    private int resolveTargetStatus(PermissionAuditAction action, Integer currentStatus) {
        return switch (action) {
            case APPROVE -> {
                if (!ApiPermissionStatus.PENDING.matches(currentStatus)) {
                    throw new BusinessException(ErrorCode.CONFLICT, "只有待审批的申请可以通过");
                }
                yield ApiPermissionStatus.APPROVED.getCode();
            }
            case REJECT -> {
                if (!ApiPermissionStatus.PENDING.matches(currentStatus)) {
                    throw new BusinessException(ErrorCode.CONFLICT, "只有待审批的申请可以驳回");
                }
                yield ApiPermissionStatus.REJECTED.getCode();
            }
            case REVOKE -> {
                if (!ApiPermissionStatus.APPROVED.matches(currentStatus)) {
                    throw new BusinessException(ErrorCode.CONFLICT, "只有已通过的申请可以收回权限");
                }
                yield ApiPermissionStatus.REVOKED.getCode();
            }
        };
    }
}
