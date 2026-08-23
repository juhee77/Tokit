package com.tokit.domain.dividend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.dividend.entity.DividendPayout;
import com.tokit.domain.dividend.service.DividendService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DividendControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private DividendController dividendController;

    @Mock
    private DividendService dividendService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private Asset testAsset;
    private DividendPayout testPayout;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(dividendController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testAsset = Asset.builder()
                .name("Gangnam Building STO")
                .symbol("GANGNAM-STO")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        setField(testAsset, "id", 10L);

        testPayout = DividendPayout.builder()
                .asset(testAsset)
                .totalDividendAmount(new BigDecimal("5000000"))
                .payoutDate(LocalDateTime.now())
                .status("COMPLETED")
                .build();
        setField(testPayout, "id", 1L);
    }

    @Test
    @DisplayName("POST /api/admin/dividends: 어드민이 배당금 500만원 등록 시 실행 성공 및 HTTP 200을 반환한다.")
    void createDividendPayout_Success() throws Exception {
        // Given
        DividendController.CreateDividendRequest request = new DividendController.CreateDividendRequest(
                10L, new BigDecimal("5000000")
        );

        when(dividendService.createDividendPayout(any(), any())).thenReturn(testPayout);

        // When & Then
        mockMvc.perform(post("/api/admin/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.assetSymbol").value("GANGNAM-STO"))
                .andExpect(jsonPath("$.data.totalDividendAmount").value(5000000));
    }

    @Test
    @DisplayName("POST /api/admin/dividends: 0원 이하 배당금 제출 시 Valid 검증으로 HTTP 400을 반환한다.")
    void createDividendPayout_ValidationError_Returns400() throws Exception {
        // Given: 음수 배당금(-500만원)
        DividendController.CreateDividendRequest request = new DividendController.CreateDividendRequest(
                10L, new BigDecimal("-5000000")
        );

        // When & Then
        mockMvc.perform(post("/api/admin/dividends")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /api/admin/dividends: 전체 배당 이력 조회가 성공하고 HTTP 200을 반환한다.")
    void getAllDividends_Success() throws Exception {
        // Given
        when(dividendService.getAllDividends()).thenReturn(List.of(testPayout));

        // When & Then
        mockMvc.perform(get("/api/admin/dividends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].assetSymbol").value("GANGNAM-STO"));
    }
}
