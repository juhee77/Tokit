package com.tokit.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RedisOrderBookRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String KEY_PREFIX = "orderbook:";

    public void saveOrderBook(String symbol, OrderBookDto orderBook) {
        String key = KEY_PREFIX + symbol;
        redisTemplate.opsForValue().set(key, orderBook, 1, TimeUnit.DAYS);
    }

    public Optional<OrderBookDto> getOrderBook(String symbol) {
        String key = KEY_PREFIX + symbol;
        Object value = redisTemplate.opsForValue().get(key);
        if (value instanceof OrderBookDto orderBookDto) {
            return Optional.of(orderBookDto);
        }
        return Optional.empty();
    }
}
