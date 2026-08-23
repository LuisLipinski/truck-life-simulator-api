package com.luislipinski.trucklife.identity.email;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResendEmailProperties.class)
@ConditionalOnProperty(prefix = "identity.email", name = "provider", havingValue = "resend")
public class ResendEmailConfiguration {

    @Bean("resendRestClient")
    RestClient resendRestClient(RestClient.Builder builder, ResendEmailProperties properties) {
        return builder
                .baseUrl("https://api.resend.com")
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + properties.requiredApiKey()
                )
                .build();
    }

    @Bean
    VerificationEmailDeliveryPort resendEmailDeliveryPort(
            @Qualifier("resendRestClient") RestClient restClient,
            ResendEmailProperties properties
    ) {
        return new ResendVerificationEmailAdapter(restClient, properties);
    }
}
