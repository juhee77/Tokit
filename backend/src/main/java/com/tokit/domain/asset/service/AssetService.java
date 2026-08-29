package com.tokit.domain.asset.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.entity.Subscription;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.asset.repository.SubscriptionRepository;
import com.tokit.domain.user.entity.InvestorType;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import com.tokit.infra.blockchain.ContractService;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final ContractService contractService;
    private final IssuerRepository issuerRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public Asset registerAsset(String symbol, String name, String contractAddress, BigDecimal totalSupply,
                                BigDecimal issuePrice, String status, Long issuerId) {
        if (assetRepository.findBySymbol(symbol).isPresent()) {
            throw new BusinessException("Symbol already exists", ErrorCode.INVALID_INPUT_VALUE);
        }
        
        Issuer issuer;
        if (issuerId != null) {
            issuer = issuerRepository.findById(issuerId)
                    .orElseThrow(() -> new BusinessException("Issuer not found", ErrorCode.INVALID_INPUT_VALUE));
        } else {
            issuer = issuerRepository.findAll().stream().findFirst()
                    .orElseGet(() -> issuerRepository.save(Issuer.builder()
                            .companyName("서울랜드트러스트")
                            .bizRegNo("123-45-67890")
                            .build()));
        }
        
        BigDecimal resolvedPrice = issuePrice != null ? issuePrice : BigDecimal.valueOf(10000);
        String resolvedStatus = status != null ? status : "청약중";

        Asset asset = Asset.builder()
                .symbol(symbol)
                .name(name)
                .contractAddress(contractAddress)
                .totalSupply(totalSupply)
                .issuer(issuer)
                .issuePrice(resolvedPrice)
                .status(resolvedStatus)
                .build();
        return assetRepository.save(asset);
    }

    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    public Asset getAssetBySymbol(String symbol) {
        return assetRepository.findBySymbol(symbol)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
    }

    public Asset getAssetById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
    }

    @Transactional
    public void subscribeAsset(String symbol, Long userId, BigDecimal amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        if (!user.isKycStatus()) {
            throw new BusinessException("KYC 신원인증이 완료되지 않은 사용자입니다.", ErrorCode.INVALID_INPUT_VALUE);
        }

        Asset asset = getAssetBySymbol(symbol);

        if (!"청약중".equals(asset.getStatus())) {
            throw new BusinessException("현재 청약 진행 중인 자산이 아닙니다.", ErrorCode.INVALID_INPUT_VALUE);
        }

        // 1. 원화 예치금 지갑 잠금 및 잔액 차감
        Wallet krwWallet = walletRepository.findKrwWalletByUserIdWithPessimisticLock(userId)
                .orElseThrow(() -> new BusinessException("원화 지갑이 존재하지 않습니다.", ErrorCode.INVALID_INPUT_VALUE));

        if (krwWallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("예치금 잔액이 부족합니다.", ErrorCode.INVALID_INPUT_VALUE);
        }

        // 2. 토큰 자산 지갑 잠금 및 잔고 추가
        Wallet assetWallet = walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(userId, asset.getId())
                .orElseGet(() -> Wallet.builder()
                        .user(user)
                        .asset(asset)
                        .balance(BigDecimal.ZERO)
                        .lockedBalance(BigDecimal.ZERO)
                        .build());

        // 투자자 등급 한도 검증 (발행인별 + 연간 누적)
        verifyInvestmentLimits(user, asset, amount);

        // 3. 잔액 차감 및 영속화
        krwWallet.updateBalance(krwWallet.getBalance().subtract(amount), krwWallet.getLockedBalance());
        
        BigDecimal tokenQuantity = amount.divide(asset.getIssuePrice(), 4, RoundingMode.HALF_UP);
        assetWallet.updateBalance(assetWallet.getBalance().add(tokenQuantity), assetWallet.getLockedBalance());
        
        if (assetWallet.getId() == null) {
            walletRepository.save(assetWallet);
        }

        // 한도 검증의 근거가 되는 청약 이력을 남깁니다.
        subscriptionRepository.save(Subscription.builder()
                .user(user)
                .asset(asset)
                .issuer(asset.getIssuer())
                .amount(amount)
                .tokenQuantity(tokenQuantity)
                .subscribedAt(LocalDateTime.now())
                .build());

        // 4. 온체인 토큰 강제 전송 실행 (Admin/Deployer -> User)
        String deployerAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
        contractService.handleTransferByPartition(
                asset.getSymbol(),
                "DEFAULT",
                deployerAddress,
                user.getWalletAddress(),
                tokenQuantity
        );
    }

    public BigDecimal getAssetCurrentAmount(Asset asset) {
        BigDecimal sum = walletRepository.sumBalanceByAssetId(asset.getId());
        if ("GNPM".equals(asset.getSymbol())) {
            // Base simulation for GNPM: 3,575,000 tokens (71.5%)
            sum = sum.add(BigDecimal.valueOf(3575000));
        }
        return sum.multiply(asset.getIssuePrice());
    }

    public int getAssetTotalInvestors(Asset asset) {
        long count = walletRepository.countInvestorsByAssetId(asset.getId());
        if ("GNPM".equals(asset.getSymbol())) {
            // Base simulation for GNPM: 1847 investors
            count += 1847;
        }
        return (int) count;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<Long, BigDecimal> getAssetCurrentAmountsMap() {
        List<Object[]> rows = walletRepository.sumBalancesGroupByAssetId();
        return rows.stream().collect(java.util.stream.Collectors.toMap(
                row -> (Long) row[0],
                row -> (BigDecimal) row[1],
                (v1, v2) -> v1
        ));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<Long, Long> getAssetTotalInvestorsMap() {
        List<Object[]> rows = walletRepository.countInvestorsGroupByAssetId();
        return rows.stream().collect(java.util.stream.Collectors.toMap(
                row -> (Long) row[0],
                row -> (Long) row[1],
                (v1, v2) -> v1
        ));
    }

    /**
     * 발행인별·연간 누적 청약 한도를 함께 검증합니다.
     *
     * <p>이전에는 해당 자산의 지갑 잔고만 확인했기 때문에, 같은 발행인의 다른 종목으로
     * 나누어 청약하면 한도를 우회할 수 있었습니다. 이제 청약 이력을 합산해 판단합니다.
     */
    private void verifyInvestmentLimits(User user, Asset asset, BigDecimal amount) {
        InvestorType investorType = user.getInvestorType();

        BigDecimal perIssuerLimit = investorType.getPerIssuerLimit();
        if (perIssuerLimit != null) {
            BigDecimal issuerTotal = subscriptionRepository
                    .sumAmountByUserAndIssuer(user.getId(), asset.getIssuer().getId())
                    .add(amount);
            if (issuerTotal.compareTo(perIssuerLimit) > 0) {
                throw new BusinessException(
                        String.format("동일 발행인 투자 한도를 초과하여 청약할 수 없습니다. (한도: %s KRW, 청약 후 누적: %s KRW)",
                                perIssuerLimit.toPlainString(), issuerTotal.toPlainString()),
                        ErrorCode.INVALID_INPUT_VALUE);
            }
        }

        BigDecimal annualLimit = investorType.getAnnualLimit();
        if (annualLimit != null) {
            BigDecimal annualTotal = subscriptionRepository
                    .sumAmountByUserSince(user.getId(), LocalDateTime.now().minusYears(1))
                    .add(amount);
            if (annualTotal.compareTo(annualLimit) > 0) {
                throw new BusinessException(
                        String.format("연간 누적 투자 한도를 초과하여 청약할 수 없습니다. (한도: %s KRW, 청약 후 누적: %s KRW)",
                                annualLimit.toPlainString(), annualTotal.toPlainString()),
                        ErrorCode.INVALID_INPUT_VALUE);
            }
        }
    }
}
