package com.tokit.domain.trade.entity;

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

    @Column(nullable = false, name = "buy_order_id")
    private Long buyOrderId;

    @Column(nullable = false, name = "sell_order_id")
    private Long sellOrderId;

    @Column(nullable = false, name = "asset_symbol")
    private String assetSymbol;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false, name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public Trade(Long buyOrderId, Long sellOrderId, String assetSymbol, BigDecimal price, BigDecimal quantity) {
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.assetSymbol = assetSymbol;
        this.price = price;
        this.quantity = quantity;
        this.createdAt = LocalDateTime.now();
    }
}
