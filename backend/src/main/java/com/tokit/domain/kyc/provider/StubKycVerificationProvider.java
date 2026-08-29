package com.tokit.domain.kyc.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 로컬/데모용 실명확인 스텁.
 *
 * <p>실제 신원확인을 하지 않으므로 <b>운영 환경에서 사용해서는 안 됩니다.</b> 인가된 벤더
 * 구현체를 빈으로 등록하면 이 스텁은 자동으로 비활성화됩니다
 * ({@link ConditionalOnMissingBean}).
 *
 * <p>다만 아무나 통과시키지는 않고, 벤더 연동 시 실제로 거절되는 대표적인 경우
 * (필수 정보 누락, 미성년자)를 흉내 내 상위 로직이 거절 경로를 그대로 밟도록 합니다.
 */
@Component
@ConditionalOnMissingBean(KycVerificationProvider.class)
@Slf4j
public class StubKycVerificationProvider implements KycVerificationProvider {

    private static final int MINIMUM_AGE = 19;

    @Override
    public KycVerificationResult verify(KycVerificationRequest request) {
        log.warn("Using stub KYC provider — no real identity verification is performed. userId={}",
                request.userId());

        String reference = "STUB-" + UUID.randomUUID();

        if (isBlank(request.legalName()) || isBlank(request.nationalIdLast7()) || request.dateOfBirth() == null) {
            return KycVerificationResult.rejected(reference, "실명확인에 필요한 정보가 누락되었습니다.");
        }

        if (request.dateOfBirth().plusYears(MINIMUM_AGE).isAfter(LocalDate.now())) {
            return KycVerificationResult.rejected(reference, "만 19세 미만은 투자자 등록을 할 수 없습니다.");
        }

        return KycVerificationResult.approved(reference);
    }

    @Override
    public String providerName() {
        return "stub";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
