package com.example.api_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api_service.model.dto.permission.AdminPermissionRow;
import com.example.api_service.model.dto.permission.EndpointMarketRow;
import com.example.api_service.model.entity.UserApiPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserApiPermissionMapper extends BaseMapper<UserApiPermission> {

    /**
     * 接口市场：以 api_endpoint_meta 为主表左连接当前用户的权限记录。
     *
     * <p>一次查询取回目录与申请状态，避免逐条接口查询权限造成 N+1。</p>
     */
    @Select("""
            SELECT e.id                  AS endpoint_id,
                   e.name                AS endpoint_name,
                   e.path                AS endpoint_path,
                   e.method              AS endpoint_method,
                   e.cost_per_call       AS base_cost,
                   e.description         AS description,
                   e.request_schema      AS request_schema,
                   e.response_schema     AS response_schema,
                   e.status              AS endpoint_status,
                   p.id                  AS permission_id,
                   p.status              AS apply_status,
                   p.custom_discount     AS custom_discount,
                   p.audit_remark        AS audit_remark,
                   p.create_time         AS apply_time
            FROM api_endpoint_meta e
            LEFT JOIN user_api_permission p
                   ON p.endpoint_id = e.id
                  AND p.user_id = #{userId}
                  AND p.is_deleted = 0
            WHERE e.is_deleted = 0
            ORDER BY e.id
            """)
    List<EndpointMarketRow> selectMarketForUser(@Param("userId") Long userId);

    /**
     * 管理员工单分页：左连接申请人与接口目录，补齐用户名、接口名与基础点数。
     */
    @Select("""
            <script>
            SELECT p.id,
                   p.user_id,
                   u.username            AS username,
                   p.endpoint_id,
                   e.name                AS endpoint_name,
                   e.path                AS endpoint_path,
                   e.method              AS endpoint_method,
                   e.cost_per_call       AS base_cost,
                   p.apply_reason,
                   p.status,
                   p.custom_discount,
                   p.audit_remark,
                   p.auditor_id,
                   p.audit_time,
                   p.create_time
            FROM user_api_permission p
            LEFT JOIN sys_user u
                   ON u.id = p.user_id
                  AND u.is_deleted = 0
            LEFT JOIN api_endpoint_meta e
                   ON e.id = p.endpoint_id
                  AND e.is_deleted = 0
            WHERE p.is_deleted = 0
            <if test="status != null">
              AND p.status = #{status}
            </if>
            <if test="keyword != null and keyword != ''">
              AND (u.username LIKE CONCAT('%', #{keyword}, '%')
                   OR e.name LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY p.create_time DESC, p.id DESC
            </script>
            """)
    List<AdminPermissionRow> selectAdminPage(Page<AdminPermissionRow> page,
                                             @Param("status") Integer status,
                                             @Param("keyword") String keyword);
}
