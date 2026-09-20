package com.tokit.global.config;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "tokit.exchange";
    public static final String ORDER_QUEUE_NAME = "tokit.order.queue";
    public static final String ORDER_ROUTING_KEY = "tokit.order.routing";
    public static final String TRADE_QUEUE_NAME = "tokit.trade.queue";
    public static final String TRADE_ROUTING_KEY = "tokit.trade.routing";

    // 주문 매칭 실패 시 메시지를 보관할 Dead Letter 설정.
    // 주문은 접수 시점에 이미 예치금/자산을 홀딩하므로, 매칭이 실패하면 체결도 취소도 되지
    // 않은 채 자금만 묶인다. 체결 실패와 달리 이를 뒤늦게 잡아줄 대사 배치도 없으므로,
    // 실패한 메시지를 폐기하지 않고 DLQ에 남겨 사람이 인지하고 복구할 수 있게 한다.
    public static final String ORDER_DLX_NAME = "tokit.order.dlx";
    public static final String ORDER_DLQ_NAME = "tokit.order.dlq";
    public static final String ORDER_DLQ_ROUTING_KEY = "tokit.order.dead";

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue orderQueue() {
        // 재시도가 모두 소진된 메시지는 DLX로 넘어간다.
        return QueueBuilder.durable(ORDER_QUEUE_NAME)
                .deadLetterExchange(ORDER_DLX_NAME)
                .deadLetterRoutingKey(ORDER_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange orderDeadLetterExchange() {
        return new DirectExchange(ORDER_DLX_NAME);
    }

    @Bean
    public Queue orderDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_DLQ_NAME).build();
    }

    @Bean
    public Binding orderDeadLetterBinding(Queue orderDeadLetterQueue, DirectExchange orderDeadLetterExchange) {
        return BindingBuilder.bind(orderDeadLetterQueue)
                .to(orderDeadLetterExchange)
                .with(ORDER_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue tradeQueue() {
        return new Queue(TRADE_QUEUE_NAME, true);
    }

    @Bean
    public Binding orderBinding(Queue orderQueue, DirectExchange exchange) {
        return BindingBuilder.bind(orderQueue).to(exchange).with(ORDER_ROUTING_KEY);
    }

    @Bean
    public Binding tradeBinding(Queue tradeQueue, DirectExchange exchange) {
        return BindingBuilder.bind(tradeQueue).to(exchange).with(TRADE_ROUTING_KEY);
    }

    /**
     * 재시도 정책: 영구 실패는 재시도하지 않는다.
     *
     * <p>리스너 재시도의 기본 정책은 예외 종류를 가리지 않으므로, "주문 행이 없음"처럼
     * 다시 시도해도 결과가 달라지지 않는 실패까지 재시도 예산(3회 × 최대 2초 백오프)을
     * 모두 소진한 뒤에야 DLQ로 넘어간다. 단일 컨슈머에서는 이 대기가 그대로 처리량 상한이
     * 되어, 영구 실패가 섞이면 큐 전체가 초당 한 건 아래로 떨어진다.
     *
     * <p>traverseCauses를 켜는 이유는 리스너 예외가 ListenerExecutionFailedException으로
     * 감싸여 전달되기 때문이다. 원인 체인을 따라가지 않으면 분류가 항상 빗나간다.
     */
    @Bean
    public RabbitListenerRetrySettingsCustomizer permanentFailureIsNotRetried() {
        return settings -> settings.setExceptionExcludes(
                List.of(AmqpRejectAndDontRequeueException.class));
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
