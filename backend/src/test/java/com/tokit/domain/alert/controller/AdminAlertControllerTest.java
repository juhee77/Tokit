package com.tokit.domain.alert.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAlertControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private AdminAlertController adminAlertController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminAlertController).build();
    }

    @Test
    @DisplayName("GET /api/admin/alerts/subscribe: 실시간 SSE 알림 채널 구독 시 HTTP 200 및 SseEmitter를 반환한다.")
    void subscribe_ReturnsSseEmitter() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/admin/alerts/subscribe"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("broadcastAlert: 어드민 백오피스 구독자들에게 대사 정합성 오차 알림을 성공적으로 브로드캐스트한다.")
    void broadcastAlert_SendsEventsToEmitters() {
        // Given: SseEmitter 구독 등록
        SseEmitter emitter = adminAlertController.subscribe();
        assertThat(emitter).isNotNull();

        // When & Then: 적색 토스트 알림 브로드캐스팅 시 예외 없이 실행됨
        adminAlertController.broadcastAlert(
                "CRITICAL_RECONCILIATION_DISCREPANCY",
                "유저 ID: 1 - RDBMS 원장(10,000)과 온체인 원장(9,000) 잔액 불일치 감지!"
        );
    }
}
