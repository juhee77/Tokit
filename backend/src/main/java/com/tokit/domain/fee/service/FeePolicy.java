package com.tokit.domain.fee.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 2차 거래 수수료 정책.
 *
 * <p>수수료는 체결 금액에 대해 선형이므로, 주문 시 전체 금액 기준으로 예치금을 홀딩해 두면
 * 부분 체결이 여러 번 일어나도 각 체결분 수수료의 합이 최초 홀딩액과 정확히 일치합니다.
 * 원 단위 미만은 버려 투자자에게 불리하지 않도록 절사합니다.
 */
@Component
public class FeePolicy {

    private final BigDecimal rate;

    public FeePolicy(@Value("${tokit.fee.trading-rate:0.001}") BigDecimal rate) {
        this.rate = rate;
    }

    /** 체결 금액에 대한 편도(한쪽) 수수료. */
    public BigDecimal calculate(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(rate).setScale(0, RoundingMode.DOWN);
    }

    public BigDecimal getRate() {
        return rate;
    }
}
