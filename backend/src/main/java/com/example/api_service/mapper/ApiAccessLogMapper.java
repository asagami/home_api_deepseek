package com.example.api_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api_service.model.dto.log.LogDailyStatRow;
import com.example.api_service.model.dto.log.admin.AdminLogDailyTrendRow;
import com.example.api_service.model.dto.log.admin.AdminLogEndpointRankRow;
import com.example.api_service.model.dto.log.admin.AdminLogPageReq;
import com.example.api_service.model.dto.log.admin.AdminLogPageRow;
import com.example.api_service.model.dto.log.admin.AdminLogSummaryRow;
import com.example.api_service.model.dto.log.admin.AdminLogUserConsumeRow;
import com.example.api_service.model.entity.ApiAccessLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ApiAccessLogMapper extends BaseMapper<ApiAccessLog> {

    /**
     * 按天聚合指定用户在 [startTime, endTime) 内的调用日志。
     *
     * <p>查询条件同时包含 user_id 与 create_time，可命中 idx_user_time 联合索引；
     * 聚合在数据库侧完成，避免把明细日志读入内存。</p>
     */
    @Select("""
            SELECT CAST(create_time AS DATE) AS stat_date,
                   COUNT(*) AS total_calls,
                   COALESCE(SUM(CASE WHEN http_status >= 200 AND http_status < 300 THEN 1 ELSE 0 END), 0)
                       AS success_calls,
                   COALESCE(SUM(CASE WHEN http_status >= 400 AND http_status < 500 THEN 1 ELSE 0 END), 0)
                       AS client_error_calls,
                   COALESCE(SUM(CASE WHEN http_status >= 500 THEN 1 ELSE 0 END), 0)
                       AS server_error_calls,
                   COALESCE(AVG(latency_ms), 0) AS avg_latency_ms
            FROM api_access_log
            WHERE user_id = #{userId}
              AND create_time >= #{startTime}
              AND create_time < #{endTime}
            GROUP BY CAST(create_time AS DATE)
            ORDER BY stat_date
            """)
    List<LogDailyStatRow> selectDailyStats(@Param("userId") Long userId,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 全站日志汇总：调用量、成功数、总点数、429 限流数、5xx 错误数与平均耗时。
     *
     * <p>只访问 api_access_log，create_time 范围条件配合 idx_time_status_cost
     * 覆盖索引即可完成聚合，无需回表。</p>
     */
    AdminLogSummaryRow selectAdminOverviewSummary(@Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 全站日志按天聚合，用于总览趋势图。
     */
    List<AdminLogDailyTrendRow> selectAdminDailyTrend(@Param("startTime") LocalDateTime startTime,
                                                      @Param("endTime") LocalDateTime endTime);

    /**
     * 接口调用排行：按调用次数倒序，同次数时点数高的靠前。
     */
    List<AdminLogEndpointRankRow> selectAdminEndpointRank(@Param("startTime") LocalDateTime startTime,
                                                          @Param("endTime") LocalDateTime endTime,
                                                          @Param("limit") int limit);

    /**
     * 消耗点数最多的用户排行。
     */
    List<AdminLogUserConsumeRow> selectAdminTopConsumeUsers(@Param("startTime") LocalDateTime startTime,
                                                            @Param("endTime") LocalDateTime endTime,
                                                            @Param("limit") int limit);

    /**
     * 全站日志分页查询，关联 sys_user.username、api_key_info.key_prefix 与
     * api_endpoint_meta.name。时间范围由 Service 强制校验，保证命中 create_time 索引。
     */
    List<AdminLogPageRow> selectAdminLogPage(Page<AdminLogPageRow> page,
                                             @Param("criteria") AdminLogPageReq criteria);
}
