package com.tokit.global.config;

import com.tokit.domain.kyc.provider.KycVerificationProvider;
import com.tokit.domain.kyc.provider.StubKycVerificationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 실명확인 프로바이더 빈 등록.
 *
 * <p>{@link ConditionalOnMissingBean}은 자동설정의 {@code @Bean} 메서드에서만 신뢰할 수 있게
 * 동작합니다. 컴포넌트 스캔 대상 클래스에 직접 붙이면 조건 평가 시점에 빈 레지스트리가 아직
 * 완성되지 않아 결과가 비결정적이므로, 스텁 등록은 이 설정 클래스로 분리했습니다.
 *
 * <p>인가된 벤더 구현체를 빈으로 등록하면 이 스텁은 등록되지 않습니다.
 */
@Configuration
public class KycProviderConfig {

    @Bean
    @ConditionalOnMissingBean(KycVerificationProvider.class)
    public KycVerificationProvider stubKycVerificationProvider() {
        return new StubKycVerificationProvider();
    }
}
