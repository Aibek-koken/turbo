package com.kora.ecommerce.payment.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MockPaymentProviderProperties.class)
class MockPaymentProviderConfiguration {

    @Bean
    @ConditionalOnMissingBean(PaymentProviderClient.class)
    PaymentProviderClient paymentProviderClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            MockPaymentProviderProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());
        return new RestClientMockPaymentProviderClient(
                restClientBuilder.requestFactory(requestFactory),
                objectMapper,
                properties);
    }
}
