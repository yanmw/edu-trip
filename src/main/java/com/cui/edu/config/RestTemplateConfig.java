package com.cui.edu.config;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.client.config.RequestConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 远程调用配置
 *
 * <p>使用 Apache HttpClient 连接池替代 SimpleClientHttpRequestFactory，核心原因：
 * SimpleClientHttpRequestFactory 底层依赖 JDK HttpURLConnection 的 Keep-Alive，
 * 当微信等外部服务端主动关闭连接后，客户端不感知，下次请求复用了已失效的连接（Stale Connection），
 * 导致 RestClientException，表现为"重启后正常、运行一段时间后报错"。
 * 通过 setValidateAfterInactivity 开启空闲连接有效性探测，彻底解决此问题。</p>
 *
 * @author Cuicui
 */
@Configuration
public class RestTemplateConfig {

    /** 连接池最大总连接数 */
    private static final int MAX_TOTAL = 200;
    /** 每个目标 Host 最大连接数 */
    private static final int MAX_PER_ROUTE = 50;
    /** 连接建立超时（ms） */
    private static final int CONNECT_TIMEOUT = 10000;
    /** 从连接池获取连接超时（ms） */
    private static final int CONNECTION_REQUEST_TIMEOUT = 5000;
    /** Socket 读取超时（ms） */
    private static final int SOCKET_TIMEOUT = 30000;
    /**
     * 连接从池中取出前的空闲检测阈值（ms）。
     * 连接空闲超过此时长，取用前会先做 TCP 有效性探测，
     * 避免复用服务端已关闭的 Keep-Alive 连接。
     */
    private static final int VALIDATE_AFTER_INACTIVITY_MS = 5000;

    @Bean
    public RestTemplate restTemplate() {
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(MAX_TOTAL);
        connManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);
        // 关键：连接从池中取出前若已空闲超过 5 秒，则先验证有效性，防止 Stale Connection
        connManager.setValidateAfterInactivity(VALIDATE_AFTER_INACTIVITY_MS);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
                .setSocketTimeout(SOCKET_TIMEOUT)
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(requestConfig)
                // 定期清理过期连接和长期空闲连接（每 30 秒一次，最大空闲 60 秒）
                .evictExpiredConnections()
                .evictIdleConnections(60, TimeUnit.SECONDS)
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        RestTemplate restTemplate = new RestTemplate(factory);
        // 支持中文编码
        restTemplate.getMessageConverters().set(1, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        return restTemplate;
    }

}
