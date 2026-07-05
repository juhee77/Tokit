package com.tokit.domain.alert.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Tag(name = "09. Admin Alerts (어드민 알림)", description = "실시간 어드민 푸시 알림 API")
@RestController
@RequestMapping("/api/admin/alerts")
@Slf4j
public class AdminAlertController {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "실시간 알림 구독", description = "SSE(Server-Sent Events) 프로토콜을 통해 실시간 알림 채널을 구독합니다.")
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10 minutes timeout
        emitters.add(emitter);

        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(Map.of("message", "Subscription connected successfully")));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((ex) -> emitters.remove(emitter));

        return emitter;
    }

    public void broadcastAlert(String title, String message) {
        log.info("Broadcasting admin alert: {} - {}", title, message);
        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("ALERT")
                        .data(Map.of("title", title, "message", message)));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }
}
