package com.kora.ecommerce.order.application.payment;

import java.util.UUID;

public record PaymentResultData(UUID orderId, UUID paymentId) {
}
