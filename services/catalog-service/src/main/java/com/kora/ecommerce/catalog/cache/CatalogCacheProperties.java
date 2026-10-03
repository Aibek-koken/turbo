package com.kora.ecommerce.catalog.cache;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "catalog.cache")
public class CatalogCacheProperties {

    private boolean enabled = true;

    @NotNull
    private Duration productDetailTtl = Duration.ofMinutes(10);

    @NotNull
    private Duration lockWait = Duration.ofMillis(250);

    @NotNull
    private Duration lockLease = Duration.ofSeconds(5);

    @NotBlank
    private String keyPrefix = "catalog:product-detail";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getProductDetailTtl() {
        return productDetailTtl;
    }

    public void setProductDetailTtl(Duration productDetailTtl) {
        this.productDetailTtl = productDetailTtl;
    }

    public Duration getLockWait() {
        return lockWait;
    }

    public void setLockWait(Duration lockWait) {
        this.lockWait = lockWait;
    }

    public Duration getLockLease() {
        return lockLease;
    }

    public void setLockLease(Duration lockLease) {
        this.lockLease = lockLease;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix == null ? null : keyPrefix.trim();
    }

    @AssertTrue(message = "product-detail-ttl must be positive")
    public boolean isProductDetailTtlPositive() {
        return isPositive(productDetailTtl);
    }

    @AssertTrue(message = "lock-wait must be positive and no greater than 5 seconds")
    public boolean isLockWaitBounded() {
        return isPositive(lockWait) && !lockWait.minus(Duration.ofSeconds(5)).isPositive();
    }

    @AssertTrue(message = "lock-lease must be positive and no greater than 30 seconds")
    public boolean isLockLeaseBounded() {
        return isPositive(lockLease) && !lockLease.minus(Duration.ofSeconds(30)).isPositive();
    }

    @AssertTrue(message = "lock-lease must be greater than or equal to lock-wait")
    public boolean isLockLeaseAtLeastLockWait() {
        if (lockLease == null || lockWait == null) {
            return true;
        }
        return !lockLease.minus(lockWait).isNegative();
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
