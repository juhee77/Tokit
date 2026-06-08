package com.tokit.global.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "tokit.exchange";
    public static final String ORDER_QUEUE_NAME = "tokit.order.queue";
    public static final String ORDER_ROUTING_KEY = "tokit.order.routing";
    public static final String TRADE_QUEUE_NAME = "tokit.trade.queue";
    public static final String TRADE_ROUTING_KEY = "tokit.trade.routing";

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue orderQueue() {
        return new Queue(ORDER_QUEUE_NAME, true);
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

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
