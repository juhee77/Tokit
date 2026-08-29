package com.tokit.domain.kyc.provider;

/**
 * 실명확인 결과. 승인/거절뿐 아니라 판단 근거와 벤더 측 참조 ID를 함께 보존합니다.
 */
public record KycVerificationResult(
        KycStatus status,
        String providerReference,
        String reason
) {

    public static KycVerificationResult approved(String providerReference) {
        return new KycVerificationResult(KycStatus.APPROVED, providerReference, null);
    }

    public static KycVerificationResult rejected(String providerReference, String reason) {
        return new KycVerificationResult(KycStatus.REJECTED, providerReference, reason);
    }

    public static KycVerificationResult pending(String providerReference) {
        return new KycVerificationResult(KycStatus.PENDING, providerReference, null);
    }

    public boolean isApproved() {
        return status == KycStatus.APPROVED;
    }

    public enum KycStatus {
        APPROVED,
        REJECTED,
        /** 벤더가 수동 심사로 넘긴 상태. 승인 전까지 거래를 허용하면 안 됩니다. */
        PENDING
    }
}
