package com.tokit.domain.dividend.entity;

import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DividendPayoutDetailTest {

    @Test
    @DisplayName("DividendPayoutDetail 엔티티 생성 및 상태 변경: 지분 비율, 배당금 및 상태 갱신 메서드가 정상 작동한다.")
    void builderAndStatusUpdate_WorksCorrectly() {
        // Given
        User user = User.builder()
                .name("Dividend Recipient")
                .email("recipient@tokit.com")
                .walletAddress("0xRECIPIENT_WALLET")
                .build();

        DividendPayout payout = DividendPayout.builder()
                .totalDividendAmount(new BigDecimal("10000000"))
                .status("PROCESSING")
                .build();

        DividendPayoutDetail detail = DividendPayoutDetail.builder()
                .payout(payout)
                .user(user)
                .walletAddress("0xRECIPIENT_WALLET")
                .shareRatio(new BigDecimal("0.150000"))
                .payoutAmount(new BigDecimal("1500000"))
                .status("PENDING")
                .errorMessage(null)
                .build();

        // When
        detail.updateStatus("SUCCESS", null);

        // Then
        assertThat(detail.getWalletAddress()).isEqualTo("0xRECIPIENT_WALLET");
        assertThat(detail.getShareRatio()).isEqualTo(new BigDecimal("0.150000"));
        assertThat(detail.getPayoutAmount()).isEqualTo(new BigDecimal("1500000"));
        assertThat(detail.getStatus()).isEqualTo("SUCCESS");
        assertThat(detail.getErrorMessage()).isNull();
    }
}
