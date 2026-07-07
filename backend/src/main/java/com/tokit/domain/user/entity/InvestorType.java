package com.tokit.domain.user.entity;

import java.math.BigDecimal;

public enum InvestorType {
    GENERAL("일반투자자", BigDecimal.valueOf(10000000)),
    QUALIFIED("소득적격투자자", BigDecimal.valueOf(20000000)),
    PROFESSIONAL("전문투자자", null);

    private final String description;
    private final BigDecimal limitAmount;

    InvestorType(String description, BigDecimal limitAmount) {
        this.description = description;
        this.limitAmount = limitAmount;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }
}
