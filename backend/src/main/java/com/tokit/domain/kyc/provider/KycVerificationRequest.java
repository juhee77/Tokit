package com.tokit.domain.kyc.provider;

import java.time.LocalDate;

/**
 * 실명확인 요청 정보. 벤더 연동 시 필요한 최소 항목만 담습니다.
 */
public record KycVerificationRequest(
        Long userId,
        String legalName,
        LocalDate dateOfBirth,
        String nationalIdLast7,
        String phoneNumber,
        String walletAddress
) {}
