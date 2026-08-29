package com.tokit.domain.wallet.controller;

import com.tokit.support.TestAuthPrincipalResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.domain.wallet.dto.WalletResponse;
import com.tokit.domain.wallet.service.WalletService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WalletControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private WalletController walletController;

    @Mock
    private WalletService walletService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private WalletResponse mockWalletResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(walletController)
                .setCustomArgumentResolvers(new TestAuthPrincipalResolver(1L, "wallet.user@tokit.com"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockWalletResponse = new WalletResponse(
                10L, 1L, "KRW", new BigDecimal("1500000"), BigDecimal.ZERO
        );
    }


    @Test
    @DisplayName("POST /api/wallets/deposit: X-Idempotency-Key와 함께 예치금 충전 시 성공 및 HTTP 200을 반환한다.")
    void depositKrw_Success() throws Exception {
        // Given
        WalletController.WalletAmountRequest request = new WalletController.WalletAmountRequest(new BigDecimal("1000000")
        );

        when(walletService.depositKrw(eq(1L), any())).thenReturn(mockWalletResponse);

        // When & Then
        mockMvc.perform(post("/api/wallets/deposit")
                        .header("X-Idempotency-Key", "uuid-v4-deposit-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.balance").value(1500000));
    }

    @Test
    @DisplayName("POST /api/wallets/deposit: 0원 이하 금액 충전 시 Valid 검증에 걸려 HTTP 400을 반환한다.")
    void depositKrw_ValidationError_Returns400() throws Exception {
        // Given: 음수 금액(-10,000원)
        WalletController.WalletAmountRequest request = new WalletController.WalletAmountRequest(new BigDecimal("-10000")
        );

        // When & Then
        mockMvc.perform(post("/api/wallets/deposit")
                        .header("X-Idempotency-Key", "uuid-v4-deposit-key-02")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /api/wallets/withdraw: X-Idempotency-Key와 함께 예치금 출금 시 성공 및 HTTP 200을 반환한다.")
    void withdrawKrw_Success() throws Exception {
        // Given
        WalletController.WalletAmountRequest request = new WalletController.WalletAmountRequest(new BigDecimal("200000")
        );

        when(walletService.withdrawKrw(eq(1L), any())).thenReturn(mockWalletResponse);

        // When & Then
        mockMvc.perform(post("/api/wallets/withdraw")
                        .header("X-Idempotency-Key", "uuid-v4-withdraw-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }
}
