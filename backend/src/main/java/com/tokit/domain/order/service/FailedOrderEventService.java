package com.tokit.domain.order.service;

import com.tokit.domain.order.entity.FailedOrderEvent;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.repository.FailedOrderEventRepository;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.global.event.PostCommitEvents;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import com.tokit.infra.rabbitmq.OrderEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DLQ로 넘어간 주문의 적재와 복구. (#23)
 *
 * <p>복구 경로는 둘이다. 일시 장애였다면 원래 큐로 재발행하고, 처리할 수 없는 주문이면
 * 취소해 홀딩을 해제하고 종결한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FailedOrderEventService {

    private final FailedOrderEventRepository failedOrderEventRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FailedOrderEvent record(OrderEvent event) {
        LocalDateTime now = LocalDateTime.now();
        return failedOrderEventRepository
                .findFirstByOrderIdAndStatusNot(event.orderId(), FailedOrderEvent.Status.RESOLVED)
                .map(existing -> {
                    existing.recordFailureAgain(now);
                    return existing;
                })
                .orElseGet(() -> failedOrderEventRepository.save(FailedOrderEvent.builder()
                        .orderId(event.orderId())
                        .userId(event.userId())
                        .assetSymbol(event.assetSymbol())
                        .orderType(event.orderType())
                        .price(event.price())
                        .quantity(event.quantity())
                        .failedAt(now)
                        .build()));
    }

    public List<FailedOrderEvent> getFailedOrders(FailedOrderEvent.Status status) {
        return status == null
                ? failedOrderEventRepository.findAllByOrderByLastFailedAtDesc()
                : failedOrderEventRepository.findByStatusOrderByLastFailedAtDesc(status);
    }

    /**
     * 주문을 원래 큐로 다시 발행한다. 발행은 커밋 이후에 일어난다.
     *
     * <p>이벤트 본문은 DLQ 메시지가 아니라 현재 주문 행에서 다시 만든다. 존재하지 않는 주문이나
     * 이미 체결·취소된 주문은 재발행해도 매칭되지 않으므로 거절하고 종결로 안내한다.
     * 매칭 쪽도 락을 잡은 뒤 주문 상태를 다시 확인하므로, 재발행이 중복 체결을 만들지 않는다.
     */
    @Transactional
    public FailedOrderEvent replay(Long id) {
        FailedOrderEvent failed = getUnresolved(id);
        Order order = orderRepository.findByIdWithAsset(failed.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!isActive(order)) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_CLOSED);
        }

        failed.markReplayed(LocalDateTime.now());
        eventPublisher.publishEvent(new PostCommitEvents.OrderPlaced(new OrderEvent(
                order.getId(),
                order.getUserId(),
                order.getAssetSymbol(),
                order.getOrderType(),
                order.getPrice(),
                order.getRemainingQuantity())));
        return failed;
    }

    /** 주문을 취소해 홀딩을 해제하고 종결한다. 이미 닫힌 주문이면 해제할 홀딩이 없으므로 종결만 한다. */
    @Transactional
    public FailedOrderEvent cancelAndResolve(Long id) {
        FailedOrderEvent failed = getUnresolved(id);
        Order order = orderRepository.findById(failed.getOrderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (isActive(order)) {
            orderService.cancelOrder(order.getId(), order.getUserId());
            failed.resolve(FailedOrderEvent.Resolution.CANCELED, LocalDateTime.now());
        } else {
            failed.resolve(FailedOrderEvent.Resolution.ALREADY_CLOSED, LocalDateTime.now());
        }
        return failed;
    }

    private FailedOrderEvent getUnresolved(Long id) {
        FailedOrderEvent failed = failedOrderEventRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FAILED_ORDER_EVENT_NOT_FOUND));
        if (failed.isResolved()) {
            throw new BusinessException(ErrorCode.FAILED_ORDER_EVENT_ALREADY_RESOLVED);
        }
        return failed;
    }

    private boolean isActive(Order order) {
        return order.getStatus() == OrderStatus.OPEN || order.getStatus() == OrderStatus.PARTIAL;
    }
}
