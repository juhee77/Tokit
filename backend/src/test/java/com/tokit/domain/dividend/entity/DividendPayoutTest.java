package com.tokit.domain.dividend.entity;

import com.tokit.domain.asset.entity.Asset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DividendPayoutTest {

    @Test
    @DisplayName("DividendPayout 엔티티 생성 및 상태 변경: 배당금 총액, 실행 일시 및 상태 전환(COMPLETED)이 정상 작동한다.")
    void builderAndStatusUpdate_WorksCorrectly() {
        // Given
        Asset asset = Asset.builder()
                .name("Gangnam Tower STO")
                .symbol("GANGNAM-STO")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .build();

        LocalDateTime payoutDate = LocalDateTime.now();

        DividendPayout payout = DividendPayout.builder()
                .asset(asset)
                .totalDividendAmount(new BigDecimal("5000000"))
                .payoutDate(payoutDate)
                .status("PROCESSING")
                .build();

        // When
        payout.updateStatus("COMPLETED");

        // Then
        assertThat(payout.getAsset()).isEqualTo(asset);
        assertThat(payout.getTotalDividendAmount()).isEqualTo(new BigDecimal("5000000"));
        assertThat(payout.getPayoutDate()).isEqualTo(payoutDate);
        assertThat(payout.getStatus()).isEqualTo("COMPLETED");
    }
}
