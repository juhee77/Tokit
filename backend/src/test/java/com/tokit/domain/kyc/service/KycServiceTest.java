package com.tokit.domain.kyc.service;

import com.tokit.domain.kyc.entity.KycVerification;
import com.tokit.domain.kyc.provider.KycVerificationProvider;
import com.tokit.domain.kyc.provider.KycVerificationRequest;
import com.tokit.domain.kyc.provider.KycVerificationResult;
import com.tokit.domain.kyc.repository.KycVerificationRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.infra.blockchain.ContractService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

    @InjectMocks
    private KycService kycService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private KycVerificationRepository kycVerificationRepository;

    @Mock
    private KycVerificationProvider verificationProvider;

    @Mock
    private ContractService contractService;

    private User investor;
    private KycVerificationRequest request;

    @BeforeEach
    void setUp() {
        investor = User.builder()
                .name("KYC Investor")
                .email("kyc@tokit.com")
                .password("$2a$10$hash")
                .walletAddress("0xKYC_INVESTOR")
                .kycStatus(false)
                .build();
        ReflectionTestUtils.setField(investor, "id", 5L);

        request = new KycVerificationRequest(
                5L, "홍길동", LocalDate.of(1990, 1, 1), "1234567", "010-1234-5678", "0xKYC_INVESTOR");

        lenient().when(userRepository.findById(5L)).thenReturn(Optional.of(investor));
        lenient().when(verificationProvider.providerName()).thenReturn("stub");
        lenient().when(kycVerificationRepository.save(any(KycVerification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("승인된 경우에만 거래 자격이 부여되고 온체인 화이트리스트에 등록된다.")
    void approved_GrantsTradingRightsAndWhitelists() {
        when(verificationProvider.verify(any())).thenReturn(KycVerificationResult.approved("REF-1"));

        kycService.submitVerification(5L, request);

        assertThat(investor.isKycStatus()).isTrue();
        verify(contractService, times(1)).addToWhitelist("0xKYC_INVESTOR");
        verify(kycVerificationRepository, times(1)).save(any(KycVerification.class));
    }

    @Test
    @DisplayName("거절된 경우 거래 자격이 부여되지 않고 화이트리스트 등록도 하지 않는다.")
    void rejected_DoesNotGrantRights() {
        when(verificationProvider.verify(any()))
                .thenReturn(KycVerificationResult.rejected("REF-2", "제재 대상"));

        kycService.submitVerification(5L, request);

        assertThat(investor.isKycStatus()).isFalse();
        verifyNoInteractions(contractService);
        // 거절 이력도 보존되어야 감사 대응이 가능합니다.
        verify(kycVerificationRepository, times(1)).save(any(KycVerification.class));
    }

    @Test
    @DisplayName("수동 심사(PENDING)는 아직 자격이 없으므로 거래를 허용하지 않는다.")
    void pending_DoesNotGrantRights() {
        when(verificationProvider.verify(any())).thenReturn(KycVerificationResult.pending("REF-3"));

        kycService.submitVerification(5L, request);

        assertThat(investor.isKycStatus()).isFalse();
        verifyNoInteractions(contractService);
    }

    @Test
    @DisplayName("자격 회수 시 화이트리스트에서 제거되고 회수 이력이 남는다.")
    void revoke_RemovesRightsAndRecordsHistory() {
        investor.updateKycStatus(true);

        kycService.revokeVerification(5L, "제재 대상 확인");

        assertThat(investor.isKycStatus()).isFalse();
        verify(contractService, times(1)).removeFromWhitelist("0xKYC_INVESTOR");
        verify(kycVerificationRepository, times(1)).save(any(KycVerification.class));
    }
}
