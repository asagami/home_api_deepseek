package com.example.api_service.service;

import com.example.api_service.common.PageResult;
import com.example.api_service.model.dto.log.admin.AdminLogOverviewReq;
import com.example.api_service.model.dto.log.admin.AdminLogPageReq;
import com.example.api_service.model.vo.log.admin.AdminLogOverviewVO;
import com.example.api_service.model.vo.log.admin.AdminLogVO;

/**
 * 管理员全站调用日志查询。
 */
public interface AdminLogQueryService {

    /**
     * 全站调用总览：汇总指标、按天趋势、接口排行与点数消耗 Top 5 用户。
     *
     * @param request 时间范围，为空时默认最近 7 天
     */
    AdminLogOverviewVO overview(AdminLogOverviewReq request);

    /**
     * 全站调用日志分页查询，时间范围必填。
     */
    PageResult<AdminLogVO> page(AdminLogPageReq request);
}
