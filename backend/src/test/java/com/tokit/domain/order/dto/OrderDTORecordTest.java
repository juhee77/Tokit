package com.tokit.domain.order.dto;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.order.controller.OrderController.*;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDTORecordTest {

    @Test
    @DisplayName("PlaceOrderRequest DTO 레코드 검증: 매수/매도 주문 파라미터가 정확히 저장된다.")
    void placeOrderRequest_InstantiationAndAccessors() {
        // Given & When
        PlaceOrderRequest request = new PlaceOrderRequest(
                1L, "GNPM", OrderType.BUY, new BigDecimal("150000"), new BigDecimal("10")
        );

        // Then
        assertThat(request.userId()).isEqualTo(1L);
        assertThat(request.assetSymbol()).isEqualTo("GNPM");
        assertThat(request.orderType()).isEqualTo(OrderType.BUY);
        assertThat(request.price()).isEqualTo(new BigDecimal("150000"));
        assertThat(request.quantity()).isEqualTo(new BigDecimal("10"));
    }

    @Test
    @DisplayName("OrderResponse 정적 팩토리 검증: Order 엔티티로부터 매칭 DTO로 데이터가 정확히 매핑된다.")
    void orderResponse_FromFactoryMethod() {
        // Given
        User user = User.builder().build();
        setField(user, "id", 10L);

        Asset asset = Asset.builder().symbol("GNPM").build();
        setField(asset, "id", 100L);

        LocalDateTime now = LocalDateTime.now();
        Order order = Order.builder()
                .user(user)
                .asset(asset)
                .type(OrderType.SELL)
                .price(new BigDecimal("151000"))
                .quantity(new BigDecimal("20"))
                .remainQty(new BigDecimal("5"))
                .status(OrderStatus.PARTIAL)
                .createdAt(now)
                .build();
        setField(order, "id", 1000L);

        // When
        OrderResponse response = OrderResponse.from(order);

        // Then
        assertThat(response.id()).isEqualTo(1000L);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.assetSymbol()).isEqualTo("GNPM");
        assertThat(response.orderType()).isEqualTo(OrderType.SELL);
        assertThat(response.price()).isEqualTo(new BigDecimal("151000"));
        assertThat(response.quantity()).isEqualTo(new BigDecimal("20"));
        assertThat(response.remainingQuantity()).isEqualTo(new BigDecimal("5"));
        assertThat(response.status()).isEqualTo(OrderStatus.PARTIAL);
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
