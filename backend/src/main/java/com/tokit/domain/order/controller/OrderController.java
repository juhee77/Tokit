package com.tokit.domain.order.controller;

import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.service.OrderService;
import com.tokit.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.tokit.global.annotation.Idempotent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "04. Order (주문)", description = "토큰증권(STO) 매수/매도 주문 접수 및 취소 API")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    public record PlaceOrderRequest(
        @NotNull(message = "User ID is required") Long userId,
        @NotBlank(message = "Asset Symbol is required") String assetSymbol,
        @NotNull(message = "Order Type (BUY/SELL) is required") OrderType orderType,
        @NotNull(message = "Price is required") @Positive(message = "Price must be positive") BigDecimal price,
        @NotNull(message = "Quantity is required") @Positive(message = "Quantity must be positive") BigDecimal quantity
    ) {}

    public record OrderResponse(
        Long id,
        Long userId,
        String assetSymbol,
        OrderType orderType,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal remainingQuantity,
        OrderStatus status,
        LocalDateTime createdAt
    ) {
        public static OrderResponse from(Order order) {
            return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getAssetSymbol(),
                order.getOrderType(),
                order.getPrice(),
                order.getQuantity(),
                order.getRemainingQuantity(),
                order.getStatus(),
                order.getCreatedAt()
            );
        }
    }

    @PostMapping
    @Idempotent
    @Operation(summary = "매수/매도 주문 접수", description = "주식 토큰의 매수 혹은 매도 주문을 접수합니다. (Idempotency 보장)")
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid PlaceOrderRequest request) {
        Order order = orderService.placeOrder(
            request.userId(),
            request.assetSymbol(),
            request.orderType(),
            request.price(),
            request.quantity()
        );
        return ResponseEntity.ok(ApiResponse.success(OrderResponse.from(order)));
    }

    @PostMapping("/{id}/cancel")
    @Idempotent
    @Operation(summary = "주문 취소", description = "접수되어 대기 중인 주문을 취소합니다. (Idempotency 보장)")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @PathVariable("id") Long id) {
        orderService.cancelOrder(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "사용자 주문 내역 조회", description = "특정 사용자의 전체 주문 내역 리스트를 조회합니다.")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByUser(@PathVariable("userId") Long userId) {
        List<OrderResponse> list = orderService.getOrdersByUser(userId).stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }
}
