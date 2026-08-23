package com.tokit.domain.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.service.OrderService;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private OrderController orderController;

    @Mock
    private OrderService orderService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private User testUser;
    private Asset testAsset;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testUser = User.builder()
                .name("Order User")
                .email("order.user@tokit.com")
                .walletAddress("0xORDER_USER_ADDRESS_01")
                .build();
        ReflectionTestUtils.setField(testUser, "id", 1L);

        testAsset = Asset.builder()
                .name("Mapo STO")
                .symbol("MAPO-STO")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        ReflectionTestUtils.setField(testAsset, "id", 10L);

        testOrder = Order.builder()
                .user(testUser)
                .asset(testAsset)
                .type(OrderType.BUY)
                .price(new BigDecimal("10000"))
                .quantity(new BigDecimal("5"))
                .remainQty(new BigDecimal("5"))
                .status(OrderStatus.OPEN)
                .build();
        ReflectionTestUtils.setField(testOrder, "id", 100L);
    }

    @Test
    @DisplayName("POST /api/orders: X-Idempotency-Key와 함께 매수 주문 제출 시 성공 및 HTTP 200을 반환한다.")
    void placeOrder_Success() throws Exception {
        // Given
        OrderController.PlaceOrderRequest request = new OrderController.PlaceOrderRequest(
                1L, "MAPO-STO", OrderType.BUY, new BigDecimal("10000"), new BigDecimal("5")
        );

        when(orderService.placeOrder(any(), any(), any(), any(), any()))
                .thenReturn(testOrder);

        // When & Then
        mockMvc.perform(post("/api/orders")
                        .header("X-Idempotency-Key", "uuid-v4-idempotency-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.assetSymbol").value("MAPO-STO"));
    }

    @Test
    @DisplayName("POST /api/orders: 음수 단가/수량 제출 시 Valid 검증에 걸려 HTTP 400을 반환한다.")
    void placeOrder_ValidationError_Returns400() throws Exception {
        // Given: 음수 가격(-10,000원)
        OrderController.PlaceOrderRequest request = new OrderController.PlaceOrderRequest(
                1L, "MAPO-STO", OrderType.BUY, new BigDecimal("-10000"), new BigDecimal("5")
        );

        // When & Then
        mockMvc.perform(post("/api/orders")
                        .header("X-Idempotency-Key", "uuid-v4-idempotency-key-02")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/orders/{id}/cancel: 소유자 본인이 X-Idempotency-Key와 함께 주문 취소 시 성공 및 HTTP 200을 반환한다.")
    void cancelOrder_Success() throws Exception {
        // Given
        doNothing().when(orderService).cancelOrder(eq(100L), eq(1L));

        // When & Then
        mockMvc.perform(post("/api/orders/100/cancel")
                        .header("X-Idempotency-Key", "uuid-v4-idempotency-key-03")
                        .param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("GET /api/orders/user/{userId}: 특정 사용자의 전체 주문 내역 조회가 성공 및 HTTP 200을 반환한다.")
    void getOrdersByUser_Success() throws Exception {
        // Given
        when(orderService.getOrdersByUser(1L)).thenReturn(List.of(testOrder));

        // When & Then
        mockMvc.perform(get("/api/orders/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].assetSymbol").value("MAPO-STO"));
    }
}
