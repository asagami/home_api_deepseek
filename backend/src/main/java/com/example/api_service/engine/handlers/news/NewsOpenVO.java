package com.example.api_service.engine.handlers.news;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 对外开放的新闻检索结果。
 */
@Schema(name = "NewsOpenVO", description = "新闻检索结果")
public record NewsOpenVO(
        @Schema(description = "文档 ID") String id,
        @Schema(description = "标题") String title,
        @Schema(description = "正文") String content,
        @Schema(description = "来源") String source,
        @Schema(description = "原文链接") String url,
        @Schema(description = "发布时间", example = "2026-10-24T09:00:00") String publishTime
) {

    public static NewsOpenVO from(NewsEsDoc document) {
        return new NewsOpenVO(
                document.getId(),
                document.getTitle(),
                document.getContent(),
                document.getSource(),
                document.getUrl(),
                document.getPublishTime()
        );
    }
}
