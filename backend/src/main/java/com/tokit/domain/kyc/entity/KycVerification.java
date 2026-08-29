package com.tokit.domain.kyc.entity;

import com.tokit.domain.kyc.provider.KycVerificationResult;
import com.tokit.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 실명확인 시도 이력. 특정금융정보법은 고객확인 결과와 근거의 보존을 요구하는데,
 * users.kyc_status 라는 boolean 하나로는 "누가, 언제, 어떤 근거로 승인했는지"를 답할 수 없습니다.
 */
@Entity
@Table(name = "kyc_verifications", indexes = {
        @Index(name = "idx_kyc_verifications_user_verified_at", columnList = "user_id, verified_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KycVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycVerificationResult.KycStatus status;

    /** 실명확인을 수행한 제공자 식별자 (예: 벤더명 또는 stub). */
    @Column(nullable = false)
    private String provider;

    /** 벤더 측 조회 키. 분쟁·감사 시 원본 기록을 되짚기 위해 보존합니다. */
    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    @Builder
    public KycVerification(User user, KycVerificationResult.KycStatus status, String provider,
                           String providerReference, String rejectReason, LocalDateTime verifiedAt) {
        this.user = user;
        this.status = status;
        this.provider = provider;
        this.providerReference = providerReference;
        this.rejectReason = rejectReason;
        this.verifiedAt = verifiedAt != null ? verifiedAt : LocalDateTime.now();
    }
}
