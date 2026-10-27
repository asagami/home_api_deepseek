package com.example.api_service.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import co.elastic.clients.transport.rest5_client.low_level.Rest5ClientBuilder;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Elasticsearch 9.x 客户端配置。
 *
 * <p>构建客户端不会立即建立连接，因此本地未启动 ES 时应用仍可正常启动；
 * 只有在实际调用检索接口时才会连接失败并返回 504。</p>
 */
@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public ElasticsearchTransport elasticsearchTransport(
            @Value("${app.elasticsearch.url:http://localhost:9200}") String url,
            @Value("${app.elasticsearch.username:}") String username,
            @Value("${app.elasticsearch.password:}") String password) {
        Rest5ClientBuilder builder = Rest5Client.builder(URI.create(url));
        if (StringUtils.hasText(username)) {
            String token = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8));
            builder.setDefaultHeaders(new Header[]{
                    new BasicHeader("Authorization", "Basic " + token)
            });
        }
        return new Rest5ClientTransport(builder.build(), new Jackson3JsonpMapper());
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
