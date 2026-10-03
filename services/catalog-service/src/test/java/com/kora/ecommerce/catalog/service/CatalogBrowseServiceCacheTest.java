package com.kora.ecommerce.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.CategorySummaryResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductAttributeResponse;
import com.kora.ecommerce.catalog.api.customer.CatalogBrowseDtos.ProductDetailResponse;
import com.kora.ecommerce.catalog.cache.ProductDetailCache;
import com.kora.ecommerce.catalog.cache.ProductDetailCacheLock;
import com.kora.ecommerce.catalog.domain.Category;
import com.kora.ecommerce.catalog.domain.Product;
import com.kora.ecommerce.catalog.domain.ProductAttribute;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import com.kora.ecommerce.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CatalogBrowseServiceCacheTest {

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CATEGORY_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductDetailCache productDetailCache;

    private CatalogBrowseService catalogBrowseService;

    @BeforeEach
    void setUp() {
        catalogBrowseService = new CatalogBrowseService(
                productRepository,
                productDetailCache,
                new ImmediateProductDetailCacheLock());
    }

    @Test
    void getProductReturnsCachedActiveDetailWithoutRepositoryLookup() {
        ProductDetailResponse cached = detailResponse(PRODUCT_ID, ProductStatus.ACTIVE, "COF-CACHED");
        when(productDetailCache.get(PRODUCT_ID)).thenReturn(Optional.of(cached));

        ProductDetailResponse response = catalogBrowseService.getProduct(PRODUCT_ID);

        assertThat(response).isEqualTo(cached);
        verifyNoInteractions(productRepository);
        verify(productDetailCache, never()).put(PRODUCT_ID, cached);
    }

    @Test
    void getProductLoadsAuthoritativeProductAndPopulatesCacheOnMiss() {
        Product product = activeProduct(PRODUCT_ID, "COF-MISS");
        when(productDetailCache.get(PRODUCT_ID)).thenReturn(Optional.empty(), Optional.empty());
        when(productRepository.findCustomerVisibleDetailsById(PRODUCT_ID, ProductStatus.ACTIVE))
                .thenReturn(Optional.of(product));

        ProductDetailResponse response = catalogBrowseService.getProduct(PRODUCT_ID);

        assertThat(response.id()).isEqualTo(PRODUCT_ID);
        assertThat(response.sku()).isEqualTo("COF-MISS");
        assertThat(response.attributes()).containsExactly(new ProductAttributeResponse("origin", "colombia"));
        verify(productRepository).findCustomerVisibleDetailsById(PRODUCT_ID, ProductStatus.ACTIVE);
        verify(productDetailCache).put(PRODUCT_ID, response);
    }

    @Test
    void getProductEvictsInactiveCachedDetailBeforeReloading() {
        ProductDetailResponse inactive = detailResponse(PRODUCT_ID, ProductStatus.INACTIVE, "COF-STALE");
        Product product = activeProduct(PRODUCT_ID, "COF-FRESH");
        when(productDetailCache.get(PRODUCT_ID)).thenReturn(Optional.of(inactive), Optional.empty());
        when(productRepository.findCustomerVisibleDetailsById(PRODUCT_ID, ProductStatus.ACTIVE))
                .thenReturn(Optional.of(product));

        ProductDetailResponse response = catalogBrowseService.getProduct(PRODUCT_ID);

        assertThat(response.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(response.sku()).isEqualTo("COF-FRESH");
        verify(productDetailCache).evict(PRODUCT_ID);
        verify(productDetailCache).put(PRODUCT_ID, response);
    }

    @Test
    void getProductSerializesConcurrentMissesAndRechecksCacheBeforeLoading() throws Exception {
        Product product = activeProduct(PRODUCT_ID, "COF-LOCKED");
        ProductDetailCache cache = new InMemoryProductDetailCache();
        ContendingProductDetailCacheLock lock = new ContendingProductDetailCacheLock();
        CatalogBrowseService lockedService = new CatalogBrowseService(productRepository, cache, lock);
        CountDownLatch repositoryEntered = new CountDownLatch(1);
        CountDownLatch releaseRepository = new CountDownLatch(1);
        AtomicInteger repositoryLoads = new AtomicInteger();
        when(productRepository.findCustomerVisibleDetailsById(PRODUCT_ID, ProductStatus.ACTIVE))
                .thenAnswer(invocation -> {
                    repositoryLoads.incrementAndGet();
                    repositoryEntered.countDown();
                    assertThat(releaseRepository.await(2, TimeUnit.SECONDS)).isTrue();
                    return Optional.of(product);
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ProductDetailResponse> first = executor.submit(() -> lockedService.getProduct(PRODUCT_ID));
            assertThat(repositoryEntered.await(2, TimeUnit.SECONDS)).isTrue();

            Future<ProductDetailResponse> second = executor.submit(() -> lockedService.getProduct(PRODUCT_ID));
            assertThat(lock.awaitBlockedAttempt(2, TimeUnit.SECONDS)).isTrue();

            releaseRepository.countDown();

            ProductDetailResponse firstResponse = first.get(2, TimeUnit.SECONDS);
            ProductDetailResponse secondResponse = second.get(2, TimeUnit.SECONDS);

            assertThat(firstResponse.sku()).isEqualTo("COF-LOCKED");
            assertThat(secondResponse).isEqualTo(firstResponse);
            assertThat(repositoryLoads).hasValue(1);
            verify(productRepository).findCustomerVisibleDetailsById(PRODUCT_ID, ProductStatus.ACTIVE);
        } finally {
            releaseRepository.countDown();
            executor.shutdownNow();
        }
    }

    private static Product activeProduct(UUID productId, String sku) {
        Category category = new Category("Coffee", "coffee");
        ReflectionTestUtils.setField(category, "id", CATEGORY_ID);

        Product product = new Product(category, sku, "Detail Blend", new BigDecimal("16.25"), "USD");
        ReflectionTestUtils.setField(product, "id", productId);
        product.setDescription("Full detail response");
        product.setStatus(ProductStatus.ACTIVE);
        product.addAttribute(new ProductAttribute("origin", "colombia"));
        ProductAttribute inactiveAttribute = new ProductAttribute("internal", "hidden");
        inactiveAttribute.setActive(false);
        product.addAttribute(inactiveAttribute);
        return product;
    }

    private static ProductDetailResponse detailResponse(UUID productId, ProductStatus status, String sku) {
        return new ProductDetailResponse(
                productId,
                sku,
                "Detail Blend",
                "Full detail response",
                new BigDecimal("16.25"),
                "USD",
                status,
                new CategorySummaryResponse(CATEGORY_ID, "Coffee", "coffee"),
                List.of(new ProductAttributeResponse("origin", "colombia")));
    }

    private static final class ImmediateProductDetailCacheLock implements ProductDetailCacheLock {

        @Override
        public <T> T withProductDetailLock(UUID productId, Supplier<T> operation) {
            return operation.get();
        }
    }

    private static final class ContendingProductDetailCacheLock implements ProductDetailCacheLock {

        private final ReentrantLock lock = new ReentrantLock();
        private final CountDownLatch blockedAttempt = new CountDownLatch(1);

        @Override
        public <T> T withProductDetailLock(UUID productId, Supplier<T> operation) {
            if (!lock.tryLock()) {
                blockedAttempt.countDown();
                lock.lock();
            }
            try {
                return operation.get();
            } finally {
                lock.unlock();
            }
        }

        boolean awaitBlockedAttempt(long timeout, TimeUnit unit) throws InterruptedException {
            return blockedAttempt.await(timeout, unit);
        }
    }

    private static final class InMemoryProductDetailCache implements ProductDetailCache {

        private final ConcurrentMap<UUID, ProductDetailResponse> entries = new ConcurrentHashMap<>();

        @Override
        public Optional<ProductDetailResponse> get(UUID productId) {
            return Optional.ofNullable(entries.get(productId));
        }

        @Override
        public void put(UUID productId, ProductDetailResponse response) {
            entries.put(productId, response);
        }

        @Override
        public void evict(UUID productId) {
            entries.remove(productId);
        }
    }
}
