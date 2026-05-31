package com.tokit.domain.order.controller;

import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.service.OrderService;
import com.tokit.global.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    public record PlaceOrderRequest(
        @NotNull(message = "Member ID is required") Long memberId,
        @NotBlank(message = "Asset Symbol is required") String assetSymbol,
        @NotNull(message = "Order Type (BUY/SELL) is required") OrderType orderType,
        @NotNull(message = "Price is required") @Positive(message = "Price must be positive") BigDecimal price,
        @NotNull(message = "Quantity is required") @Positive(message = "Quantity must be positive") BigDecimal quantity
    ) {}

    public record OrderResponse(
        Long id,
        Long memberId,
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
                order.getMemberId(),
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
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(@RequestBody @Valid PlaceOrderRequest request) {
        Order order = orderService.placeOrder(
            request.memberId(),
            request.assetSymbol(),
            request.orderType(),
            request.price(),
            request.quantity()
        );
        return ResponseEntity.ok(ApiResponse.success(OrderResponse.from(order)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(@PathVariable("id") Long id) {
        orderService.cancelOrder(id);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/member/{memberId}")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getOrdersByMember(@PathVariable("memberId") Long memberId) {
        List<OrderResponse> list = orderService.getOrdersByMember(memberId).stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }
}
