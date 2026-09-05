package com.meridianbank.account.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private RestClient buildClient(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(2_000);
        requestFactory.setReadTimeout(3_000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Bean
    public RestClient customerKycServiceRestClient(CustomerKycServiceProperties properties) {
        return buildClient(properties.baseUrl());
    }

    @Bean
    public RestClient ledgerServiceRestClient(LedgerServiceProperties properties) {
        return buildClient(properties.baseUrl());
    }
}
