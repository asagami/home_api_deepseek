package com.example.api_service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api_service.model.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 原子扣减额度，仅在余额充足且账户正常时生效。
     *
     * @return 受影响行数，0 表示余额不足或账户不可用
     */
    @Update("""
            UPDATE sys_user
            SET balance_quota = balance_quota - #{cost},
                update_time = NOW()
            WHERE id = #{userId}
              AND balance_quota >= #{cost}
              AND status = 1
              AND is_deleted = 0
            """)
    int deductQuota(@Param("userId") Long userId, @Param("cost") int cost);

    /**
     * 原子追加额度，避免并发充值时的读改写丢失更新。
     *
     * @return 受影响行数，0 表示用户不存在或已删除
     */
    @Update("""
            UPDATE sys_user
            SET balance_quota = balance_quota + #{amount},
                update_time = NOW()
            WHERE id = #{userId}
              AND is_deleted = 0
            """)
    int addQuota(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 充值入账的原子加点，使用数据库自增语义避免并发下的读改写丢失更新。
     *
     * @return 受影响行数，0 表示用户不存在
     */
    @Update("UPDATE sys_user SET balance_quota = balance_quota + #{points} WHERE id = #{userId}")
    int addBalanceQuota(@Param("userId") Long userId, @Param("points") long points);
}
