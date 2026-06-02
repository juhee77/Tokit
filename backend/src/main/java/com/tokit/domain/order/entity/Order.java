package com.tokit.domain.order.entity;

import com.tokit.domain.user.entity.User;
import com.tokit.domain.asset.entity.Asset;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type; // BUY(매수) / SELL(매도)

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal price; // 주문 단가

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal quantity; // 최초 주문 수량

    @Column(name = "remain_qty", nullable = false, precision = 20, scale = 4)
    private BigDecimal remainQty; // 미체결 남은 수량

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status; // OPEN/PARTIAL/FILLED/CANCELED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Order(User user, Asset asset, OrderType type, BigDecimal price, BigDecimal quantity, BigDecimal remainQty, OrderStatus status, LocalDateTime createdAt) {
        this.user = user;
        this.asset = asset;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.remainQty = remainQty;
        this.status = status;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public void updateRemainQty(BigDecimal remainQty) {
        this.remainQty = remainQty;
    }

    public void updateStatus(OrderStatus status) {
        this.status = status;
    }

    public void updateRemainingQuantity(BigDecimal matchQty) {
        this.remainQty = this.remainQty.subtract(matchQty);
        if (this.remainQty.compareTo(BigDecimal.ZERO) <= 0) {
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIAL;
        }
    }

    public void cancel() {
        this.status = OrderStatus.CANCELED;
    }

    // --- Backward Compatibility Adapters ---
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    public String getAssetSymbol() {
        return asset != null ? asset.getSymbol() : null;
    }

    public OrderType getOrderType() {
        return type;
    }

    public BigDecimal getRemainingQuantity() {
        return remainQty;
    }
}
