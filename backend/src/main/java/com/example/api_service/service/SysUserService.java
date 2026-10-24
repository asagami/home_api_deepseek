package com.example.api_service.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.example.api_service.model.entity.SysUser;

public interface SysUserService extends IService<SysUser> {

    /**
     * 原子扣减用户额度。
     *
     * @return true 扣减成功；false 余额不足或账户不可用
     */
    boolean deductQuota(Long userId, int cost);

    /**
     * 原子追加用户额度。
     *
     * @return true 追加成功；false 用户不存在或已删除
     */
    boolean addQuota(Long userId, long amount);

    /**
     * 充值入账的原子加点。
     *
     * @return true 入账成功；false 用户不存在
     */
    boolean addBalanceQuota(Long userId, long points);
}
