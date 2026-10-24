package com.example.api_service.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 统一分页入参基类。
 *
 * <p>所有需要分页的请求 DTO 继承本类。对外的 pageNum / pageSize 受 Bean Validation
 * 约束，同时提供 {@link #getSafePageNum()} 与 {@link #getSafePageSize()} 作为
 * 不依赖校验生效时机的兜底，防止超大 pageSize 打爆内存。</p>
 */
@Schema(description = "分页请求参数")
public class BasePageRequest {

    /** 页码上限，与 pageSize 上限共同约束单次查询的数据量。 */
    private static final int MAX_PAGE_NUM = 100;
    /** 每页条数上限。 */
    private static final int MAX_PAGE_SIZE = 100;
    /** 默认页码。 */
    private static final int DEFAULT_PAGE_NUM = 1;
    /** 默认每页条数。 */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** 一个计费单元包含的记录数。 */
    private static final int BILLING_UNIT_SIZE = 20;

    @Schema(description = "页码，从 1 开始", defaultValue = "1", minimum = "1", maximum = "100")
    @Min(value = 1, message = "pageNum 不能小于 1")
    @Max(value = MAX_PAGE_NUM, message = "pageNum 不能大于 100")
    private Integer pageNum = DEFAULT_PAGE_NUM;

    @Schema(description = "每页条数", defaultValue = "20", minimum = "1", maximum = "100")
    @Min(value = 1, message = "pageSize 不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "pageSize 不能大于 100")
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    @Schema(description = "深度分页游标；普通分页可忽略", nullable = true)
    private String nextCursor;

    public BasePageRequest() {
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    /**
     * 安全的页码：null、0 或负数回退到 1，超过上限则截断到 100。
     */
    public int getSafePageNum() {
        if (pageNum == null || pageNum < 1) {
            return DEFAULT_PAGE_NUM;
        }
        return Math.min(pageNum, MAX_PAGE_NUM);
    }

    /**
     * 安全的每页条数：null、0 或负数回退到 20，超过上限则截断到 100。
     */
    public int getSafePageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * SQL 分页偏移量。
     */
    public long getOffset() {
        return (long) (getSafePageNum() - 1) * getSafePageSize();
    }

    /**
     * 分页计费倍率：每 20 条算 1 个计费单元，不足 20 条按 1 个单元计。
     */
    public int getBillingMultiplier() {
        return (int) Math.ceil(getSafePageSize() / 20.0);
    }
}
