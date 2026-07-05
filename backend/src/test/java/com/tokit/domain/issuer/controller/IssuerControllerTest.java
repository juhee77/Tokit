package com.tokit.domain.issuer.controller;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class IssuerControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private IssuerRepository issuerRepository;

    private Issuer testIssuer;
    private Asset testAsset;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        testIssuer = issuerRepository.save(Issuer.builder()
                .companyName("한강자산운용")
                .bizRegNo("123-45-67890")
                .build());

        testAsset = assetRepository.save(Asset.builder()
                .issuer(testIssuer)
                .name("한강 랜드마크 타워")
                .symbol("HAN-RIVER")
                .contractAddress("0x70997970C51812dc3A010C7d01b50e0d17dc79C9")
                .totalSupply(BigDecimal.valueOf(100000))
                .issuePrice(BigDecimal.valueOf(5000))
                .status("상장완료")
                .build());
    }

    @Test
    @DisplayName("발행사 자산 목록 조회 API가 정상적으로 동작해야 한다.")
    void getIssuerAssets_Success() throws Exception {
        mockMvc.perform(get("/api/issuer/assets")
                        .param("issuerId", testIssuer.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].symbol").value("HAN-RIVER"))
                .andExpect(jsonPath("$.data[0].name").value("한강 랜드마크 타워"));
    }

    @Test
    @DisplayName("공시 보고서 업로드 API가 정상적으로 동작해야 한다.")
    void uploadReport_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "quarter_report.pdf",
                "application/pdf",
                "dummy pdf content".getBytes()
        );

        mockMvc.perform(multipart("/api/issuer/reports")
                        .file(file)
                        .param("assetId", testAsset.getId().toString())
                        .param("title", "2026 3Q Performance Report")
                        .param("issuerId", testIssuer.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.title").value("2026 3Q Performance Report"));
    }
}
