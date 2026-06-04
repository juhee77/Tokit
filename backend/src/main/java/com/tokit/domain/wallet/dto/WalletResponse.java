package com.tokit.domain.wallet.dto;

import com.tokit.domain.wallet.entity.Wallet;
import java.math.BigDecimal;

public record WalletResponse(
        Long id,
        Long userId,
        String assetSymbol,
        BigDecimal balance,
        BigDecimal lockedBalance
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUser().getId(),
                wallet.getAsset() != null ? wallet.getAsset().getSymbol() : "KRW",
                wallet.getBalance(),
                wallet.getLockedBalance()
        );
    }
}
