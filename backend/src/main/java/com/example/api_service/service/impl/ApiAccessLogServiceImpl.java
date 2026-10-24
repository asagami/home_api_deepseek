package com.example.api_service.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.example.api_service.mapper.ApiAccessLogMapper;
import com.example.api_service.model.entity.ApiAccessLog;
import com.example.api_service.service.ApiAccessLogService;
import org.springframework.stereotype.Service;

@Service
public class ApiAccessLogServiceImpl extends ServiceImpl<ApiAccessLogMapper, ApiAccessLog>
        implements ApiAccessLogService {
}
