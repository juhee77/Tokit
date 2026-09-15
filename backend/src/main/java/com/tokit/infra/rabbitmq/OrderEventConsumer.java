package com.tokit.infra.rabbitmq;

import com.tokit.domain.matching.service.MatchingService;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.global.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final OrderRepository orderRepository;
    private final MatchingService matchingService;

    /**
     * 주문 이벤트를 받아 매칭을 기동한다.
     *
     * <p>예외를 잡아 로그만 남기면 메시지가 ACK되어 사라지고, 주문은 체결도 취소도 되지 않은
     * 채 예치금/자산만 홀딩된 상태로 남는다. 체결 실패와 달리 이를 뒤늦게 잡아줄 대사 배치도
     * 없으므로, 여기서는 예외를 밖으로 던져 리스너의 재시도와 DLQ가 동작하게 한다.
     *
     * <p>일시적 실패(락 대기, 커넥션 순단 등)는 재시도로 복구되고, 재시도가 소진되면
     * 메시지는 폐기되지 않고 {@code tokit.order.dlq}에 남는다.
     */
    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE_NAME)
    public void consumeOrderEvent(OrderEvent event) {
        log.info("Consumed order event from RabbitMQ: {}", event);

        // 주문 행이 없는 것은 재시도해도 달라지지 않는다. 재시도를 건너뛰고 바로 DLQ로 보낸다.
        Order order = orderRepository.findByIdWithAsset(event.orderId())
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException(
                        "Order not found with id: " + event.orderId()));

        matchingService.matchOrder(order);
    }
}
