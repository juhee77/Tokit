package com.tokit.domain.trade.entity;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TradeEntityTest {

    @Test
    @DisplayName("Trade 엔티티 생성 및 어댑터 검증: 체결 단가, 수량, 체결 일시 및 자산 심볼 어댑터가 정상 동작한다.")
    void builderAndAdapters_WorksCorrectly() {
        // Given
        Asset asset = Asset.builder().name("Yeouido STO").symbol("YEOUIDO-STO").build();
        Order buyOrder = Order.builder().asset(asset).type(OrderType.BUY).price(new BigDecimal("20000")).quantity(new BigDecimal("5")).build();
        Order sellOrder = Order.builder().asset(asset).type(OrderType.SELL).price(new BigDecimal("20000")).quantity(new BigDecimal("5")).build();

        LocalDateTime tradedAt = LocalDateTime.now();

        // When
        Trade trade = Trade.builder()
                .buyOrder(buyOrder)
                .sellOrder(sellOrder)
                .asset(asset)
                .price(new BigDecimal("20000"))
                .quantity(new BigDecimal("5"))
                .tradedAt(tradedAt)
                .build();

        // Then
        assertThat(trade.getBuyOrder()).isEqualTo(buyOrder);
        assertThat(trade.getSellOrder()).isEqualTo(sellOrder);
        assertThat(trade.getAsset()).isEqualTo(asset);
        assertThat(trade.getPrice()).isEqualTo(new BigDecimal("20000"));
        assertThat(trade.getQuantity()).isEqualTo(new BigDecimal("5"));
        assertThat(trade.getTradedAt()).isEqualTo(tradedAt);
        assertThat(trade.getCreatedAt()).isEqualTo(tradedAt);
        assertThat(trade.getAssetSymbol()).isEqualTo("YEOUIDO-STO");
    }
}
