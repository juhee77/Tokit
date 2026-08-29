package com.tokit.domain.fee.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FeePolicyTest {

    private final FeePolicy policy = new FeePolicy(new BigDecimal("0.001"));

    @Test
    @DisplayName("calculate: 체결 금액의 0.1%를 수수료로 산출한다.")
    void calculatesConfiguredRate() {
        assertThat(policy.calculate(new BigDecimal("1000000"))).isEqualByComparingTo("1000");
        assertThat(policy.calculate(new BigDecimal("50000"))).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("calculate: 원 단위 미만은 절사해 투자자에게 불리하지 않게 한다.")
    void truncatesBelowWon() {
        // 12,345 * 0.001 = 12.345 -> 12원
        assertThat(policy.calculate(new BigDecimal("12345"))).isEqualByComparingTo("12");
    }

    @Test
    @DisplayName("calculate: 0 이하 금액에는 수수료가 붙지 않는다.")
    void zeroForNonPositiveAmounts() {
        assertThat(policy.calculate(BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(policy.calculate(null)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("수수료는 체결 금액에 선형이므로 부분 체결분의 합이 전체 주문 수수료와 일치한다.")
    void feeIsLinearAcrossPartialFills() {
        // 주문 시 전체 금액 기준으로 홀딩한 수수료가 부분 체결에서 정확히 소진되어야
        // 취소 시 반환액이 어긋나지 않습니다.
        BigDecimal whole = policy.calculate(new BigDecimal("100000"));
        BigDecimal firstFill = policy.calculate(new BigDecimal("60000"));
        BigDecimal secondFill = policy.calculate(new BigDecimal("40000"));

        assertThat(firstFill.add(secondFill)).isEqualByComparingTo(whole);
    }
}
