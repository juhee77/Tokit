package com.tokit.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityTest {

    @Test
    @DisplayName("User 엔티티 생성 및 KYC/InvestorType 상태 변경: 기본 투자자 유형(GENERAL)과 KYC 상태 전환이 작동한다.")
    void builderAndUpdate_WorksCorrectly() {
        // Given
        User user = User.builder()
                .name("General Investor")
                .email("general@tokit.com")
                .walletAddress("0xUSER_WALLET_ADDRESS")
                .kycStatus(false)
                .build();

        // When
        user.updateKycStatus(true);
        user.updateInvestorType(InvestorType.QUALIFIED);

        // Then
        assertThat(user.getName()).isEqualTo("General Investor");
        assertThat(user.getEmail()).isEqualTo("general@tokit.com");
        assertThat(user.getWalletAddress()).isEqualTo("0xUSER_WALLET_ADDRESS");
        assertThat(user.isKycStatus()).isTrue();
        assertThat(user.getInvestorType()).isEqualTo(InvestorType.QUALIFIED);
    }
}
