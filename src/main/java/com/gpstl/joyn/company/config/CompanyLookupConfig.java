package com.gpstl.joyn.company.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CompanyLookupConfig {

    @Bean
    public RestClient companyLookupRestClient(
            @Value("${joyn.siret.base-url:https://recherche-entreprises.api.gouv.fr}") String baseUrl,
            @Value("${joyn.siret.connect-timeout:2s}") Duration connectTimeout,
            @Value("${joyn.siret.read-timeout:5s}") Duration readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}
