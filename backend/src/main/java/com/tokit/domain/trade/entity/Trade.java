package com.tokit.domain.trade.entity;

import com.tokit.domain.order.entity.Order;
import com.tokit.domain.asset.entity.Asset;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trades")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buy_order_id", nullable = false)
    private Order buyOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sell_order_id", nullable = false)
    private Order sellOrder;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal price; // 최종 체결 단가

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal quantity; // 체결된 수량

    @Column(name = "traded_at", nullable = false)
    private LocalDateTime tradedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset; // 매칭 조회 편의를 위한 자산 관계 추가

    @Builder
    public Trade(Order buyOrder, Order sellOrder, BigDecimal price, BigDecimal quantity, LocalDateTime tradedAt, Asset asset) {
        this.buyOrder = buyOrder;
        this.sellOrder = sellOrder;
        this.price = price;
        this.quantity = quantity;
        this.tradedAt = tradedAt != null ? tradedAt : LocalDateTime.now();
        this.asset = asset;
    }

    // --- Backward Compatibility Adapters ---
    public String getAssetSymbol() {
        return asset != null ? asset.getSymbol() : null;
    }

    public Long getBuyOrderId() {
        return buyOrder != null ? buyOrder.getId() : null;
    }

    public Long getSellOrderId() {
        return sellOrder != null ? sellOrder.getId() : null;
    }

    public LocalDateTime getCreatedAt() {
        return tradedAt;
    }
}
