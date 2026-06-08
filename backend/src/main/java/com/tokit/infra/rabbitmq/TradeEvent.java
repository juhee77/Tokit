package com.tokit.infra.rabbitmq;

import java.math.BigDecimal;

public record TradeEvent(
        Long tradeId,
        Long buyOrderId,
        Long sellOrderId,
        String assetSymbol,
        String buyerWalletAddress,
        String sellerWalletAddress,
        BigDecimal price,
        BigDecimal quantity
) {}
