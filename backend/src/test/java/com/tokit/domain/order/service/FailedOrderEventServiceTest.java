package com.tokit.domain.order.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.order.entity.FailedOrderEvent;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.FailedOrderEventRepository;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.global.event.PostCommitEvents;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import com.tokit.infra.rabbitmq.OrderEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FailedOrderEventServiceTest {

    @InjectMocks
    private FailedOrderEventService service;

    @Mock
    private FailedOrderEventRepository failedOrderEventRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Order order;
    private OrderEvent event;

    @BeforeEach
    void setUp() {
        User user = User.builder().name("u").email("u@tokit.com").walletAddress("0xU").build();
        ReflectionTestUtils.setField(user, "id", 7L);
        Asset asset = Asset.builder().name("a").symbol("PANGYO-STO")
                .totalSupply(BigDecimal.TEN).issuePrice(BigDecimal.ONE).status("거래중").build();
        order = Order.builder().user(user).asset(asset).type(OrderType.BUY)
                .price(new BigDecimal("10000")).quantity(new BigDecimal("5")).remainQty(new BigDecimal("3"))
                .status(OrderStatus.PARTIAL).build();
        ReflectionTestUtils.setField(order, "id", 100L);
        event = new OrderEvent(100L, 7L, "PANGYO-STO", OrderType.BUY, new BigDecimal("10000"), new BigDecimal("5"));
    }

    private FailedOrderEvent failedRecord() {
        FailedOrderEvent failed = FailedOrderEvent.builder()
                .orderId(100L).userId(7L).assetSymbol("PANGYO-STO").orderType(OrderType.BUY)
                .price(new BigDecimal("10000")).quantity(new BigDecimal("5"))
                .failedAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(failed, "id", 1L);
        return failed;
    }

    @Test
    @DisplayName("record: 처음 실패한 주문은 PENDING 기록으로 적재된다.")
    void record_NewFailure_SavesPending() {
        when(failedOrderEventRepository.findFirstByOrderIdAndStatusNot(100L, FailedOrderEvent.Status.RESOLVED))
                .thenReturn(Optional.empty());
        when(failedOrderEventRepository.save(any(FailedOrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        FailedOrderEvent saved = service.record(event);

        assertThat(saved.getOrderId()).isEqualTo(100L);
        assertThat(saved.getStatus()).isEqualTo(FailedOrderEvent.Status.PENDING);
        assertThat(saved.getFailureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("record: 재발행 후 다시 실패하면 새 행 대신 기존 기록을 PENDING으로 되돌리고 실패 횟수를 올린다.")
    void record_RepeatedFailure_UpdatesExisting() {
        FailedOrderEvent existing = failedRecord();
        existing.markReplayed(LocalDateTime.now());
        when(failedOrderEventRepository.findFirstByOrderIdAndStatusNot(100L, FailedOrderEvent.Status.RESOLVED))
                .thenReturn(Optional.of(existing));

        FailedOrderEvent result = service.record(event);

        assertThat(result).isSameAs(existing);
        assertThat(result.getStatus()).isEqualTo(FailedOrderEvent.Status.PENDING);
        assertThat(result.getFailureCount()).isEqualTo(2);
        assertThat(result.getReplayCount()).isEqualTo(1);
        verify(failedOrderEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("replay: 활성 주문은 현재 잔량으로 커밋 후 재발행되고 재발행 횟수가 기록된다.")
    void replay_ActiveOrder_PublishesAfterCommit() {
        FailedOrderEvent failed = failedRecord();
        when(failedOrderEventRepository.findById(1L)).thenReturn(Optional.of(failed));
        when(orderRepository.findByIdWithAsset(100L)).thenReturn(Optional.of(order));

        service.replay(1L);

        assertThat(failed.getStatus()).isEqualTo(FailedOrderEvent.Status.REPLAYED);
        assertThat(failed.getReplayCount()).isEqualTo(1);
        verify(eventPublisher).publishEvent(new PostCommitEvents.OrderPlaced(new OrderEvent(
                100L, 7L, "PANGYO-STO", OrderType.BUY, new BigDecimal("10000"), new BigDecimal("3"))));
    }

    @Test
    @DisplayName("replay: 이미 체결·취소된 주문은 재발행하지 않는다.")
    void replay_ClosedOrder_Rejected() {
        ReflectionTestUtils.setField(order, "status", OrderStatus.FILLED);
        FailedOrderEvent failed = failedRecord();
        when(failedOrderEventRepository.findById(1L)).thenReturn(Optional.of(failed));
        when(orderRepository.findByIdWithAsset(100L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.replay(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ALREADY_CLOSED);
        assertThat(failed.getStatus()).isEqualTo(FailedOrderEvent.Status.PENDING);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("cancelAndResolve: 활성 주문은 취소해 홀딩을 해제하고 CANCELED로 종결한다.")
    void cancelAndResolve_ActiveOrder_CancelsAndResolves() {
        FailedOrderEvent failed = failedRecord();
        when(failedOrderEventRepository.findById(1L)).thenReturn(Optional.of(failed));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        service.cancelAndResolve(1L);

        verify(orderService).cancelOrder(100L, 7L);
        assertThat(failed.getStatus()).isEqualTo(FailedOrderEvent.Status.RESOLVED);
        assertThat(failed.getResolution()).isEqualTo(FailedOrderEvent.Resolution.CANCELED);
    }

    @Test
    @DisplayName("cancelAndResolve: 이미 닫힌 주문은 취소 없이 ALREADY_CLOSED로 종결한다.")
    void cancelAndResolve_ClosedOrder_ResolvesWithoutCancel() {
        ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELED);
        FailedOrderEvent failed = failedRecord();
        when(failedOrderEventRepository.findById(1L)).thenReturn(Optional.of(failed));
        when(orderRepository.findById(100L)).thenReturn(Optional.of(order));

        service.cancelAndResolve(1L);

        verifyNoInteractions(orderService);
        assertThat(failed.getResolution()).isEqualTo(FailedOrderEvent.Resolution.ALREADY_CLOSED);
    }

    @Test
    @DisplayName("종결된 기록은 재발행하거나 다시 종결할 수 없다.")
    void resolvedRecord_RejectsFurtherActions() {
        FailedOrderEvent failed = failedRecord();
        failed.resolve(FailedOrderEvent.Resolution.CANCELED, LocalDateTime.now());
        when(failedOrderEventRepository.findById(1L)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service.replay(1L))
                .extracting("errorCode").isEqualTo(ErrorCode.FAILED_ORDER_EVENT_ALREADY_RESOLVED);
        assertThatThrownBy(() -> service.cancelAndResolve(1L))
                .extracting("errorCode").isEqualTo(ErrorCode.FAILED_ORDER_EVENT_ALREADY_RESOLVED);
        verifyNoInteractions(orderRepository, orderService, eventPublisher);
    }
}
