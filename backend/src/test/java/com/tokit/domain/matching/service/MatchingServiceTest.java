package com.tokit.domain.matching.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.matching.engine.MatchResult;
import com.tokit.domain.matching.engine.MatchingEngine;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.trade.service.TradeService;
import com.tokit.domain.user.entity.User;
import com.tokit.infra.redis.OrderBookDto;
import com.tokit.infra.redis.RedisOrderBookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @InjectMocks
    private MatchingService matchingService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MatchingEngine matchingEngine;

    @Mock
    private TradeService tradeService;

    @Mock
    private RedisOrderBookRepository redisOrderBookRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private User buyer;
    private User seller;
    private Asset testAsset;
    private Order buyOrder;
    private Order sellOrder;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        buyer = User.builder()
                .name("Taker Buyer")
                .email("buyer.match@tokit.com")
                .walletAddress("0xBUYER_MATCH")
                .build();
        setField(buyer, "id", 1L);

        seller = User.builder()
                .name("Maker Seller")
                .email("seller.match@tokit.com")
                .walletAddress("0xSELLER_MATCH")
                .build();
        setField(seller, "id", 2L);

        testAsset = Asset.builder()
                .name("Pangyo STO")
                .symbol("PANGYO-STO")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        setField(testAsset, "id", 10L);

        buyOrder = Order.builder()
                .user(buyer)
                .asset(testAsset)
                .type(OrderType.BUY)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("10"))
                .status(OrderStatus.OPEN)
                .build();
        setField(buyOrder, "id", 100L);
        setField(buyOrder, "remainQty", new BigDecimal("10"));

        sellOrder = Order.builder()
                .user(seller)
                .asset(testAsset)
                .type(OrderType.SELL)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("10"))
                .status(OrderStatus.OPEN)
                .build();
        setField(sellOrder, "id", 200L);
        setField(sellOrder, "remainQty", new BigDecimal("10"));
    }


    @Test
    @DisplayName("matchOrder: 매수 주문 매칭 성공 시 체결 내역 저장, DB 업데이트, STOMP 호가창 브로드캐스트가 연동 실행된다.")
    void matchOrder_ExecutesTradeAndBroadcastsOrderBook() {
        // Given
        when(orderRepository.findByAsset_SymbolAndStatusIn(eq("PANGYO-STO"), any()))
                .thenReturn(List.of(sellOrder));

        MatchResult.Match match = new MatchResult.Match(sellOrder, new BigDecimal("10000"), new BigDecimal("10"));
        MatchResult matchResult = new MatchResult(buyOrder, List.of(match));

        when(matchingEngine.match(eq(buyOrder), any())).thenReturn(matchResult);


        // When
        matchingService.matchOrder(buyOrder);

        // Then
        verify(tradeService, times(1)).saveTrade(
                eq(100L), eq(200L), eq("PANGYO-STO"), eq(new BigDecimal("10000")), eq(new BigDecimal("10"))
        );
        verify(orderRepository, atLeastOnce()).save(any(Order.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/orderbook/PANGYO-STO"), any(OrderBookDto.class));
    }

    @Test
    @DisplayName("updateAndBroadcastOrderBook: 매수/매도 호가창을 가격순 정렬 집계하여 Redis 및 STOMP 주제로 전송한다.")
    void updateAndBroadcastOrderBook_Success() {
        // Given
        when(orderRepository.findByAsset_SymbolAndStatusIn(eq("PANGYO-STO"), any()))
                .thenReturn(List.of(buyOrder, sellOrder));

        // When
        matchingService.updateAndBroadcastOrderBook("PANGYO-STO");

        // Then
        verify(redisOrderBookRepository, times(1)).saveOrderBook(eq("PANGYO-STO"), any(OrderBookDto.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/orderbook/PANGYO-STO"), any(OrderBookDto.class));
    }
}
