package com.tokit.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvestorTypeTest {

    @Test
    @DisplayName("InvestorType 열거형 검증: GENERAL(1,000만원), QUALIFIED(2,000만원), PROFESSIONAL(무제한) 한도가 정밀 매핑되어 있다.")
    void investorTypeValues_MatchLimitAmounts() {
        // Given & When & Then
        assertThat(InvestorType.GENERAL.getDescription()).isEqualTo("일반투자자");
        assertThat(InvestorType.GENERAL.getLimitAmount()).isEqualTo(BigDecimal.valueOf(10000000));

        assertThat(InvestorType.QUALIFIED.getDescription()).isEqualTo("소득적격투자자");
        assertThat(InvestorType.QUALIFIED.getLimitAmount()).isEqualTo(BigDecimal.valueOf(20000000));

        assertThat(InvestorType.PROFESSIONAL.getDescription()).isEqualTo("전문투자자");
        assertThat(InvestorType.PROFESSIONAL.getLimitAmount()).isNull();
    }
}
