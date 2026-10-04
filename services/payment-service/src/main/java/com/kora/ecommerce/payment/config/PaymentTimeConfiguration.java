package com.kora.ecommerce.payment.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PaymentTimeConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock paymentClock() {
        return Clock.systemUTC();
    }
}
