package com.tokit.domain.user.service;

import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import com.tokit.infra.blockchain.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ContractService contractService;
    private final WalletRepository walletRepository;
    private final AssetRepository assetRepository;

    @Transactional
    public User signUp(String email, String name, String walletAddress) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = userRepository.save(User.builder()
                            .email(email)
                            .name(name)
                            .walletAddress(walletAddress)
                            .build());

                    // 기본 원화(KRW) 예치금 지갑 자동 개설 (10,000,000 KRW)
                    walletRepository.save(Wallet.builder()
                            .user(newUser)
                            .asset(null)
                            .balance(java.math.BigDecimal.valueOf(10000000.0))
                            .lockedBalance(java.math.BigDecimal.ZERO)
                            .build());

                    // 대표 토큰증권 보유 지갑 개설 및 초기 보유량 시딩
                    List<String> defaultSymbols = List.of("HDYT", "GNPM", "BSND", "JJIS");
                    for (String symbol : defaultSymbols) {
                        assetRepository.findBySymbol(symbol).ifPresent(asset -> {
                            walletRepository.save(Wallet.builder()
                                    .user(newUser)
                                    .asset(asset)
                                    .balance(java.math.BigDecimal.valueOf(1000.0))
                                    .lockedBalance(java.math.BigDecimal.ZERO)
                                    .build());
                        });
                    }

                    return newUser;
                });
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional
    public User updateKycStatus(Long id, boolean kycStatus) {
        User user = getUserById(id);
        user.updateKycStatus(kycStatus);
        
        // 온체인 화이트리스트 동기화
        if (kycStatus) {
            contractService.addToWhitelist(user.getWalletAddress());
        } else {
            contractService.removeFromWhitelist(user.getWalletAddress());
        }
        
        return user;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
