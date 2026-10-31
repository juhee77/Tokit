package com.tokit.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 매칭에 최종 실패해 DLQ로 넘어간 주문 이벤트. (#23)
 *
 * <p>DLQ 메시지는 알림 후 ACK되어 사라지므로, 복구에 필요한 사실을 여기에 남긴다.
 * 알림을 놓치거나 Slack 전송이 실패해도 관리자 API로 목록을 조회해 재발행하거나 종결할 수 있다.
 *
 * <p>같은 주문이 재발행 후 다시 실패하면 새 행을 만들지 않고 종결되지 않은 기존 행을 다시 PENDING으로 돌린다.
 */
@Entity
@Table(
    name = "failed_order_events",
    indexes = {
        @Index(name = "idx_failed_order_events_order", columnList = "order_id"),
        @Index(name = "idx_failed_order_events_status", columnList = "status, last_failed_at")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FailedOrderEvent {

    public enum Status { PENDING, REPLAYED, RESOLVED }

    public enum Resolution {
        /** 관리자가 주문을 취소해 홀딩을 해제했다. */
        CANCELED,
        /** 종결 시점에 주문이 이미 체결·취소되어 해제할 홀딩이 없었다. */
        ALREADY_CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "asset_symbol")
    private String assetSymbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type")
    private OrderType orderType;

    @Column(precision = 20, scale = 4)
    private BigDecimal price;

    @Column(precision = 20, scale = 4)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    private Resolution resolution;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "replay_count", nullable = false)
    private int replayCount;

    @Column(name = "first_failed_at", nullable = false)
    private LocalDateTime firstFailedAt;

    @Column(name = "last_failed_at", nullable = false)
    private LocalDateTime lastFailedAt;

    @Column(name = "last_replayed_at")
    private LocalDateTime lastReplayedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Builder
    public FailedOrderEvent(Long orderId, Long userId, String assetSymbol, OrderType orderType,
                            BigDecimal price, BigDecimal quantity, LocalDateTime failedAt) {
        this.orderId = orderId;
        this.userId = userId;
        this.assetSymbol = assetSymbol;
        this.orderType = orderType;
        this.price = price;
        this.quantity = quantity;
        this.status = Status.PENDING;
        this.failureCount = 1;
        this.replayCount = 0;
        this.firstFailedAt = failedAt;
        this.lastFailedAt = failedAt;
    }

    public void recordFailureAgain(LocalDateTime failedAt) {
        this.status = Status.PENDING;
        this.failureCount++;
        this.lastFailedAt = failedAt;
    }

    public void markReplayed(LocalDateTime replayedAt) {
        this.status = Status.REPLAYED;
        this.replayCount++;
        this.lastReplayedAt = replayedAt;
    }

    public void resolve(Resolution resolution, LocalDateTime resolvedAt) {
        this.status = Status.RESOLVED;
        this.resolution = resolution;
        this.resolvedAt = resolvedAt;
    }

    public boolean isResolved() {
        return status == Status.RESOLVED;
    }
}
