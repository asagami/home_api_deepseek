package com.example.api_service.engine;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api_service.common.PageResult;
import com.example.api_service.model.dto.BasePageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Function;

/**
 * SQL 查询类业务模板。
 *
 * <p>类级只读事务保证子类不会误写数据；分页参数统一经过 {@link BasePageRequest}
 * 的安全裁剪，避免超大 pageSize 打爆内存。</p>
 *
 * @param <Req>  请求 DTO
 * @param <Resp> 响应 VO
 */
@Transactional(readOnly = true)
public abstract class AbstractSqlApiHandler<Req, Resp> extends AbstractApiHandler<Req, Resp> {

    /**
     * 基于请求中的安全分页参数构建 MyBatis-Plus 分页对象。
     */
    protected <E> Page<E> buildMpPage(BasePageRequest request) {
        return new Page<>(request.getSafePageNum(), request.getSafePageSize());
    }

    /**
     * 将 MyBatis-Plus 的 {@link IPage} 转换为对外的 {@link PageResult}。
     *
     * <p>严禁把 IPage 直接返回给前端，必须经此方法完成实体到 VO 的映射。</p>
     */
    protected <E, R> PageResult<R> toPageResult(IPage<E> page, Function<E, R> converter) {
        List<R> records = page.getRecords().stream()
                .map(converter)
                .toList();
        return PageResult.of(records, page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize());
    }
}
