package com.kora.ecommerce.payment.provider;

import java.util.Objects;

public record PaymentProviderResult(
        PaymentProviderOutcome outcome,
        String providerReference,
        String failureReason) {

    public PaymentProviderResult {
        outcome = Objects.requireNonNull(outcome, "outcome is required");
        providerReference = normalizeOptional(providerReference);
        failureReason = normalizeOptional(failureReason);

        if (outcome == PaymentProviderOutcome.SUCCEEDED) {
            if (providerReference == null) {
                throw new IllegalArgumentException("providerReference is required for successful provider results");
            }
            failureReason = null;
        } else if (failureReason == null) {
            failureReason = outcome.defaultFailureReason();
        }
    }

    public static PaymentProviderResult succeeded(String providerReference) {
        return new PaymentProviderResult(PaymentProviderOutcome.SUCCEEDED, providerReference, null);
    }

    public static PaymentProviderResult declined(String providerReference) {
        return new PaymentProviderResult(PaymentProviderOutcome.DECLINED, providerReference, "PAYMENT_DECLINED");
    }

    public static PaymentProviderResult timedOut() {
        return new PaymentProviderResult(PaymentProviderOutcome.TIMED_OUT, null, "PROVIDER_TIMEOUT");
    }

    public static PaymentProviderResult provider5xx(int httpStatus) {
        String reason = httpStatus >= 500 && httpStatus <= 599
                ? "PROVIDER_5XX_HTTP_" + httpStatus
                : "PROVIDER_5XX";
        return new PaymentProviderResult(PaymentProviderOutcome.PROVIDER_5XX, null, reason);
    }

    public static PaymentProviderResult malformedResponse() {
        return new PaymentProviderResult(
                PaymentProviderOutcome.MALFORMED_RESPONSE,
                null,
                "PROVIDER_MALFORMED_RESPONSE");
    }

    public boolean retryable() {
        return outcome.isRetryable();
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
