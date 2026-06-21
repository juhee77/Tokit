package com.tokit.infra.rabbitmq;

import com.tokit.domain.matching.service.MatchingService;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.global.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final OrderRepository orderRepository;
    private final MatchingService matchingService;

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE_NAME)
    public void consumeOrderEvent(OrderEvent event) {
        log.info("Consumed order event from RabbitMQ: {}", event);
        
        try {
            // DB에서 최신 주문 정보 조회 (Lazy Loading 예외 방지를 위해 Asset을 JOIN FETCH로 함께 조회)
            Order order = orderRepository.findByIdWithAsset(event.orderId())
                    .orElseThrow(() -> new IllegalArgumentException("Order not found with id: " + event.orderId()));
            
            // 매칭 프로세스 기동
            matchingService.matchOrder(order);
            
        } catch (Exception e) {
            log.error("Error processing order matching event for order id: " + event.orderId(), e);
        }
    }
}
