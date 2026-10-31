package com.example.api_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api_service.model.entity.AdminRechargeMonthlyUsage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminRechargeMonthlyUsageMapper extends BaseMapper<AdminRechargeMonthlyUsage> {

    /**
     * 确保本月计数行存在；已存在时不做任何修改。
     * MySQL 与 H2(MySQL 模式) 均支持 ON DUPLICATE KEY UPDATE。
     */
    @Insert("""
            INSERT INTO admin_recharge_monthly_usage
                (id, operator_id, user_id, month_key, granted_points)
            VALUES
                (#{id}, #{operatorId}, #{userId}, #{monthKey}, 0)
            ON DUPLICATE KEY UPDATE granted_points = granted_points
            """)
    int insertIfAbsent(@Param("id") Long id,
                       @Param("operatorId") Long operatorId,
                       @Param("userId") Long userId,
                       @Param("monthKey") String monthKey);

    /**
     * 锁定本月计数行，同一 (管理员, 目标用户, 月份) 的充值串行校验。
     */
    @Select("""
            SELECT granted_points
            FROM admin_recharge_monthly_usage
            WHERE operator_id = #{operatorId}
              AND user_id = #{userId}
              AND month_key = #{monthKey}
            FOR UPDATE
            """)
    Long selectGrantedForUpdate(@Param("operatorId") Long operatorId,
                                @Param("userId") Long userId,
                                @Param("monthKey") String monthKey);

    @Update("""
            UPDATE admin_recharge_monthly_usage
            SET granted_points = granted_points + #{points},
                update_time = CURRENT_TIMESTAMP
            WHERE operator_id = #{operatorId}
              AND user_id = #{userId}
              AND month_key = #{monthKey}
            """)
    int addGranted(@Param("operatorId") Long operatorId,
                   @Param("userId") Long userId,
                   @Param("monthKey") String monthKey,
                   @Param("points") long points);
}
