package com.kora.ecommerce.catalog.service;

import java.util.UUID;

import com.kora.ecommerce.catalog.cache.ProductDetailCache;
import com.kora.ecommerce.catalog.repository.ProductRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProductDetailCacheInvalidator {

    private static final int CATEGORY_EVICTION_BATCH_SIZE = 100;

    private final ProductDetailCache productDetailCache;
    private final ProductRepository productRepository;
    private final TransactionTemplate readOnlyRequiresNewTransaction;

    ProductDetailCacheInvalidator(
            ProductDetailCache productDetailCache,
            ProductRepository productRepository,
            PlatformTransactionManager transactionManager) {
        this.productDetailCache = productDetailCache;
        this.productRepository = productRepository;
        this.readOnlyRequiresNewTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyRequiresNewTransaction.setReadOnly(true);
        this.readOnlyRequiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void evictProductAfterCommit(UUID productId) {
        runAfterCommit(() -> productDetailCache.evict(productId));
    }

    public void evictCategoryProductsAfterCommit(UUID categoryId) {
        runAfterCommit(() -> evictCategoryProducts(categoryId));
    }

    private void evictCategoryProducts(UUID categoryId) {
        readOnlyRequiresNewTransaction.executeWithoutResult(status -> {
            int page = 0;
            Slice<UUID> productIds;
            do {
                productIds = productRepository.findIdsByCategoryId(
                        categoryId,
                        PageRequest.of(page, CATEGORY_EVICTION_BATCH_SIZE));
                productIds.forEach(productDetailCache::evict);
                page++;
            } while (productIds.hasNext());
        });
    }

    private static void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
