package com.tokit.infra.alert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SlackAlertServiceTest {

    @Test
    @DisplayName("Webhook URL이 없는 경우 에러 없이 warn 로깅으로 정상 대체되어야 한다.")
    void sendAlert_NoWebhookUrl_FallbackGracefully() {
        // Given
        SlackAlertService alertService = new SlackAlertService("");

        // When & Then
        assertDoesNotThrow(() -> {
            alertService.sendAlert("TEST TITLE", "TEST MESSAGE");
        });
    }
}
