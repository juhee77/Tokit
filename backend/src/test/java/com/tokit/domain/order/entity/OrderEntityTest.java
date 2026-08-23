package com.tokit.domain.order.entity;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEntityTest {

    @Test
    @DisplayName("Order 엔티티 부분 체결 및 완전 체결 상태 전환: updateRemainingQuantity 호출 시 PARTIAL/FILLED 상태로 전환된다.")
    void updateRemainingQuantity_PartialAndFilledStateTransitions() {
        // Given
        User user = User.builder().name("Orderer").email("orderer@tokit.com").walletAddress("0xORDERER").build();
        Asset asset = Asset.builder().name("Teheran STO").symbol("TEHERAN-STO").build();

        Order order = Order.builder()
                .user(user)
                .asset(asset)
                .type(OrderType.BUY)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("10"))
                .remainQty(new BigDecimal("10"))
                .status(OrderStatus.OPEN)
                .build();

        // When: 4개 부분 체결
        order.updateRemainingQuantity(new BigDecimal("4"));

        // Then
        assertThat(order.getRemainingQuantity()).isEqualTo(new BigDecimal("6"));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIAL);

        // When: 남은 6개 완전 체결
        order.updateRemainingQuantity(new BigDecimal("6"));

        // Then
        assertThat(order.getRemainingQuantity()).isEqualTo(BigDecimal.ZERO);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    @DisplayName("cancel: 주문 취소 시 CANCELED 상태로 전환된다.")
    void cancel_SetsStatusToCanceled() {
        // Given
        Order order = Order.builder()
                .type(OrderType.SELL)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("5"))
                .remainQty(new BigDecimal("5"))
                .status(OrderStatus.OPEN)
                .build();

        // When
        order.cancel();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }
}
