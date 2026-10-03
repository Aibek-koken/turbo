package com.kora.ecommerce.catalog.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CatalogCachePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void providesSafeDefaultsForProductDetailCaching() {
        contextRunner.run(context -> {
            CatalogCacheProperties properties = context.getBean(CatalogCacheProperties.class);

            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.getProductDetailTtl()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.getLockWait()).isEqualTo(Duration.ofMillis(250));
            assertThat(properties.getLockLease()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.getKeyPrefix()).isEqualTo("catalog:product-detail");
        });
    }

    @Test
    void bindsEnvironmentDrivenCacheSettings() {
        contextRunner
                .withPropertyValues(
                        "catalog.cache.enabled=false",
                        "catalog.cache.product-detail-ttl=PT45S",
                        "catalog.cache.lock-wait=PT0.1S",
                        "catalog.cache.lock-lease=PT2S",
                        "catalog.cache.key-prefix=catalog:test-product-detail")
                .run(context -> {
                    CatalogCacheProperties properties = context.getBean(CatalogCacheProperties.class);

                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getProductDetailTtl()).isEqualTo(Duration.ofSeconds(45));
                    assertThat(properties.getLockWait()).isEqualTo(Duration.ofMillis(100));
                    assertThat(properties.getLockLease()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.getKeyPrefix()).isEqualTo("catalog:test-product-detail");
                });
    }

    @Test
    void rejectsUnboundedLockWaitSettings() {
        contextRunner
                .withPropertyValues("catalog.cache.lock-wait=PT6S")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(CatalogCacheProperties.class)
    static class PropertiesConfiguration {
    }
}
