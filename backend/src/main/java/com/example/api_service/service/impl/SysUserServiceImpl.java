package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.api_service.mapper.SysUserMapper;
import com.example.api_service.model.entity.SysUser;
import com.example.api_service.service.SysUserService;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Override
    public boolean deductQuota(Long userId, int cost) {
        return baseMapper.deductQuota(userId, cost) > 0;
    }

    @Override
    public boolean addQuota(Long userId, long amount) {
        return baseMapper.addQuota(userId, amount) > 0;
    }

    @Override
    public boolean addBalanceQuota(Long userId, long points) {
        return baseMapper.addBalanceQuota(userId, points) > 0;
    }
}
