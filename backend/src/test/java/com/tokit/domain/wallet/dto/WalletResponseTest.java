package com.tokit.domain.wallet.dto;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.wallet.entity.Wallet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WalletResponseTest {

    @Test
    @DisplayName("WalletResponse.from static factory: KRW 지갑 및 STO 지갑 엔티티가 Response DTO로 올바르게 변환된다.")
    void from_WalletEntity_MapsKrwAndTokenSymbolCorrectly() {
        // Given
        User user = User.builder().name("Test User").email("test@tokit.com").walletAddress("0xUSER").build();
        Asset asset = Asset.builder().name("Sample STO").symbol("SAMPLE-STO").build();

        Wallet krwWallet = Wallet.builder()
                .user(user)
                .asset(null)
                .balance(new BigDecimal("5000000"))
                .lockedBalance(BigDecimal.ZERO)
                .build();

        Wallet tokenWallet = Wallet.builder()
                .user(user)
                .asset(asset)
                .balance(new BigDecimal("50"))
                .lockedBalance(new BigDecimal("10"))
                .build();

        // When
        WalletResponse krwResponse = WalletResponse.from(krwWallet);
        WalletResponse tokenResponse = WalletResponse.from(tokenWallet);

        // Then
        assertThat(krwResponse.assetSymbol()).isEqualTo("KRW");
        assertThat(krwResponse.balance()).isEqualTo(new BigDecimal("5000000"));
        assertThat(krwResponse.lockedBalance()).isEqualTo(BigDecimal.ZERO);

        assertThat(tokenResponse.assetSymbol()).isEqualTo("SAMPLE-STO");
        assertThat(tokenResponse.balance()).isEqualTo(new BigDecimal("50"));
        assertThat(tokenResponse.lockedBalance()).isEqualTo(new BigDecimal("10"));
    }
}
