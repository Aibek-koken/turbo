package com.kora.ecommerce.catalog.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

final class RedissonProductDetailCacheLock implements ProductDetailCacheLock {

    private final RedissonClient redissonClient;
    private final ProductDetailCacheKeyGenerator keyGenerator;
    private final CatalogCacheProperties properties;

    RedissonProductDetailCacheLock(
            RedissonClient redissonClient,
            ProductDetailCacheKeyGenerator keyGenerator,
            CatalogCacheProperties properties) {
        this.redissonClient = redissonClient;
        this.keyGenerator = keyGenerator;
        this.properties = properties;
    }

    @Override
    public <T> T withProductDetailLock(UUID productId, Supplier<T> operation) {
        Objects.requireNonNull(productId, "productId must not be null");
        Objects.requireNonNull(operation, "operation must not be null");

        RLock lock = redissonClient.getLock(keyGenerator.productDetailLockKey(productId));
        acquire(lock, productId);

        RuntimeException operationFailure = null;
        try {
            return operation.get();
        } catch (RuntimeException ex) {
            operationFailure = ex;
            throw ex;
        } finally {
            releaseIfOwned(lock, productId, operationFailure);
        }
    }

    private void acquire(RLock lock, UUID productId) {
        try {
            boolean acquired = lock.tryLock(
                    toMillis(properties.getLockWait()),
                    toMillis(properties.getLockLease()),
                    TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ProductDetailCacheLockException(
                        "Timed out waiting for product-detail cache lock for product " + productId + ".");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProductDetailCacheLockException(
                    "Interrupted while waiting for product-detail cache lock for product " + productId + ".",
                    ex);
        } catch (ProductDetailCacheLockException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ProductDetailCacheLockException(
                    "Product-detail cache lock is unavailable for product " + productId + ".",
                    ex);
        }
    }

    private void releaseIfOwned(RLock lock, UUID productId, RuntimeException operationFailure) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (RuntimeException ex) {
            ProductDetailCacheLockException releaseFailure = new ProductDetailCacheLockException(
                    "Failed to release product-detail cache lock for product " + productId + ".",
                    ex);
            if (operationFailure != null) {
                operationFailure.addSuppressed(releaseFailure);
                return;
            }
            throw releaseFailure;
        }
    }

    private static long toMillis(Duration duration) {
        return Math.max(1L, duration.toMillis());
    }
}
