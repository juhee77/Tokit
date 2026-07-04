package com.tokit.domain.asset.controller;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.infra.blockchain.ContractService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AssetSubscriptionIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private IssuerRepository issuerRepository;

    @MockitoBean
    private ContractService contractService;

    private User testUser;
    private Asset testAsset;
    private Issuer testIssuer;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // 1. 테스트 유저 생성 (Flyway V4 시드 데이터와 충돌 방지)
        testUser = userRepository.findByEmail("test-investor@tokit.com")
                .map(existingUser -> {
                    existingUser.updateKycStatus(true);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .name("김토킷")
                        .email("test-investor@tokit.com")
                        .walletAddress("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")
                        .kycStatus(true)
                        .build()));

        // 2. 발행인 생성
        testIssuer = issuerRepository.save(Issuer.builder()
                .companyName("서울랜드트러스트")
                .bizRegNo("999-99-99998")
                .build());

        // 3. 자산 생성 (청약중 상태)
        testAsset = assetRepository.save(Asset.builder()
                .issuer(testIssuer)
                .name("서울 강남 프라임 오피스 빌딩")
                .symbol("TEST-GNPM")
                .contractAddress("0x5FbDB2315678afecb367f032d93F642f64180aa3")
                .totalSupply(BigDecimal.valueOf(5000000))
                .issuePrice(BigDecimal.valueOf(10000)) // 1토큰 = 10,000원
                .status("청약중")
                .build());

        // 4. 원화 지갑 생성 (잔액: 1,000,000원)
        walletRepository.save(Wallet.builder()
                .user(testUser)
                .asset(null)
                .balance(BigDecimal.valueOf(1000000))
                .lockedBalance(BigDecimal.ZERO)
                .build());
    }

    @Test
    @DisplayName("토큰증권 청약 신청 성공 - 예치금 차감 및 토큰 배정 완료")
    void subscribeAsset_Success() throws Exception {
        // given
        String requestBody = "{\"userId\":" + testUser.getId() + ",\"amount\":200000}"; // 200,000원 투자 (20토큰 배정 예상)
        
        // blockchain transfer 모킹
        doNothing().when(contractService).handleTransferByPartition(any(), any(), any(), any(), any());

        // when & then
        mockMvc.perform(post("/api/assets/TEST-GNPM/subscribe")
                        .header("X-Idempotency-Key", "idempotency-key-uuid-1234")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        // 예치금 잔액 확인 (1,000,000 - 200,000 = 800,000)
        Wallet krwWallet = walletRepository.findKrwWalletByUserId(testUser.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, krwWallet.getBalance().compareTo(BigDecimal.valueOf(800000)));

        // 배정된 토큰 잔액 확인 (20토큰)
        Wallet tokenWallet = walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(testUser.getId(), testAsset.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(0, tokenWallet.getBalance().compareTo(BigDecimal.valueOf(20)));

        // 온체인 전송 호출 여부 검증
        verify(contractService).handleTransferByPartition(
                eq("TEST-GNPM"),
                eq("DEFAULT"),
                eq("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"),
                eq(testUser.getWalletAddress()),
                eq(BigDecimal.valueOf(20).setScale(4)) // 20.0000
        );
    }

    @Test
    @DisplayName("토큰증권 청약 신청 실패 - KYC 미인증 사용자")
    void subscribeAsset_Fail_KycRequired() throws Exception {
        // given
        testUser.updateKycStatus(false);
        userRepository.save(testUser);
        
        String requestBody = "{\"userId\":" + testUser.getId() + ",\"amount\":200000}";

        // when & then
        mockMvc.perform(post("/api/assets/TEST-GNPM/subscribe")
                        .header("X-Idempotency-Key", "idempotency-key-uuid-1235")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토큰증권 청약 신청 실패 - 잔액 부족")
    void subscribeAsset_Fail_InsufficientBalance() throws Exception {
        // given
        String requestBody = "{\"userId\":" + testUser.getId() + ",\"amount\":2000000}"; // 2,000,000원 투자 시도 (잔액 1,000,000원)

        // when & then
        mockMvc.perform(post("/api/assets/TEST-GNPM/subscribe")
                        .header("X-Idempotency-Key", "idempotency-key-uuid-1236")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());
    }
}
