package com.kora.ecommerce.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kora.ecommerce.catalog.domain.ProductAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, UUID> {

    List<ProductAttribute> findByProductIdOrderByAttributeKey(UUID productId);

    Optional<ProductAttribute> findByIdAndProductId(UUID id, UUID productId);

    Optional<ProductAttribute> findByProductIdAndAttributeKey(UUID productId, String attributeKey);
}
