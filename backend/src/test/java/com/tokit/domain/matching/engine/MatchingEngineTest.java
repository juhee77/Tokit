package com.tokit.domain.matching.engine;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {

    private MatchingEngine matchingEngine;
    private User buyer;
    private User seller;
    private Asset testAsset;

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine();

        buyer = User.builder()
                .name("Buyer User")
                .email("buyer@tokit.com")
                .walletAddress("0xBUYER_ADDRESS_11")
                .build();
        ReflectionTestUtils.setField(buyer, "id", 1L);

        seller = User.builder()
                .name("Seller User")
                .email("seller@tokit.com")
                .walletAddress("0xSELLER_ADDRESS_22")
                .build();
        ReflectionTestUtils.setField(seller, "id", 2L);

        testAsset = Asset.builder()
                .name("Pangyo IT Building STO")
                .symbol("PANGYO-STO")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        ReflectionTestUtils.setField(testAsset, "id", 5L);
    }

    @Test
    @DisplayName("BUY Taker 매칭: 가장 저렴한 매도 호가(Price ASC) 및 선등록 순서(Time ASC)에 따라 체결된다.")
    void match_BuyOrder_PriceTimePriority() {
        // Given: 기존 매도 호가(SELL Maker) 3건
        Order cheapSeller = Order.builder()
                .user(seller)
                .asset(testAsset)
                .type(OrderType.SELL)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("10"))
                .remainQty(new BigDecimal("10"))
                .status(OrderStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(cheapSeller, "id", 101L);
        ReflectionTestUtils.setField(cheapSeller, "createdAt", LocalDateTime.now().minusMinutes(10));

        Order expensiveSeller = Order.builder()
                .user(seller)
                .asset(testAsset)
                .type(OrderType.SELL)
                .price(new BigDecimal("12000"))
                .quantity(new BigDecimal("10"))
                .remainQty(new BigDecimal("10"))
                .status(OrderStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(expensiveSeller, "id", 102L);
        ReflectionTestUtils.setField(expensiveSeller, "createdAt", LocalDateTime.now().minusMinutes(20));

        Order cheaperLaterSeller = Order.builder()
                .user(seller)
                .asset(testAsset)
                .type(OrderType.SELL)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("10"))
                .remainQty(new BigDecimal("10"))
                .status(OrderStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(cheaperLaterSeller, "id", 103L);
        ReflectionTestUtils.setField(cheaperLaterSeller, "createdAt", LocalDateTime.now().minusMinutes(5));

        // 신규 매수 주문 (11,000원에 15주 구매)
        Order incomingBuyOrder = Order.builder()
                .user(buyer)
                .asset(testAsset)
                .type(OrderType.BUY)
                .price(new BigDecimal("11000"))
                .quantity(new BigDecimal("15"))
                .remainQty(new BigDecimal("15"))
                .status(OrderStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(incomingBuyOrder, "id", 200L);

        // When
        MatchResult result = matchingEngine.match(incomingBuyOrder, List.of(expensiveSeller, cheaperLaterSeller, cheapSeller));

        // Then
        assertThat(result.matches()).hasSize(2);
        // 1차 체결: 가장 먼저 등록된 10,000원 매도자 (10주 전량 체결)
        assertThat(result.matches().get(0).makerOrder().getId()).isEqualTo(101L);
        assertThat(result.matches().get(0).matchPrice()).isEqualTo(new BigDecimal("10000"));
        assertThat(result.matches().get(0).matchQuantity()).isEqualTo(new BigDecimal("10"));

        // 2차 체결: 동일 10,000원이지만 나중에 등록된 매도자 (5주 부분 체결)
        assertThat(result.matches().get(1).makerOrder().getId()).isEqualTo(103L);
        assertThat(result.matches().get(1).matchQuantity()).isEqualTo(new BigDecimal("5"));

        // 신규 매수 주문 잔량 0주
        assertThat(incomingBuyOrder.getRemainingQuantity().stripTrailingZeros()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("SELL Taker 매칭: 가장 높은 매수 호가(Price DESC) 순으로 체결된다.")
    void match_SellOrder_PricePriority() {
        // Given: 기존 매수 호가(BUY Maker) 2건
        Order highBidder = Order.builder()
                .user(buyer)
                .asset(testAsset)
                .type(OrderType.BUY)
                .price(new BigDecimal("15000"))
                .quantity(new BigDecimal("10"))
                .remainQty(new BigDecimal("10"))
                .status(OrderStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(highBidder, "id", 301L);
        ReflectionTestUtils.setField(highBidder, "createdAt", LocalDateTime.now().minusMinutes(5));

        Order lowBidder = Order.builder()
                .user(buyer)
                .asset(testAsset)
                .type(OrderType.BUY)
                .price(new BigDecimal("13000"))
                .quantity(new BigDecimal("10"))
                .remainQty(new BigDecimal("10"))
                .status(OrderStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(lowBidder, "id", 302L);
        ReflectionTestUtils.setField(lowBidder, "createdAt", LocalDateTime.now().minusMinutes(10));

        // 신규 매도 주문 (14,000원에 5주 판매)
        Order incomingSellOrder = Order.builder()
                .user(seller)
                .asset(testAsset)
                .type(OrderType.SELL)
                .price(new BigDecimal("14000"))
                .quantity(new BigDecimal("5"))
                .remainQty(new BigDecimal("5"))
                .status(OrderStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(incomingSellOrder, "id", 400L);

        // When
        MatchResult result = matchingEngine.match(incomingSellOrder, List.of(lowBidder, highBidder));

        // Then
        assertThat(result.matches()).hasSize(1);
        // 15,000원 고가 매수자 체결
        assertThat(result.matches().get(0).makerOrder().getId()).isEqualTo(301L);
        assertThat(result.matches().get(0).matchPrice()).isEqualTo(new BigDecimal("15000"));
        assertThat(result.matches().get(0).matchQuantity()).isEqualTo(new BigDecimal("5"));
    }

    @Test
    @DisplayName("호가 불일치 매칭 불가: 매수 가격(9,000원) < 매도 호가(10,000원)인 경우 매칭 결과 0건을 반환한다.")
    void match_NoMatchWhenPricesDoNotOverlap() {
        // Given
        Order makerSell = Order.builder()
                .user(seller)
                .asset(testAsset)
                .type(OrderType.SELL)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("10"))
                .remainQty(new BigDecimal("10"))
                .status(OrderStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(makerSell, "id", 501L);

        Order takerBuy = Order.builder()
                .user(buyer)
                .asset(testAsset)
                .type(OrderType.BUY)
                .price(new BigDecimal("9000"))
                .quantity(new BigDecimal("5"))
                .remainQty(new BigDecimal("5"))
                .status(OrderStatus.OPEN)
                .build();

        // When
        MatchResult result = matchingEngine.match(takerBuy, List.of(makerSell));

        // Then
        assertThat(result.matches()).isEmpty();
        assertThat(takerBuy.getRemainingQuantity().stripTrailingZeros()).isEqualTo(new BigDecimal("5"));
    }
}
