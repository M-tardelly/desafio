package com.example.activation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Cliente HTTP do provedor externo, com timeouts explícitos e credencial vinda da configuração.
 */
@Configuration(proxyBeanMethods = false)
public class ProviderClientConfig {

    public static final String API_KEY_HEADER = "X-Api-Key";

    @Bean
    public RestClient providerRestClient(RestClient.Builder builder, ProviderProperties properties) {
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory);
        if (StringUtils.hasText(properties.apiKey())) {
            builder.defaultHeader(API_KEY_HEADER, properties.apiKey());
        }
        return builder.build();
    }
}
