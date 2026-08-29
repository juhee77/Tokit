package com.tokit.domain.kyc.service;

import com.tokit.domain.kyc.entity.KycVerification;
import com.tokit.domain.kyc.provider.KycVerificationProvider;
import com.tokit.domain.kyc.provider.KycVerificationRequest;
import com.tokit.domain.kyc.provider.KycVerificationResult;
import com.tokit.domain.kyc.repository.KycVerificationRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import com.tokit.infra.blockchain.ContractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 실명확인 절차. KYC 통과 여부는 사용자가 스스로 정하는 값이 아니라
 * 제공자의 검증 결과로만 바뀝니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class KycService {

    private final UserRepository userRepository;
    private final KycVerificationRepository kycVerificationRepository;
    private final KycVerificationProvider verificationProvider;
    private final ContractService contractService;

    @Transactional
    public KycVerification submitVerification(Long userId, KycVerificationRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        KycVerificationResult result = verificationProvider.verify(request);

        KycVerification record = kycVerificationRepository.save(KycVerification.builder()
                .user(user)
                .status(result.status())
                .provider(verificationProvider.providerName())
                .providerReference(result.providerReference())
                .rejectReason(result.reason())
                .verifiedAt(LocalDateTime.now())
                .build());

        // 승인된 경우에만 거래 자격을 부여하고 온체인 화이트리스트에 등록합니다.
        // PENDING(수동 심사)은 아직 자격이 없으므로 상태를 바꾸지 않습니다.
        if (result.isApproved()) {
            user.updateKycStatus(true);
            contractService.addToWhitelist(user.getWalletAddress());
            log.info("KYC approved for userId={} via provider={}", userId, verificationProvider.providerName());
        } else {
            log.info("KYC not approved for userId={} status={} reason={}",
                    userId, result.status(), result.reason());
        }

        return record;
    }

    /**
     * 운영자가 사후 제재 확인 등으로 자격을 회수합니다.
     */
    @Transactional
    public void revokeVerification(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        user.updateKycStatus(false);
        contractService.removeFromWhitelist(user.getWalletAddress());

        kycVerificationRepository.save(KycVerification.builder()
                .user(user)
                .status(KycVerificationResult.KycStatus.REJECTED)
                .provider("admin-revocation")
                .rejectReason(reason)
                .verifiedAt(LocalDateTime.now())
                .build());

        log.info("KYC revoked for userId={} reason={}", userId, reason);
    }

    public List<KycVerification> getVerificationHistory(Long userId) {
        return kycVerificationRepository.findByUser_IdOrderByVerifiedAtDesc(userId);
    }
}
