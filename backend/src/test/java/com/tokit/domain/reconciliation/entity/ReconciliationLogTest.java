package com.tokit.domain.reconciliation.entity;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReconciliationLogTest {

    @Test
    @DisplayName("ReconciliationLog 엔티티 생성: 오프체인 RDBMS 잔액과 온체인 블록체인 잔액 오차가 정상 기록된다.")
    void builder_StoresDiscrepancyLogCorrectly() {
        // Given
        User user = User.builder()
                .name("Discrepancy User")
                .email("discrepancy@tokit.com")
                .walletAddress("0xDISCREPANCY_WALLET")
                .build();

        Asset asset = Asset.builder()
                .name("Yeoksam STO")
                .symbol("YEOKSAM-STO")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .build();

        LocalDateTime now = LocalDateTime.now();

        // When
        ReconciliationLog log = ReconciliationLog.builder()
                .user(user)
                .asset(asset)
                .walletAddress("0xDISCREPANCY_WALLET")
                .offchainBalance(new BigDecimal("10000.0000"))
                .onchainBalance(new BigDecimal("9900.0000"))
                .difference(new BigDecimal("100.0000"))
                .checkedAt(now)
                .build();

        // Then
        assertThat(log.getWalletAddress()).isEqualTo("0xDISCREPANCY_WALLET");
        assertThat(log.getOffchainBalance()).isEqualTo(new BigDecimal("10000.0000"));
        assertThat(log.getOnchainBalance()).isEqualTo(new BigDecimal("9900.0000"));
        assertThat(log.getDifference()).isEqualTo(new BigDecimal("100.0000"));
        assertThat(log.getCheckedAt()).isEqualTo(now);
    }
}
