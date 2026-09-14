package com.tokit.domain.wallet.service;

import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.dto.WalletResponse;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import com.tokit.domain.wallet.dto.WalletSummaryResponse;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public WalletSummaryResponse getWalletSummary(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        List<Wallet> wallets = walletRepository.findAllByUserId(userId);

        BigDecimal krwBalance = BigDecimal.ZERO;
        BigDecimal krwLockedBalance = BigDecimal.ZERO;
        BigDecimal totalAssetValuation = BigDecimal.ZERO;
        int assetCount = 0;

        for (Wallet wallet : wallets) {
            if (wallet.getAsset() == null) {
                krwBalance = wallet.getBalance();
                krwLockedBalance = wallet.getLockedBalance();
            } else {
                assetCount++;
                BigDecimal totalQty = wallet.getBalance().add(wallet.getLockedBalance());
                BigDecimal issuePrice = wallet.getAsset().getIssuePrice() != null
                        ? wallet.getAsset().getIssuePrice()
                        : BigDecimal.ZERO;
                totalAssetValuation = totalAssetValuation.add(totalQty.multiply(issuePrice));
            }
        }

        BigDecimal totalPortfolioValue = krwBalance.add(krwLockedBalance).add(totalAssetValuation);
        List<WalletResponse> walletResponses = wallets.stream().map(WalletResponse::from).toList();

        return new WalletSummaryResponse(
                userId,
                krwBalance,
                krwLockedBalance,
                totalPortfolioValue,
                assetCount,
                walletResponses
        );
    }

    @Transactional
    public WalletResponse depositKrw(Long userId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
        }

        Wallet wallet = getOrCreateKrwWalletWithLock(userId);
        
        wallet.updateBalance(wallet.getBalance().add(amount), wallet.getLockedBalance());
        return WalletResponse.from(wallet);
    }

    @Transactional
    public WalletResponse withdrawKrw(Long userId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("출금 금액은 0보다 커야 합니다.");
        }

        Wallet wallet = getOrCreateKrwWalletWithLock(userId);
        
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("출금 가능 잔고가 부족합니다.");
        }
        
        wallet.updateBalance(wallet.getBalance().subtract(amount), wallet.getLockedBalance());
        return WalletResponse.from(wallet);
    }

    private Wallet getOrCreateKrwWalletWithLock(Long userId) {
        return walletRepository.findKrwWalletByUserIdWithPessimisticLock(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
                    Wallet newWallet = Wallet.builder()
                            .user(user)
                            .asset(null)
                            .balance(BigDecimal.ZERO)
                            .lockedBalance(BigDecimal.ZERO)
                            .build();
                    return walletRepository.save(newWallet);
                });
    }
}
