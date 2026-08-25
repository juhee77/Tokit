package com.tokit.domain.issuer.controller;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.entity.AssetReport;
import com.tokit.domain.asset.repository.AssetReportRepository;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IssuerControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private IssuerController issuerController;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private AssetReportRepository assetReportRepository;

    private Asset testAsset;
    private Issuer testIssuer;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(issuerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testIssuer = Issuer.builder().companyName("Seoul Land Trust").bizRegNo("123-45-67890").build();
        setField(testIssuer, "id", 1L);

        testAsset = Asset.builder()
                .issuer(testIssuer)
                .name("Gangnam STO")
                .symbol("GNPM")
                .contractAddress("0x5FbDB2315678afecb367f032d93F642f64180aa3")
                .totalSupply(new BigDecimal("1000000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        setField(testAsset, "id", 1L);
    }

    @Test
    @DisplayName("GET /api/issuer/assets: 특정 발행사가 발행한 자산 목록과 공모 진행률이 반환된다.")
    void getIssuerAssets_Success() throws Exception {
        // Given
        given(assetRepository.findByIssuer_Id(1L)).willReturn(List.of(testAsset));
        given(walletRepository.sumBalanceByAssetId(1L)).willReturn(new BigDecimal("500000"));
        given(walletRepository.countInvestorsByAssetId(1L)).willReturn(120L);

        // When & Then
        mockMvc.perform(get("/api/issuer/assets").param("issuerId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].symbol").value("GNPM"))
                .andExpect(jsonPath("$.data[0].totalInvestors").value(120));
    }

    @Test
    @DisplayName("GET /api/issuer/assets/{symbol}/investors: 기초자산 주주명부와 지분율이 정상 계산되어 반환된다.")
    void getShareholders_Success() throws Exception {
        // Given
        User shareholder = User.builder().name("Juhee").walletAddress("0xSHAREHOLDER").build();
        Wallet wallet = Wallet.builder().user(shareholder).asset(testAsset).balance(new BigDecimal("100000")).build();

        given(assetRepository.findBySymbol("GNPM")).willReturn(Optional.of(testAsset));
        given(walletRepository.findByAsset_IdAndBalanceGreaterThanOrderByBalanceDesc(eq(1L), any(BigDecimal.class)))
                .willReturn(List.of(wallet));

        // When & Then
        mockMvc.perform(get("/api/issuer/assets/GNPM/investors").param("issuerId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].name").value("Juhee"))
                .andExpect(jsonPath("$.data[0].shareRatio").value(10.0));
    }

    @Test
    @DisplayName("GET /api/issuer/assets/{symbol}/reports: 자산별 분기 공시 보고서 목록이 반환된다.")
    void getReports_Success() throws Exception {
        // Given
        AssetReport report = AssetReport.builder()
                .asset(testAsset)
                .title("2026년 3분기 운용 보고서")
                .filePath("/uploads/2026_q3.pdf")
                .createdAt(LocalDateTime.now())
                .build();
        setField(report, "id", 10L);

        given(assetRepository.findBySymbol("GNPM")).willReturn(Optional.of(testAsset));
        given(assetReportRepository.findByAsset_IdOrderByCreatedAtDesc(1L)).willReturn(List.of(report));

        // When & Then
        mockMvc.perform(get("/api/issuer/assets/GNPM/reports").param("issuerId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].title").value("2026년 3분기 운용 보고서"));
    }
}
