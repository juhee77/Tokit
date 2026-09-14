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

    public BigDecimal calculateSpread() {
        if (bids == null || bids.isEmpty() || asks == null || asks.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal highestBid = bids.get(0).price();
        BigDecimal lowestAsk = asks.get(0).price();
        return lowestAsk.subtract(highestBid).max(BigDecimal.ZERO);
    }

    public BigDecimal calculateTotalBidVolume() {
        if (bids == null || bids.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return bids.stream()
                .map(OrderBookEntry::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalAskVolume() {
        if (asks == null || asks.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return asks.stream()
                .map(OrderBookEntry::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
