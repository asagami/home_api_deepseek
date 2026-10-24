package com.example.api_service.engine;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.example.api_service.common.BusinessException;
import com.example.api_service.common.ErrorCode;
import com.example.api_service.common.PageResult;
import com.example.api_service.model.dto.BasePageRequest;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Elasticsearch 检索类业务模板。
 *
 * <p>统一处理防深分页、trackTotalHits、查询执行与结果转换，子类只需声明索引、
 * 文档类型、查询条件以及文档到 VO 的映射。</p>
 *
 * @param <Req>  请求 DTO，必须继承 {@link BasePageRequest}
 * @param <Doc>  ES 文档类型
 * @param <Resp> 单条结果 VO
 */
@Slf4j
public abstract class AbstractEsApiHandler<Req extends BasePageRequest, Doc, Resp>
        extends AbstractApiHandler<Req, PageResult<Resp>> {

    /** ES 默认最大结果窗口，from + size 超过该值必须改用 search_after。 */
    private static final int MAX_RESULT_WINDOW = 10_000;

    protected final ElasticsearchClient elasticsearchClient;

    protected AbstractEsApiHandler(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = Objects.requireNonNull(elasticsearchClient,
                "elasticsearchClient 不能为空");
    }

    /**
     * 索引名称。
     */
    protected abstract String getIndexName();

    /**
     * 文档类型，用于反序列化命中的 {@code _source}。
     */
    protected abstract Class<Doc> getDocumentClass();

    /**
     * 构建查询条件；返回 null 表示 match_all。
     */
    protected abstract Query buildQuery(Req request);

    /**
     * 将命中文档转换为对外的单条 VO。
     */
    protected abstract Resp convertDocToResp(Doc document);

    @Override
    protected final PageResult<Resp> doExecute(Req request) throws Exception {
        int pageNum = request.getSafePageNum();
        int pageSize = request.getSafePageSize();
        long fromLong = (long) (pageNum - 1) * pageSize;
        if (fromLong + pageSize > MAX_RESULT_WINDOW) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "分页窗口超过 " + MAX_RESULT_WINDOW + " 条，请使用 nextCursor 进行深度分页");
        }
        int from = (int) fromLong;

        try {
            SearchRequest.Builder builder = new SearchRequest.Builder()
                    .index(getIndexName())
                    .from(from)
                    .size(pageSize)
                    .trackTotalHits(track -> track.enabled(true));
            Query query = buildQuery(request);
            if (query != null) {
                builder.query(query);
            }

            SearchResponse<Doc> response = elasticsearchClient.search(
                    builder.build(), getDocumentClass());
            return toPageResult(response, pageNum, pageSize);
        } catch (IOException | ElasticsearchException ex) {
            log.error("ES 检索失败: index={}, from={}, size={}",
                    getIndexName(), from, pageSize, ex);
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR,
                    "检索服务暂时不可用，请稍后重试", ex);
        }
    }

    private PageResult<Resp> toPageResult(SearchResponse<Doc> response, int pageNum, int pageSize) {
        List<Resp> records = response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .map(this::convertDocToResp)
                .toList();

        long total = response.hits().total() == null
                ? records.size()
                : response.hits().total().value();
        return PageResult.of(records, total, pageNum, pageSize);
    }
}
