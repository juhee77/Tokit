package com.tokit.infra.rabbitmq;

import com.tokit.domain.order.entity.FailedOrderEvent;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.service.FailedOrderEventService;
import com.tokit.global.observability.TradingMetrics;
import com.tokit.infra.alert.SlackAlertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventDeadLetterConsumerTest {

    @InjectMocks
    private OrderEventDeadLetterConsumer consumer;

    @Mock
    private SlackAlertService slackAlertService;

    @Mock
    private TradingMetrics tradingMetrics;

    @Mock
    private FailedOrderEventService failedOrderEventService;

    private final OrderEvent event = new OrderEvent(
            100L, 7L, "PANGYO-STO", OrderType.BUY, new BigDecimal("10000"), BigDecimal.ONE);

    private FailedOrderEvent stored() {
        FailedOrderEvent failed = FailedOrderEvent.builder().orderId(100L).failedAt(LocalDateTime.now()).build();
        ReflectionTestUtils.setField(failed, "id", 42L);
        return failed;
    }

    @Test
    @DisplayName("DLQ 메시지는 적재된 뒤 복구 경로를 담은 알림으로 전달된다.")
    void storesThenAlertsWithRecoveryPath() {
        when(failedOrderEventService.record(event)).thenReturn(stored());

        consumer.consumeDeadLetteredOrderEvent(event);

        verify(failedOrderEventService).record(event);
        verify(slackAlertService).sendAlert(anyString(), contains("/api/admin/failed-orders/42"));
    }

    @Test
    @DisplayName("알림 전송이 실패해도 기록이 남았으므로 메시지를 재처리하지 않는다.")
    void alertFailureDoesNotRethrow() {
        when(failedOrderEventService.record(event)).thenReturn(stored());
        doThrow(new RuntimeException("slack down")).when(slackAlertService).sendAlert(anyString(), anyString());

        assertThatCode(() -> consumer.consumeDeadLetteredOrderEvent(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("적재가 실패하면 그 사실을 알림에 담고 예외를 올려 재시도를 태운다.")
    void storeFailureAlertsAndRethrows() {
        when(failedOrderEventService.record(event)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> consumer.consumeDeadLetteredOrderEvent(event))
                .isInstanceOf(IllegalStateException.class);
        verify(slackAlertService).sendAlert(anyString(), contains("유일한 기록"));
    }
}
