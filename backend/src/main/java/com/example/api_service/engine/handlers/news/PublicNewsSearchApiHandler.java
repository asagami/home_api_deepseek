package com.example.api_service.engine.handlers.news;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.example.api_service.engine.AbstractEsApiHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * ES 分页检索标杆示例：按关键词检索新闻标题与正文。
 *
 * <p>分页窗口、trackTotalHits、异常转换与 PageResult 组装均由
 * {@link AbstractEsApiHandler} 统一处理，子类只声明索引、查询与文档映射。</p>
 */
@Component
public class PublicNewsSearchApiHandler
        extends AbstractEsApiHandler<NewsSearchPageReq, NewsEsDoc, NewsOpenVO> {

    private final String indexName;

    public PublicNewsSearchApiHandler(
            ElasticsearchClient elasticsearchClient,
            @Value("${app.search.news-index:news}") String indexName) {
        super(elasticsearchClient);
        this.indexName = indexName;
    }

    @Override
    public String getEndpointPath() {
        return "/api/v1/search/news";
    }

    @Override
    protected String getIndexName() {
        return indexName;
    }

    @Override
    protected Class<NewsEsDoc> getDocumentClass() {
        return NewsEsDoc.class;
    }

    @Override
    protected Query buildQuery(NewsSearchPageReq request) {
        String keyword = request.getKeyword();
        if (!StringUtils.hasText(keyword)) {
            // 无关键词时返回 match_all，由基类按分页参数取最新数据
            return null;
        }
        return Query.of(query -> query.multiMatch(multiMatch -> multiMatch
                .query(keyword.trim())
                .fields("title", "content")));
    }

    @Override
    protected NewsOpenVO convertDocToResp(NewsEsDoc document) {
        return NewsOpenVO.from(document);
    }
}
