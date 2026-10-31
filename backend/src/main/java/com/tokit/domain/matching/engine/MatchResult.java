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
        BigDecimal matchQuantity,
        // 이 체결 직후 신규(테이커) 주문의 잔량. 엔진이 모든 체결을 먼저 계산하므로
        // 정산 시점의 주문 엔티티에는 마지막 잔량만 남아 있어 체결별 잔량을 따로 실어 보낸다.
        BigDecimal incomingRemainAfter
    ) {}
}
