package com.tokit.infra.rabbitmq;

import com.tokit.domain.order.service.FailedOrderEventService;
import com.tokit.global.config.RabbitMQConfig;
import com.tokit.global.observability.TradingMetrics;
import com.tokit.infra.alert.SlackAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 매칭에 최종 실패한 주문 이벤트를 사람에게 알린다.
 *
 * <p>DLQ에 메시지를 쌓아두기만 하면 아무도 보지 않아 유실과 다를 바 없다. 주문은 접수 시점에
 * 이미 예치금/자산을 홀딩하므로, 방치되면 사용자 자금이 계속 묶인다. 따라서 도착 즉시 알린다.
 *
 * <p>이 리스너는 메시지를 소비하지 않고 알림만 보낸 뒤 그대로 둔다면 무한 재처리가 되므로,
 * ACK하여 DLQ를 비운다. 대신 메시지를 버리기 전에 {@code failed_order_events}에 적재해,
 * 알림을 놓치거나 전송이 실패해도 관리자 API로 재발행·종결할 수 있게 한다. (#23)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventDeadLetterConsumer {

    private final SlackAlertService slackAlertService;
    private final TradingMetrics tradingMetrics;
    private final FailedOrderEventService failedOrderEventService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_DLQ_NAME)
    public void consumeDeadLetteredOrderEvent(OrderEvent event) {
        log.error("Order event dead-lettered after retries. orderId={}, symbol={}",
                event.orderId(), event.assetSymbol());

        tradingMetrics.recordOrderMatchingFailure();

        // 적재가 먼저다. 적재에 실패하면 알림이 유일한 기록이 되므로, 그 사실을 알림에 담고
        // 예외를 올려 리스너 재시도를 태운다.
        Long failedEventId;
        try {
            failedEventId = failedOrderEventService.record(event).getId();
        } catch (RuntimeException e) {
            log.error("Failed to store dead-lettered order event. orderId={}", event.orderId(), e);
            alert(event, "실패 기록 적재에도 실패했습니다. 이 알림이 유일한 기록입니다.");
            throw e;
        }

        try {
            alert(event, "관리자 API(/api/admin/failed-orders/" + failedEventId + ")로 재발행하거나 취소해 종결하세요.");
        } catch (RuntimeException e) {
            // 기록은 이미 남았으므로 알림 실패로 메시지를 재처리하지 않는다.
            log.error("Failed to send DLQ alert. orderId={}", event.orderId(), e);
        }
    }

    private void alert(OrderEvent event, String recoveryHint) {
        slackAlertService.sendAlert(
                "주문 매칭 실패 (DLQ)",
                String.format(
                        "주문 ID %d의 매칭이 재시도 후에도 실패했습니다.%n"
                                + "종목: %s, 유형: %s, 가격: %s, 수량: %s, 사용자: %d%n"
                                + "이 주문은 체결도 취소도 되지 않은 상태이며 예치금/자산이 홀딩되어 있습니다. %s",
                        event.orderId(), event.assetSymbol(), event.orderType(),
                        event.price(), event.quantity(), event.userId(), recoveryHint)
        );
    }
}
