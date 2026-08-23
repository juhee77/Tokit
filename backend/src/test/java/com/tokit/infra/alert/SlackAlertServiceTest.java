package com.tokit.infra.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class SlackAlertServiceTest {

    @Test
    @DisplayName("sendAlert: webhookUrl이 비어 있을 경우 예외 없이 안전하게 경고 로그만 남기고 종료한다.")
    void sendAlert_BlankWebhookUrl_NoException() {
        // Given
        SlackAlertService service = new SlackAlertService("");

        // When & Then
        assertThatCode(() -> service.sendAlert("ALERT_TITLE", "Alert message content"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendAlert: null webhookUrl 설정 시 예외 발생 없이 안전하게 로그 처리된다.")
    void sendAlert_NullWebhookUrl_NoException() {
        // Given
        SlackAlertService service = new SlackAlertService(null);

        // When & Then
        assertThatCode(() -> service.sendAlert("CRITICAL_ERROR", "System alert test"))
                .doesNotThrowAnyException();
    }
}
