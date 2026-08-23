package com.tokit.domain.wallet.entity;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WalletEntityTest {

    @Test
    @DisplayName("Wallet 엔티티 생성 및 잔액 갱신: KRW 예치금 및 STO 토큰 잔액/락 잔액이 정확히 갱신된다.")
    void builderAndUpdateBalance_WorksCorrectly() {
        // Given
        User user = User.builder()
                .name("Wallet User")
                .email("wallet.user@tokit.com")
                .walletAddress("0xWALLET_USER")
                .build();

        Asset asset = Asset.builder()
                .name("Pangyo Tech STO")
                .symbol("PANGYO-STO")
                .build();

        Wallet krwWallet = Wallet.builder()
                .user(user)
                .asset(null) // KRW 원화 지갑
                .balance(new BigDecimal("10000000"))
                .lockedBalance(BigDecimal.ZERO)
                .build();

        Wallet tokenWallet = Wallet.builder()
                .user(user)
                .asset(asset) // STO 지갑
                .balance(new BigDecimal("100"))
                .lockedBalance(new BigDecimal("10"))
                .build();

        // When
        krwWallet.updateBalance(new BigDecimal("9000000"), new BigDecimal("1000000"));
        tokenWallet.updateBalance(new BigDecimal("110"), BigDecimal.ZERO);

        // Then
        assertThat(krwWallet.getAsset()).isNull();
        assertThat(krwWallet.getBalance()).isEqualTo(new BigDecimal("9000000"));
        assertThat(krwWallet.getLockedBalance()).isEqualTo(new BigDecimal("1000000"));

        assertThat(tokenWallet.getAsset()).isEqualTo(asset);
        assertThat(tokenWallet.getBalance()).isEqualTo(new BigDecimal("110"));
        assertThat(tokenWallet.getLockedBalance()).isEqualTo(BigDecimal.ZERO);
    }
}
