package com.tokit.infra.redis;

import java.math.BigDecimal;
import java.util.List;

public record OrderBookDto(
    String symbol,
    List<OrderBookEntry> bids, // 매수 호가 목록
    List<OrderBookEntry> asks  // 매도 호가 목록
) {
    public record OrderBookEntry(
        BigDecimal price,
        BigDecimal quantity
    ) {}
}
