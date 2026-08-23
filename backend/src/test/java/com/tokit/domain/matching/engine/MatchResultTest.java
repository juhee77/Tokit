package com.tokit.domain.matching.engine;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchResultTest {

    @Test
    @DisplayName("MatchResult 레코드 데이터 검증: Taker 주문과 체결된 Maker 주문 리스트의 수량 및 체결가가 정상 유지된다.")
    void recordInstantiation_HoldsMatchesCorrectly() {
        // Given
        User taker = User.builder().name("Taker").email("taker@tokit.com").walletAddress("0xTAKER").build();
        User maker = User.builder().name("Maker").email("maker@tokit.com").walletAddress("0xMAKER").build();

        Asset asset = Asset.builder().name("Centum STO").symbol("CENTUM-STO").build();

        Order takerOrder = Order.builder().user(taker).asset(asset).type(OrderType.BUY).price(new BigDecimal("10000")).quantity(new BigDecimal("10")).build();
        Order makerOrder = Order.builder().user(maker).asset(asset).type(OrderType.SELL).price(new BigDecimal("10000")).quantity(new BigDecimal("10")).build();

        MatchResult.Match match = new MatchResult.Match(makerOrder, new BigDecimal("10000"), new BigDecimal("10"));

        // When
        MatchResult result = new MatchResult(takerOrder, List.of(match));

        // Then
        assertThat(result.incomingOrder()).isEqualTo(takerOrder);
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).makerOrder()).isEqualTo(makerOrder);
        assertThat(result.matches().get(0).matchPrice()).isEqualTo(new BigDecimal("10000"));
        assertThat(result.matches().get(0).matchQuantity()).isEqualTo(new BigDecimal("10"));
    }
}
