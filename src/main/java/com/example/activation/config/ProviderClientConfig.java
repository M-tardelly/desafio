package com.example.activation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Cliente HTTP do provedor externo, com timeouts explícitos e credencial vinda da configuração.
 *
 * @since 2026-10-29
 */
@Configuration(proxyBeanMethods = false)
public class ProviderClientConfig {

    /**
     * Header em que a credencial do provedor é enviada.
     */
    public static final String API_KEY_HEADER = "X-Api-Key";

    /**
     * Cria o {@link RestClient} do provedor com URL base, timeouts de conexão e leitura e, quando
     * configurada, a credencial enviada em {@value #API_KEY_HEADER}.
     *
     * @param builder    builder do Spring Boot (já instrumentado com métricas)
     * @param properties configuração {@code app.provider}
     * @return cliente HTTP pronto para uso
     */
    @Bean
    public RestClient providerRestClient(RestClient.Builder builder, ProviderProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory);
        if (StringUtils.hasText(properties.apiKey())) {
            builder.defaultHeader(API_KEY_HEADER, properties.apiKey());
        }
        return builder.build();
    }
}
