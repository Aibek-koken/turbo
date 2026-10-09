package com.kora.ecommerce.catalog.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.kora.ecommerce.catalog.api.admin.CatalogAdminDtos.UpdateProductRequest;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.CategorySummaryResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductAttributeResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;
import com.kora.ecommerce.catalog.domain.Category;
import com.kora.ecommerce.catalog.domain.Product;
import com.kora.ecommerce.catalog.domain.ProductAttribute;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import com.kora.ecommerce.catalog.repository.CategoryRepository;
import com.kora.ecommerce.catalog.repository.ProductAttributeRepository;
import com.kora.ecommerce.catalog.repository.ProductRepository;
import com.kora.ecommerce.catalog.service.CatalogAdminService;
import com.kora.ecommerce.catalog.service.CatalogBrowseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockReset;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:catalog_redis_it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.batch.job.enabled=false",
                "catalog.cache.enabled=true",
                "catalog.cache.product-detail-ttl=PT2M",
                "catalog.cache.lock-wait=PT2S",
                "catalog.cache.lock-lease=PT8S",
                "catalog.cache.key-prefix=catalog:redis-it-product-detail",
                "management.health.redis.enabled=true"
        })
@Testcontainers
class CatalogRedisCacheIT {

    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");
    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(REDIS_PORT);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.data.redis.database", () -> "0");
        registry.add("spring.data.redis.timeout", () -> "PT2S");
        registry.add("spring.data.redis.connect-timeout", () -> "PT2S");
    }

    @Autowired
    private ProductDetailCache productDetailCache;

    @Autowired
    private ProductDetailCacheKeyGenerator keyGenerator;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private CatalogBrowseService catalogBrowseService;

    @Autowired
    private CatalogAdminService catalogAdminService;

    @Autowired
    private CategoryRepository categoryRepository;

    @SpyBean(reset = MockReset.BEFORE)
    private ProductRepository productRepository;

    @Autowired
    private ProductAttributeRepository productAttributeRepository;

    @BeforeEach
    void resetCatalogAndRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        productAttributeRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        clearInvocations(productRepository);
    }

    @Test
    void redisCacheRoundTripsProductDetailJsonWithTtl() {
        UUID productId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ProductDetailResponse response = new ProductDetailResponse(
                productId,
                "REDIS-SER-001",
                "Serialized Espresso",
                "Stored as plain JSON",
                new BigDecimal("18.50"),
                "USD",
                ProductStatus.ACTIVE,
                new CategorySummaryResponse(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "Coffee",
                        "coffee"),
                List.of(new ProductAttributeResponse("origin", "colombia")));

        productDetailCache.put(productId, response);

        String redisKey = keyGenerator.productDetailKey(productId);
        String rawPayload = redisTemplate.opsForValue().get(redisKey);

        assertThat(rawPayload)
                .contains("\"sku\":\"REDIS-SER-001\"")
                .contains("\"attributes\"")
                .doesNotContain("@class", "com.kora.ecommerce.catalog");
        assertThat(redisTemplate.getExpire(redisKey, TimeUnit.SECONDS)).isPositive();
        assertThat(productDetailCache.get(productId)).contains(response);
    }

    @Test
    void cacheMissPopulatesRedisCachedReadBypassesDatabaseAndAdminUpdateInvalidates() {
        Product product = saveActiveProduct("REDIS-CACHE-001", "Initial Cached Blend");
        UUID productId = product.getId();

        ProductDetailResponse firstRead = catalogBrowseService.getProduct(productId);

        String redisKey = keyGenerator.productDetailKey(productId);
        assertThat(redisTemplate.opsForValue().get(redisKey))
                .contains("\"sku\":\"REDIS-CACHE-001\"")
                .contains("\"name\":\"Initial Cached Blend\"");

        Product databaseOnlyUpdate = productRepository.findDetailsById(productId).orElseThrow();
        databaseOnlyUpdate.setName("Database Name Without Invalidation");
        productRepository.saveAndFlush(databaseOnlyUpdate);

        ProductDetailResponse cachedRead = catalogBrowseService.getProduct(productId);

        assertThat(cachedRead).isEqualTo(firstRead);
        assertThat(cachedRead.name()).isEqualTo("Initial Cached Blend");

        catalogAdminService.updateProduct(productId, new UpdateProductRequest(
                product.getCategory().getId(),
                "REDIS-CACHE-001",
                "Admin Invalidated Blend",
                "Fresh detail after admin update",
                new BigDecimal("21.00"),
                "USD",
                ProductStatus.ACTIVE));

        assertThat(redisTemplate.hasKey(redisKey)).isFalse();

        ProductDetailResponse afterInvalidation = catalogBrowseService.getProduct(productId);

        assertThat(afterInvalidation.name()).isEqualTo("Admin Invalidated Blend");
        assertThat(redisTemplate.opsForValue().get(redisKey))
                .contains("\"name\":\"Admin Invalidated Blend\"");
    }

    @Test
    void concurrentCacheMissesUseRedissonLockAndPerformOneDatabaseLoad() throws Exception {
        Product product = saveActiveProduct("REDIS-LOCK-001", "Locked Cache Blend");
        UUID productId = product.getId();
        CountDownLatch firstRepositoryLoadEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstRepositoryLoad = new CountDownLatch(1);
        CountDownLatch secondRequestStarted = new CountDownLatch(1);
        AtomicInteger repositoryLoads = new AtomicInteger();

        doAnswer(invocation -> {
            repositoryLoads.incrementAndGet();
            firstRepositoryLoadEntered.countDown();
            assertThat(releaseFirstRepositoryLoad.await(3, TimeUnit.SECONDS)).isTrue();
            return productRepository.findDetailsById(productId)
                    .filter(found -> found.getStatus() == ProductStatus.ACTIVE)
                    .filter(found -> found.getCategory().isActive());
        }).when(productRepository).findCustomerVisibleDetailsById(eq(productId), eq(ProductStatus.ACTIVE));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ProductDetailResponse> first = executor.submit(() -> catalogBrowseService.getProduct(productId));
            assertThat(firstRepositoryLoadEntered.await(3, TimeUnit.SECONDS)).isTrue();
            assertProductLockIsHeld(productId);

            Future<ProductDetailResponse> second = executor.submit(() -> {
                secondRequestStarted.countDown();
                return catalogBrowseService.getProduct(productId);
            });
            assertThat(secondRequestStarted.await(3, TimeUnit.SECONDS)).isTrue();

            releaseFirstRepositoryLoad.countDown();

            ProductDetailResponse firstResponse = first.get(3, TimeUnit.SECONDS);
            ProductDetailResponse secondResponse = second.get(3, TimeUnit.SECONDS);

            assertThat(firstResponse.sku()).isEqualTo("REDIS-LOCK-001");
            assertThat(secondResponse).isEqualTo(firstResponse);
            assertThat(repositoryLoads).hasValue(1);
            assertThat(productDetailCache.get(productId)).contains(firstResponse);
        } finally {
            releaseFirstRepositoryLoad.countDown();
            executor.shutdownNow();
        }
    }

    private void assertProductLockIsHeld(UUID productId) throws InterruptedException {
        RLock probeLock = redissonClient.getLock(keyGenerator.productDetailLockKey(productId));
        boolean acquired = probeLock.tryLock(100, 500, TimeUnit.MILLISECONDS);
        try {
            assertThat(acquired).isFalse();
        } finally {
            if (acquired && probeLock.isHeldByCurrentThread()) {
                probeLock.unlock();
            }
        }
    }

    private Product saveActiveProduct(String sku, String name) {
        Category category = categoryRepository.saveAndFlush(new Category("Coffee", "coffee"));
        Product product = new Product(category, sku, name, new BigDecimal("19.50"), "USD");
        product.setDescription("Detail description for " + sku);
        product.setStatus(ProductStatus.ACTIVE);
        product.addAttribute(new ProductAttribute("origin", "colombia"));
        ProductAttribute hiddenAttribute = new ProductAttribute("internal", "supplier-only");
        hiddenAttribute.setActive(false);
        product.addAttribute(hiddenAttribute);
        return productRepository.saveAndFlush(product);
    }

    @TestConfiguration
    static class JwtDecoderConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                if (!"catalog-admin-token".equals(token)) {
                    throw new JwtException("Test decoder rejects unknown bearer tokens.");
                }
                Instant issuedAt = Instant.now();
                return new Jwt(
                        token,
                        issuedAt,
                        issuedAt.plusSeconds(300),
                        Map.of("alg", "none"),
                        Map.of(
                                "sub", "test-user",
                                "iss", "http://localhost:8085/realms/ecommerce",
                                "realm_access", Map.of("roles", List.of("CATALOG_ADMIN"))));
            };
        }
    }
}
