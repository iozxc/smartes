package cn.omisheep.smartes;

import cn.omisheep.smartes.core.ElasticSearchService;
import cn.omisheep.smartes.core.ElasticSearchServiceImpl;
import cn.omisheep.smartes.core.SmartESProperties;
import cn.omisheep.smartes.core.aop.ESAop;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zhouxinchen[1269670415@qq.com]
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties({SmartESProperties.class})
public class SmartESAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RestHighLevelClient restHighLevelClient(SmartESProperties properties) {
        SmartESProperties.ClientConfig config              = properties.getClient();
        HttpHost                       httpHost            = new HttpHost(config.getHostname(), config.getPort(), config.getScheme());
        final CredentialsProvider      credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(config.getUsername(), config.getPassword()));

        RestClientBuilder builder = RestClient.builder(httpHost)
                .setRequestConfigCallback(requestConfigBuilder -> {
                    requestConfigBuilder.setConnectTimeout(-1);
                    requestConfigBuilder.setSocketTimeout(-1);
                    requestConfigBuilder.setConnectionRequestTimeout(-1);
                    return requestConfigBuilder;
                }).setHttpClientConfigCallback(httpClientBuilder -> {
                    httpClientBuilder.disableAuthCaching();
                    return httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                });

        return new RestHighLevelClient(builder);
    }


    @Bean
    @ConditionalOnMissingBean
    public ElasticSearchService elasticSearchService(@Qualifier("restHighLevelClient") RestHighLevelClient client,
                                                     SmartESProperties esConfig) {
        return new ElasticSearchServiceImpl(client, esConfig);
    }

    @Bean
    @ConditionalOnProperty(name = "smartes.orm", havingValue = "MYBATIS", matchIfMissing = true)
    @ConditionalOnMissingBean
    public ESAop esAop(ElasticSearchService elasticSearchService) {
        return new ESAop(elasticSearchService);
    }
}
