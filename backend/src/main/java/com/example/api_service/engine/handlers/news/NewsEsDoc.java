package com.example.api_service.engine.handlers.news;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Elasticsearch 中的新闻文档。
 *
 * <p>{@code _source} 字段需与下列属性同名；{@code publish_time} 通过别名兼容下划线命名。
 * 时间使用字符串承载，避免 ES 客户端与业务侧时区/序列化配置耦合。</p>
 */
@Data
@Schema(name = "NewsEsDoc", description = "ES 新闻文档")
public class NewsEsDoc {

    @Schema(description = "文档 ID")
    private String id;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "正文")
    private String content;

    @Schema(description = "来源")
    private String source;

    @Schema(description = "原文链接")
    private String url;

    @Schema(description = "发布时间", example = "2026-10-24T09:00:00")
    @JsonAlias("publish_time")
    private String publishTime;
}
