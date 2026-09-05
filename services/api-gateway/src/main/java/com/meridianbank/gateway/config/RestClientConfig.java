package com.meridianbank.gateway.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Bounded connect/read timeouts on every proxied call — matching this project's established
 * "no unbounded outbound call" convention (see e.g. every service's Kafka producer/Hikari
 * timeout settings) — so a stuck downstream service degrades to a fast 502/504 at the edge
 * instead of hanging the gateway's request threads indefinitely.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient upstreamRestClient(UpstreamProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        return RestClient.builder().requestFactory(factory).build();
    }
}
