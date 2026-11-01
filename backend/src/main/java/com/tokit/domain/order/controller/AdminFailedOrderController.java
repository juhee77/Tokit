package com.tokit.domain.order.controller;

import com.tokit.domain.order.entity.FailedOrderEvent;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.service.FailedOrderEventService;
import com.tokit.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** DLQ로 넘어간 주문의 조회·재발행·종결. 경로가 /api/admin/** 이므로 ADMIN만 접근한다. (#23) */
@Tag(name = "10. Admin Failed Orders (매칭 실패 주문)", description = "DLQ로 넘어간 주문의 조회, 재발행, 취소 종결 API")
@RestController
@RequestMapping("/api/admin/failed-orders")
@RequiredArgsConstructor
public class AdminFailedOrderController {

    private final FailedOrderEventService failedOrderEventService;

    public record FailedOrderEventResponse(
        Long id,
        Long orderId,
        Long userId,
        String assetSymbol,
        OrderType orderType,
        BigDecimal price,
        BigDecimal quantity,
        FailedOrderEvent.Status status,
        FailedOrderEvent.Resolution resolution,
        int failureCount,
        int replayCount,
        LocalDateTime firstFailedAt,
        LocalDateTime lastFailedAt,
        LocalDateTime lastReplayedAt,
        LocalDateTime resolvedAt
    ) {
        public static FailedOrderEventResponse from(FailedOrderEvent e) {
            return new FailedOrderEventResponse(
                e.getId(), e.getOrderId(), e.getUserId(), e.getAssetSymbol(), e.getOrderType(),
                e.getPrice(), e.getQuantity(), e.getStatus(), e.getResolution(),
                e.getFailureCount(), e.getReplayCount(),
                e.getFirstFailedAt(), e.getLastFailedAt(), e.getLastReplayedAt(), e.getResolvedAt()
            );
        }
    }

    @GetMapping
    @Operation(summary = "매칭 실패 주문 목록", description = "DLQ로 넘어간 주문을 최근 실패 순으로 조회합니다. status로 거를 수 있습니다.")
    public ResponseEntity<ApiResponse<List<FailedOrderEventResponse>>> getFailedOrders(
            @RequestParam(value = "status", required = false) FailedOrderEvent.Status status) {
        List<FailedOrderEventResponse> list = failedOrderEventService.getFailedOrders(status).stream()
                .map(FailedOrderEventResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/{id}/replay")
    @Operation(summary = "재발행", description = "주문을 매칭 큐로 다시 발행합니다. 이미 체결·취소된 주문은 거절됩니다.")
    public ResponseEntity<ApiResponse<FailedOrderEventResponse>> replay(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                FailedOrderEventResponse.from(failedOrderEventService.replay(id))));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "취소 후 종결", description = "주문을 취소해 홀딩된 예치금·자산을 해제하고 실패 기록을 종결합니다.")
    public ResponseEntity<ApiResponse<FailedOrderEventResponse>> cancelAndResolve(@PathVariable("id") Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                FailedOrderEventResponse.from(failedOrderEventService.cancelAndResolve(id))));
    }
}
