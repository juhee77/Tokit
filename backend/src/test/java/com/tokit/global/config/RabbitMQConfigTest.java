package com.tokit.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTest {

    private RabbitMQConfig rabbitMQConfig;

    @BeforeEach
    void setUp() {
        rabbitMQConfig = new RabbitMQConfig();
    }

    @Test
    @DisplayName("RabbitMQ 구성 요소 빈 검증: tokit.exchange, tokit.order.queue, tokit.trade.queue 및 바인딩이 정상 생성된다.")
    void rabbitMQConfig_CreatesQueuesExchangesAndBindings() {
        // When
        DirectExchange exchange = rabbitMQConfig.exchange();
        Queue orderQueue = rabbitMQConfig.orderQueue();
        Queue tradeQueue = rabbitMQConfig.tradeQueue();
        Binding orderBinding = rabbitMQConfig.orderBinding(orderQueue, exchange);
        Binding tradeBinding = rabbitMQConfig.tradeBinding(tradeQueue, exchange);
        MessageConverter messageConverter = rabbitMQConfig.messageConverter();

        // Then
        assertThat(exchange.getName()).isEqualTo(RabbitMQConfig.EXCHANGE_NAME);
        assertThat(orderQueue.getName()).isEqualTo(RabbitMQConfig.ORDER_QUEUE_NAME);
        assertThat(tradeQueue.getName()).isEqualTo(RabbitMQConfig.TRADE_QUEUE_NAME);

        assertThat(orderBinding.getRoutingKey()).isEqualTo(RabbitMQConfig.ORDER_ROUTING_KEY);
        assertThat(tradeBinding.getRoutingKey()).isEqualTo(RabbitMQConfig.TRADE_ROUTING_KEY);

        assertThat(messageConverter).isInstanceOf(Jackson2JsonMessageConverter.class);
    }
}
