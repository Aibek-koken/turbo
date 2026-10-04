package com.kora.ecommerce.payment.provider;

public interface PaymentProviderClient {

    PaymentProviderResult authorize(PaymentProviderRequest request);
}
