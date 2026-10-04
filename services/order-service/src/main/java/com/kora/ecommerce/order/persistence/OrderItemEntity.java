package com.kora.ecommerce.order.persistence;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItemEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "item_number", nullable = false)
    private int itemNumber;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "product_sku", nullable = false, updatable = false, length = 128)
    private String productSku;

    @Column(name = "product_name", nullable = false, updatable = false, length = 255)
    private String productName;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal unitPriceAmount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "line_total_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal lineTotalAmount;

    protected OrderItemEntity() {
    }

    private OrderItemEntity(
            UUID id,
            int itemNumber,
            UUID productId,
            String productSku,
            String productName,
            int quantity,
            BigDecimal unitPriceAmount,
            String currency,
            BigDecimal lineTotalAmount) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.itemNumber = itemNumber;
        this.productId = Objects.requireNonNull(productId, "productId is required");
        this.productSku = Objects.requireNonNull(productSku, "productSku is required");
        this.productName = Objects.requireNonNull(productName, "productName is required");
        this.quantity = quantity;
        this.unitPriceAmount = Objects.requireNonNull(unitPriceAmount, "unitPriceAmount is required");
        this.currency = Objects.requireNonNull(currency, "currency is required");
        this.lineTotalAmount = Objects.requireNonNull(lineTotalAmount, "lineTotalAmount is required");
    }

    public static OrderItemEntity snapshot(
            UUID id,
            int itemNumber,
            UUID productId,
            String productSku,
            String productName,
            int quantity,
            BigDecimal unitPriceAmount,
            String currency,
            BigDecimal lineTotalAmount) {
        return new OrderItemEntity(
                id,
                itemNumber,
                productId,
                productSku,
                productName,
                quantity,
                unitPriceAmount,
                currency,
                lineTotalAmount);
    }

    void attachTo(OrderEntity order) {
        this.order = Objects.requireNonNull(order, "order is required");
    }

    public UUID getId() {
        return id;
    }

    public OrderEntity getOrder() {
        return order;
    }

    public int getItemNumber() {
        return itemNumber;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductSku() {
        return productSku;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPriceAmount() {
        return unitPriceAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getLineTotalAmount() {
        return lineTotalAmount;
    }
}
