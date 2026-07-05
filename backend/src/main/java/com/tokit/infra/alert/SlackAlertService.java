package com.tokit.infra.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
public class SlackAlertService {

    private final RestClient restClient;
    private final String webhookUrl;

    public SlackAlertService(
            @Value("${app.slack.webhook-url:}") String webhookUrl) {
        this.restClient = RestClient.create();
        this.webhookUrl = webhookUrl;
    }

    public void sendAlert(String title, String message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("[SLACK ALERT (NO WEBHOOK CONFIG)] TITLE: {}, MESSAGE: {}", title, message);
            return;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "text", String.format("*[%s]*\n%s", title, message)
            );

            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully sent Slack alert: {}", title);
        } catch (Exception e) {
            log.error("Failed to send Slack alert. Title: {}, Error: {}", title, e.getMessage(), e);
        }
    }
}
