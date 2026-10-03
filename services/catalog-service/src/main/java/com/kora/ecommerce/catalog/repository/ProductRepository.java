package com.kora.ecommerce.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import com.kora.ecommerce.catalog.domain.Product;
import com.kora.ecommerce.catalog.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    @EntityGraph(attributePaths = "category")
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<Product> findByStatusAndCategorySlug(ProductStatus status, String categorySlug, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select p from Product p
            where p.status = :status
              and p.category.active = true
              and (:categorySlug is null or p.category.slug = :categorySlug)
              and (
                    :search is null
                    or lower(p.sku) like lower(concat('%', :search, '%'))
                    or lower(p.name) like lower(concat('%', :search, '%'))
                    or lower(coalesce(p.description, '')) like lower(concat('%', :search, '%'))
                  )
            """)
    Page<Product> findCustomerVisible(
            @Param("status") ProductStatus status,
            @Param("categorySlug") String categorySlug,
            @Param("search") String search,
            Pageable pageable);

    @EntityGraph(attributePaths = {"category", "attributes"})
    @Query("select distinct p from Product p where p.id = :id")
    Optional<Product> findDetailsById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"category", "attributes"})
    @Query("select distinct p from Product p where p.sku = :sku")
    Optional<Product> findDetailsBySku(@Param("sku") String sku);

    @EntityGraph(attributePaths = {"category", "attributes"})
    @Query("""
            select distinct p from Product p
            where p.id = :id
              and p.status = :status
              and p.category.active = true
            """)
    Optional<Product> findCustomerVisibleDetailsById(
            @Param("id") UUID id,
            @Param("status") ProductStatus status);
}
