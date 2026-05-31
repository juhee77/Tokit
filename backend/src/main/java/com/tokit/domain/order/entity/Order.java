package com.tokit.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "member_id")
    private Long memberId;

    @Column(nullable = false, name = "asset_symbol")
    private String assetSymbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "order_type")
    private OrderType orderType;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, name = "remaining_quantity", precision = 18, scale = 8)
    private BigDecimal remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public Order(Long memberId, String assetSymbol, OrderType orderType, BigDecimal price, BigDecimal quantity) {
        this.memberId = memberId;
        this.assetSymbol = assetSymbol;
        this.orderType = orderType;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = quantity;
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void updateRemainingQuantity(BigDecimal filledQuantity) {
        if (filledQuantity.compareTo(this.remainingQuantity) > 0) {
            throw new IllegalArgumentException("Filled quantity cannot be greater than remaining quantity");
        }
        this.remainingQuantity = this.remainingQuantity.subtract(filledQuantity);
        if (this.remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    public void cancel() {
        if (this.status == OrderStatus.FILLED || this.status == OrderStatus.CANCELED) {
            throw new IllegalStateException("Filled or Canceled orders cannot be canceled");
        }
        this.status = OrderStatus.CANCELED;
    }
}
