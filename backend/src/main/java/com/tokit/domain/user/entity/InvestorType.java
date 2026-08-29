package com.tokit.domain.user.entity;

import java.math.BigDecimal;

/**
 * 투자자 등급별 청약 한도.
 *
 * <p>조각투자 가이드라인은 한도를 두 축으로 규정합니다. 하나는 같은 발행인에게 몰아서
 * 투자하지 못하도록 하는 발행인별 한도이고, 다른 하나는 전체 위험 노출을 제한하는
 * 연간 누적 한도입니다. 두 한도는 함께 검증되어야 하며, 전문투자자는 양쪽 모두 면제됩니다.
 * ({@code null}은 한도 없음을 의미합니다.)
 */
public enum InvestorType {
    GENERAL("일반투자자", BigDecimal.valueOf(10_000_000), BigDecimal.valueOf(20_000_000)),
    QUALIFIED("소득적격투자자", BigDecimal.valueOf(20_000_000), BigDecimal.valueOf(40_000_000)),
    PROFESSIONAL("전문투자자", null, null);

    private final String description;
    private final BigDecimal perIssuerLimit;
    private final BigDecimal annualLimit;

    InvestorType(String description, BigDecimal perIssuerLimit, BigDecimal annualLimit) {
        this.description = description;
        this.perIssuerLimit = perIssuerLimit;
        this.annualLimit = annualLimit;
    }

    public String getDescription() {
        return description;
    }

    /** 동일 발행인에 대한 누적 청약 한도. null이면 한도 없음. */
    public BigDecimal getPerIssuerLimit() {
        return perIssuerLimit;
    }

    /** 최근 1년간 전체 누적 청약 한도. null이면 한도 없음. */
    public BigDecimal getAnnualLimit() {
        return annualLimit;
    }

    /**
     * @deprecated 발행인별 한도로 이름이 명확해졌습니다. {@link #getPerIssuerLimit()}을 사용하세요.
     */
    @Deprecated
    public BigDecimal getLimitAmount() {
        return perIssuerLimit;
    }
}
