package com.tokit.domain.matching.engine;

import com.tokit.domain.order.entity.Order;
import java.math.BigDecimal;
import java.util.List;

public record MatchResult(
    Order incomingOrder,
    List<Match> matches
) {
    public record Match(
        Order makerOrder,
        BigDecimal matchPrice,
        BigDecimal matchQuantity
    ) {}
}
