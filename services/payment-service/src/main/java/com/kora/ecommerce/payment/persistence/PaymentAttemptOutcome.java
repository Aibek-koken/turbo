package com.kora.ecommerce.payment.persistence;

public enum PaymentAttemptOutcome {
    REQUESTED,
    SUCCEEDED,
    DECLINED,
    TIMED_OUT,
    PROVIDER_5XX,
    MALFORMED_RESPONSE
}
