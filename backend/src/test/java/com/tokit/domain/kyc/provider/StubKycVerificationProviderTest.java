package com.tokit.domain.kyc.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class StubKycVerificationProviderTest {

    private final StubKycVerificationProvider provider = new StubKycVerificationProvider();

    private KycVerificationRequest request(String name, LocalDate dob, String idLast7) {
        return new KycVerificationRequest(1L, name, dob, idLast7, "010-1234-5678", "0xWALLET");
    }

    @Test
    @DisplayName("성인이 필수 정보를 모두 제출하면 승인된다.")
    void approvesCompleteAdultSubmission() {
        KycVerificationResult result = provider.verify(
                request("홍길동", LocalDate.now().minusYears(30), "1234567"));

        assertThat(result.isApproved()).isTrue();
        assertThat(result.providerReference()).startsWith("STUB-");
    }

    @Test
    @DisplayName("필수 정보가 누락되면 거절된다.")
    void rejectsIncompleteSubmission() {
        KycVerificationResult result = provider.verify(
                request("", LocalDate.now().minusYears(30), "1234567"));

        assertThat(result.isApproved()).isFalse();
        assertThat(result.reason()).contains("누락");
    }

    @Test
    @DisplayName("만 19세 미만은 거절된다.")
    void rejectsMinor() {
        KycVerificationResult result = provider.verify(
                request("김미성", LocalDate.now().minusYears(18), "1234567"));

        assertThat(result.isApproved()).isFalse();
        assertThat(result.reason()).contains("19세");
    }
}
