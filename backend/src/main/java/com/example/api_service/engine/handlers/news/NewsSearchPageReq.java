package com.example.api_service.engine.handlers.news;

import com.example.api_service.model.dto.BasePageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 新闻检索分页参数。
 */
@Schema(name = "NewsSearchPageReq", description = "新闻检索分页参数")
public class NewsSearchPageReq extends BasePageRequest {

    @Schema(description = "关键词，同时匹配标题与正文；为空时返回最新新闻", example = "人工智能")
    @Size(max = 100, message = "keyword 长度不能超过 100")
    private String keyword;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}
