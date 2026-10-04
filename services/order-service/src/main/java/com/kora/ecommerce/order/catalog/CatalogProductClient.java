package com.kora.ecommerce.order.catalog;

import java.util.UUID;

public interface CatalogProductClient {

    ProductSnapshot resolveProductSnapshot(UUID productId);
}
