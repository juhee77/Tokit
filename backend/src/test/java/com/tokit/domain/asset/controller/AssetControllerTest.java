package com.tokit.domain.asset.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.service.AssetService;
import com.tokit.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssetControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private AssetController assetController;

    @Mock
    private AssetService assetService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private Asset testAsset;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(assetController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testAsset = Asset.builder()
                .name("Seocho STO")
                .symbol("SEOCHO-STO")
                .contractAddress("0x5FbDB2315678afecb367f032d93F642f64180aa3")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        setField(testAsset, "id", 1L);
    }

    @Test
    @DisplayName("POST /api/assets: 올바른 자산 정보 제출 시 등록이 성공하고 HTTP 200을 반환한다.")
    void registerAsset_Success() throws Exception {
        // Given
        AssetController.RegisterAssetRequest request = new AssetController.RegisterAssetRequest(
                "SEOCHO-STO", "Seocho STO", "0x5FbDB2315678afecb367f032d93F642f64180aa3",
                new BigDecimal("100000"), new BigDecimal("10000"), "상장완료", 1L
        );

        when(assetService.registerAsset(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(testAsset);

        // When & Then
        mockMvc.perform(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.symbol").value("SEOCHO-STO"))
                .andExpect(jsonPath("$.data.name").value("Seocho STO"));
    }

    @Test
    @DisplayName("POST /api/assets: 필수값(symbol, name 등) 누락 시 Valid 검증에 걸려 HTTP 400을 반환한다.")
    void registerAsset_ValidationError_Returns400() throws Exception {
        // Given: symbol, name 누락
        AssetController.RegisterAssetRequest request = new AssetController.RegisterAssetRequest(
                "", "", "", new BigDecimal("-10"), null, null, null
        );

        // When & Then
        mockMvc.perform(post("/api/assets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /api/assets: 전체 자산 목록 조회가 성공하여 HTTP 200을 반환한다.")
    void getAllAssets_Success() throws Exception {
        // Given
        when(assetService.getAllAssets()).thenReturn(List.of(testAsset));

        // When & Then
        mockMvc.perform(get("/api/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].symbol").value("SEOCHO-STO"));
    }

    @Test
    @DisplayName("GET /api/assets/{symbol}: 심볼 단건 조회가 성공하여 HTTP 200을 반환한다.")
    void getAssetBySymbol_Success() throws Exception {
        // Given
        when(assetService.getAssetBySymbol("SEOCHO-STO")).thenReturn(testAsset);

        // When & Then
        mockMvc.perform(get("/api/assets/SEOCHO-STO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.symbol").value("SEOCHO-STO"));
    }
}
