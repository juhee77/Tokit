package com.tokit.domain.kyc.provider;

/**
 * 실명확인(CDD) 수행 주체.
 *
 * <p>현재 KYC는 사용자가 스스로 켜는 boolean 값이라 실제 신원확인이 전혀 이뤄지지 않습니다.
 * 실제 서비스에서는 인가받은 실명확인 벤더가 이 자리를 대신해야 하므로, 도메인 코드가
 * 특정 벤더 API에 직접 묶이지 않도록 경계를 인터페이스로 고정합니다.
 *
 * <p>운영 환경에서 사용할 구현체는 다음을 충족해야 합니다.
 * <ul>
 *   <li>신분증 진위확인 및 계좌 실명확인</li>
 *   <li>제재 대상(sanctions/PEP) 스크리닝</li>
 *   <li>검증 결과와 근거의 보존 (특정금융정보법상 기록 보존 의무)</li>
 * </ul>
 */
public interface KycVerificationProvider {

    /**
     * 신원확인을 요청합니다. 구현체는 결과를 그대로 반환할 뿐, 사용자 상태를 직접 바꾸지 않습니다.
     */
    KycVerificationResult verify(KycVerificationRequest request);

    /** 감사 로그에 남길 제공자 식별자. */
    String providerName();
}
