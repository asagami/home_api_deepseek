package com.example.api_service.engine.handlers.detailqry;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.example.api_service.engine.AbstractEsApiHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 流水查询
 */
@Component
public class EsDetailqryApiHandler extends AbstractEsApiHandler<EsDetailqryPageReq, EsDetailqryEsDoc, EsDetailqryOpenVO> {

    private final String indexName;

    public EsDetailqryApiHandler(
            ElasticsearchClient elasticsearchClient,
            @Value("${app.search.detailqry-index:detailqry}") String indexName) {
        super(elasticsearchClient);
        this.indexName = indexName;
    }

    @Override
    public String getEndpointPath() {
        return "/api/v1/es/detailqry";
    }

    @Override
    protected String getIndexName() {
        return indexName;
    }

    @Override
    protected Class<EsDetailqryEsDoc> getDocumentClass() {
        return EsDetailqryEsDoc.class;
    }

    @Override
    protected Query buildQuery(EsDetailqryPageReq request) {
        return null;
    }

    @Override
    protected EsDetailqryOpenVO convertDocToResp(EsDetailqryEsDoc doc) {
        return new EsDetailqryOpenVO(
                doc.getOthactno(),
                doc.getAmount(),
                doc.getBalance()
        );
    }
}
