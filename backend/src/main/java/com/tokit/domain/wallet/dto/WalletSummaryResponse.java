package com.tokit.domain.wallet.dto;

import java.math.BigDecimal;
import java.util.List;

public record WalletSummaryResponse(
        Long userId,
        BigDecimal krwBalance,
        BigDecimal krwLockedBalance,
        BigDecimal totalPortfolioValue,
        int totalAssetCount,
        List<WalletResponse> wallets
) {}
