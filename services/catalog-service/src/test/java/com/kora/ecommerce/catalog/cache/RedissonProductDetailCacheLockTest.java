package com.kora.ecommerce.catalog.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class RedissonProductDetailCacheLockTest {

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Test
    void executesOperationWithConfiguredWaitAndLeaseAndUnlocksOwnedLock() throws InterruptedException {
        RedissonProductDetailCacheLock cacheLock = cacheLock(Duration.ofMillis(125), Duration.ofSeconds(3));
        when(redissonClient.getLock("catalog:test-product-detail:lock:v1:" + PRODUCT_ID)).thenReturn(lock);
        when(lock.tryLock(125, 3000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = cacheLock.withProductDetailLock(PRODUCT_ID, () -> "loaded");

        assertThat(result).isEqualTo("loaded");
        verify(lock).unlock();
    }

    @Test
    void failsWithoutRunningOperationWhenLockWaitTimesOut() throws InterruptedException {
        RedissonProductDetailCacheLock cacheLock = cacheLock(Duration.ofMillis(125), Duration.ofSeconds(3));
        when(redissonClient.getLock("catalog:test-product-detail:lock:v1:" + PRODUCT_ID)).thenReturn(lock);
        when(lock.tryLock(125, 3000, TimeUnit.MILLISECONDS)).thenReturn(false);

        assertThatThrownBy(() -> cacheLock.withProductDetailLock(PRODUCT_ID, () -> "loaded"))
                .isInstanceOf(ProductDetailCacheLockException.class)
                .hasMessageContaining("Timed out waiting");
        verify(lock, never()).unlock();
    }

    @Test
    void restoresInterruptFlagWhenLockWaitIsInterrupted() throws InterruptedException {
        RedissonProductDetailCacheLock cacheLock = cacheLock(Duration.ofMillis(125), Duration.ofSeconds(3));
        when(redissonClient.getLock("catalog:test-product-detail:lock:v1:" + PRODUCT_ID)).thenReturn(lock);
        when(lock.tryLock(125, 3000, TimeUnit.MILLISECONDS))
                .thenThrow(new InterruptedException("stop waiting"));

        assertThatThrownBy(() -> cacheLock.withProductDetailLock(PRODUCT_ID, () -> "loaded"))
                .isInstanceOf(ProductDetailCacheLockException.class)
                .hasMessageContaining("Interrupted while waiting");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    @Test
    void skipsUnlockWhenCurrentThreadDoesNotOwnLock() throws InterruptedException {
        RedissonProductDetailCacheLock cacheLock = cacheLock(Duration.ofMillis(125), Duration.ofSeconds(3));
        when(redissonClient.getLock("catalog:test-product-detail:lock:v1:" + PRODUCT_ID)).thenReturn(lock);
        when(lock.tryLock(125, 3000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        String result = cacheLock.withProductDetailLock(PRODUCT_ID, () -> "loaded");

        assertThat(result).isEqualTo("loaded");
        verify(lock, never()).unlock();
    }

    private RedissonProductDetailCacheLock cacheLock(Duration wait, Duration lease) {
        CatalogCacheProperties properties = new CatalogCacheProperties();
        properties.setKeyPrefix("catalog:test-product-detail");
        properties.setLockWait(wait);
        properties.setLockLease(lease);
        return new RedissonProductDetailCacheLock(
                redissonClient,
                new ProductDetailCacheKeyGenerator(properties),
                properties);
    }
}
