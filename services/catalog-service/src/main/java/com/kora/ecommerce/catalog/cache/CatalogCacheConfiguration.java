package com.kora.ecommerce.catalog.cache;

import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CatalogCacheProperties.class)
class CatalogCacheConfiguration {

    @Bean
    ProductDetailCacheKeyGenerator productDetailCacheKeyGenerator(CatalogCacheProperties properties) {
        return new ProductDetailCacheKeyGenerator(properties);
    }

    @Bean
    ProductDetailCacheJson productDetailCacheJson(ObjectMapper objectMapper) {
        return new ProductDetailCacheJson(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "catalog.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    ProductDetailCache redisProductDetailCache(
            StringRedisTemplate redisTemplate,
            ProductDetailCacheKeyGenerator keyGenerator,
            ProductDetailCacheJson cacheJson,
            CatalogCacheProperties properties) {
        return new RedisProductDetailCache(redisTemplate, keyGenerator, cacheJson, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "catalog.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    ProductDetailCacheLock redissonProductDetailCacheLock(
            RedissonClient redissonClient,
            ProductDetailCacheKeyGenerator keyGenerator,
            CatalogCacheProperties properties) {
        return new RedissonProductDetailCacheLock(redissonClient, keyGenerator, properties);
    }

    @Bean
    @ConditionalOnMissingBean(ProductDetailCache.class)
    ProductDetailCache noopProductDetailCache() {
        return NoopProductDetailCache.INSTANCE;
    }

    @Bean
    @ConditionalOnMissingBean(ProductDetailCacheLock.class)
    ProductDetailCacheLock noopProductDetailCacheLock() {
        return NoopProductDetailCacheLock.INSTANCE;
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "catalog.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    RedissonClient redissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database,
            @Value("${spring.data.redis.timeout:PT2S}") Duration timeout,
            @Value("${spring.data.redis.connect-timeout:PT2S}") Duration connectTimeout) {
        Config config = new Config();
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setTimeout(toMillis(timeout))
                .setConnectTimeout(toMillis(connectTimeout));

        if (StringUtils.hasText(password)) {
            serverConfig.setPassword(password);
        }

        return Redisson.create(config);
    }

    private static int toMillis(Duration duration) {
        long millis = duration.toMillis();
        if (millis > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.toIntExact(millis);
    }
}
