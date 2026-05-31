package com.tokit.infra.rabbitmq;

import com.tokit.domain.order.entity.OrderType;
import java.math.BigDecimal;

public record OrderEvent(
    Long orderId,
    Long memberId,
    String assetSymbol,
    OrderType orderType,
    BigDecimal price,
    BigDecimal quantity
) {}
