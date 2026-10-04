package com.kora.ecommerce.payment.provider;

public enum PaymentProviderOutcome {
    SUCCEEDED(false, null),
    DECLINED(false, "PAYMENT_DECLINED"),
    TIMED_OUT(true, "PROVIDER_TIMEOUT"),
    PROVIDER_5XX(true, "PROVIDER_5XX"),
    MALFORMED_RESPONSE(false, "PROVIDER_MALFORMED_RESPONSE");

    private final boolean retryable;
    private final String defaultFailureReason;

    PaymentProviderOutcome(boolean retryable, String defaultFailureReason) {
        this.retryable = retryable;
        this.defaultFailureReason = defaultFailureReason;
    }

    public boolean isRetryable() {
        return retryable;
    }

    String defaultFailureReason() {
        return defaultFailureReason;
    }
}
