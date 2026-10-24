package com.example.api_service.common;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 统一分页响应体，作为 {@link Result} 的 data 使用。
 *
 * <p>禁止把 MyBatis-Plus 的 IPage 或 Elasticsearch 的原始响应直接暴露给前端，
 * 统一转换为本结构后再返回。</p>
 *
 * @param total      总记录数
 * @param pageNum    当前页码（从 1 开始）
 * @param pageSize   每页条数
 * @param hasMore    是否还有下一页
 * @param nextCursor 深度分页游标；使用 search_after 时由服务端回填
 * @param records    当前页数据
 */
@Schema(name = "PageResult", description = "分页数据")
public record PageResult<T>(
        @Schema(description = "总记录数", example = "100") long total,
        @Schema(description = "当前页码，从 1 开始", example = "1") int pageNum,
        @Schema(description = "每页条数", example = "20") int pageSize,
        @Schema(description = "是否还有下一页", example = "true") boolean hasMore,
        @Schema(description = "深度分页游标，下一页请求时回传", nullable = true) String nextCursor,
        @Schema(description = "当前页数据") List<T> records
) {

    /** 兜底页码，避免上游传入非法值导致分页语义错乱。 */
    private static final int DEFAULT_PAGE_NUM = 1;
    /** 兜底每页条数，与 BasePageRequest 的默认值保持一致。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    public PageResult {
        total = Math.max(total, 0L);
        pageNum = pageNum < DEFAULT_PAGE_NUM ? DEFAULT_PAGE_NUM : pageNum;
        pageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : pageSize;
        records = records == null ? List.of() : List.copyOf(records);
    }

    /**
     * 基于总数推导 hasMore 的常用工厂方法。
     */
    public static <T> PageResult<T> of(List<T> records, long total, int pageNum, int pageSize) {
        boolean hasMore = (long) pageNum * pageSize < total;
        return new PageResult<>(total, pageNum, pageSize, hasMore, null, records);
    }

    /**
     * 完整工厂方法，适用于游标分页等需要显式指定 hasMore / nextCursor 的场景。
     */
    public static <T> PageResult<T> of(long total, int pageNum, int pageSize,
                                       boolean hasMore, String nextCursor, List<T> records) {
        return new PageResult<>(total, pageNum, pageSize, hasMore, nextCursor, records);
    }

    /**
     * 空结果，使用默认分页参数。
     */
    public static <T> PageResult<T> empty() {
        return empty(DEFAULT_PAGE_NUM, DEFAULT_PAGE_SIZE);
    }

    /**
     * 空结果，保留调用方的分页参数，便于前端维持分页状态。
     */
    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return new PageResult<>(0L, pageNum, pageSize, false, null, List.of());
    }
}
