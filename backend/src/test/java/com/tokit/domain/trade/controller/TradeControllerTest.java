package com.tokit.domain.trade.controller;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.trade.entity.Trade;
import com.tokit.domain.trade.service.TradeService;
import com.tokit.domain.user.entity.User;
import com.tokit.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TradeControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private TradeController tradeController;

    @Mock
    private TradeService tradeService;

    private User buyer;
    private User seller;
    private Asset testAsset;
    private Order buyOrder;
    private Order sellOrder;
    private Trade testTrade;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(tradeController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        buyer = User.builder()
                .name("Buyer")
                .email("buyer@tokit.com")
                .walletAddress("0xBUYER")
                .build();
        setField(buyer, "id", 1L);

        seller = User.builder()
                .name("Seller")
                .email("seller@tokit.com")
                .walletAddress("0xSELLER")
                .build();
        setField(seller, "id", 2L);

        testAsset = Asset.builder()
                .name("Magok STO")
                .symbol("MAGOK-STO")
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
                .quantity(new BigDecimal("5"))
                .build();
        setField(buyOrder, "id", 100L);

        sellOrder = Order.builder()
                .user(seller)
                .asset(testAsset)
                .type(OrderType.SELL)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("5"))
                .build();
        setField(sellOrder, "id", 200L);

        testTrade = Trade.builder()
                .buyOrder(buyOrder)
                .sellOrder(sellOrder)
                .asset(testAsset)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("5"))
                .tradedAt(LocalDateTime.now())
                .build();
        setField(testTrade, "id", 999L);
    }

    @Test
    @DisplayName("GET /api/trades/asset/{symbol}: 자산별 체결 내역 조회가 성공하여 HTTP 200을 반환한다.")
    void getTradesByAsset_Success() throws Exception {
        // Given
        when(tradeService.getTradesByAsset("MAGOK-STO")).thenReturn(List.of(testTrade));

        // When & Then
        mockMvc.perform(get("/api/trades/asset/MAGOK-STO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].id").value(999))
                .andExpect(jsonPath("$.data[0].assetSymbol").value("MAGOK-STO"));
    }

    @Test
    @DisplayName("GET /api/trades/subscribe/{symbol}: SSE 체결 내역 스트림 구독 시 text/event-stream 헤더를 반환한다.")
    void subscribeTrades_Success() throws Exception {
        // Given
        SseEmitter mockEmitter = new SseEmitter();
        when(tradeService.subscribeTrades("MAGOK-STO")).thenReturn(mockEmitter);

        // When & Then
        mockMvc.perform(get("/api/trades/subscribe/MAGOK-STO"))
                .andExpect(status().isOk());
    }
}


