package com.tokit.domain.trade.dto;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.trade.controller.TradeController.TradeResponse;
import com.tokit.domain.trade.entity.Trade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TradeResponseTest {

    @Test
    @DisplayName("TradeResponse 정적 팩토리 메서드 검증: Trade 엔티티로부터 DTO로 매칭 및 체결 내역 정보가 정확히 매핑된다.")
    void tradeResponse_FromFactoryMethod() {
        // Given
        Order buyOrder = Order.builder().build();
        setField(buyOrder, "id", 100L);

        Order sellOrder = Order.builder().build();
        setField(sellOrder, "id", 200L);

        Asset asset = Asset.builder().symbol("GNPM").build();

        LocalDateTime now = LocalDateTime.now();
        Trade trade = Trade.builder()
                .buyOrder(buyOrder)
                .sellOrder(sellOrder)
                .asset(asset)
                .price(new BigDecimal("150000"))
                .quantity(new BigDecimal("10"))
                .tradedAt(now)
                .build();
        setField(trade, "id", 1L);

        // When
        TradeResponse response = TradeResponse.from(trade);

        // Then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.buyOrderId()).isEqualTo(100L);
        assertThat(response.sellOrderId()).isEqualTo(200L);
        assertThat(response.assetSymbol()).isEqualTo("GNPM");
        assertThat(response.price()).isEqualTo(new BigDecimal("150000"));
        assertThat(response.quantity()).isEqualTo(new BigDecimal("10"));
        assertThat(response.createdAt()).isEqualTo(now);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
