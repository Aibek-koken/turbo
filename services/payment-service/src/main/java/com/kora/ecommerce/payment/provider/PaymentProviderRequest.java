package com.kora.ecommerce.payment.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

public record PaymentProviderRequest(
        UUID providerRequestId,
        UUID paymentId,
        UUID orderId,
        String customerId,
        BigDecimal amount,
        String currency) {

    public PaymentProviderRequest {
        Objects.requireNonNull(providerRequestId, "providerRequestId is required");
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(orderId, "orderId is required");
        customerId = requireText(customerId, "customerId");
        amount = requirePositiveAmount(amount);
        currency = requireCurrency(currency);
    }

    private static BigDecimal requirePositiveAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount is required");
        BigDecimal scaled = amount.setScale(4, RoundingMode.UNNECESSARY);
        if (scaled.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return scaled;
    }

    private static String requireCurrency(String currency) {
        String normalized = requireText(currency, "currency");
        if (!normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an uppercase ISO-4217 code");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
