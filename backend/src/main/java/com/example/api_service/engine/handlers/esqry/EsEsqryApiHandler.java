package com.example.api_service.engine.handlers.esqry;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.example.api_service.engine.AbstractEsApiHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * es用户分页查询
 */
@Component
public class EsEsqryApiHandler extends AbstractEsApiHandler<EsEsqryPageReq, EsEsqryEsDoc, EsEsqryOpenVO> {

    private final String indexName;

    public EsEsqryApiHandler(
            ElasticsearchClient elasticsearchClient,
            @Value("${app.search.esqry-index:sample_index}") String indexName) {
        super(elasticsearchClient);
        this.indexName = indexName;
    }

    @Override
    public String getEndpointPath() {
        return "/api/v1/es/esqry";
    }

    @Override
    protected String getIndexName() {
        return indexName;
    }

    @Override
    protected Class<EsEsqryEsDoc> getDocumentClass() {
        return EsEsqryEsDoc.class;
    }

    @Override
    protected Query buildQuery(EsEsqryPageReq request) {
        // TODO: 按需构造 ES Query 条件；返回 null 时默认 match_all
        return null;
    }

    @Override
    protected EsEsqryOpenVO convertDocToResp(EsEsqryEsDoc doc) {
        return new EsEsqryOpenVO(
                doc.getUserId(),
                doc.getUsername(),
                doc.getSuperuser(),
                doc.getValidUntil()
        );
    }
}
