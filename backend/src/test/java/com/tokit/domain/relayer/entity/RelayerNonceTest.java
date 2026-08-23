package com.tokit.domain.relayer.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelayerNonceTest {

    @Test
    @DisplayName("builder: 대소문자 혼용 지갑 주소를 소문자로 자동 정규화하고 초기 논스를 0으로 세팅한다.")
    void builder_LowerCasesWalletAddressAndDefaultsNonce() {
        // Given
        String mixedCaseAddress = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";

        // When
        RelayerNonce relayerNonce = RelayerNonce.builder()
                .walletAddress(mixedCaseAddress)
                .build();

        // Then
        assertThat(relayerNonce.getWalletAddress()).isEqualTo(mixedCaseAddress.toLowerCase());
        assertThat(relayerNonce.getNextNonce()).isEqualTo(0L);
        assertThat(relayerNonce.getDailyTxCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("incrementNonce: 가스리스 트랜잭션 성공 시 논스가 1 증가한다.")
    void incrementNonce_IncrementsNextNonceByOne() {
        // Given
        RelayerNonce relayerNonce = RelayerNonce.builder()
                .walletAddress("0xuser")
                .nextNonce(5L)
                .build();

        // When
        relayerNonce.incrementNonce();

        // Then
        assertThat(relayerNonce.getNextNonce()).isEqualTo(6L);
    }

    @Test
    @DisplayName("checkAndIncrementLimit: 일일 대납 이체 한도 5회 초과 시 IllegalArgumentException 예외가 던져진다.")
    void checkAndIncrementLimit_Enforces5TxDailyLimit() {
        // Given
        RelayerNonce relayerNonce = RelayerNonce.builder()
                .walletAddress("0xlimituser")
                .lastTxDate(LocalDate.now())
                .dailyTxCount(4)
                .build();

        // 5번째 호출까지는 성공
        relayerNonce.checkAndIncrementLimit();
        assertThat(relayerNonce.getDailyTxCount()).isEqualTo(5);

        // 6번째 호출 시 5회 한도 초과 예외 발생
        assertThatThrownBy(relayerNonce::checkAndIncrementLimit)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("일일 대납 이체 한도(5회)를 초과했습니다.");
    }
}
