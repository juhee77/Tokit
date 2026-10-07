package com.tokit.domain.trade.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 종목별 SSE 구독자를 관리하고 체결 이벤트를 전파한다.
 *
 * <p>구독자 관리를 {@code TradeService}에서 분리한 이유는 두 가지다. 체결 정산과 스트리밍은
 * 관심사가 다르고, 무엇보다 <b>구독자 목록이 서비스 내부에 숨어 있으면 다중 구독자 동작을
 * 검증할 수 없다.</b> 구독자 수와 전파 결과를 외부에서 관찰할 수 있게 한다.
 */
@Component
@Slf4j
public class TradeStreamBroadcaster {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** 전파 결과. 몇 명에게 전달되고 몇 명이 끊겨 정리되었는지 알려준다. */
    public record BroadcastResult(int delivered, int dropped) {}

    public SseEmitter subscribe(String assetSymbol) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());

        // 등록도 compute 안에서 해야 한다. computeIfAbsent로 목록을 받아 바깥에서 add 하면,
        // 그 사이에 정리 스레드가 "비었다"고 판단해 목록을 맵에서 지울 수 있다. 그러면 방금
        // 등록한 구독자는 맵에 없는 목록에 들어가 어떤 이벤트도 받지 못한다.
        emitters.compute(assetSymbol, (key, current) -> {
            List<SseEmitter> subscribers = (current == null) ? new CopyOnWriteArrayList<>() : current;
            subscribers.add(emitter);
            return subscribers;
        });

        emitter.onCompletion(() -> unsubscribe(assetSymbol, emitter));
        emitter.onTimeout(() -> unsubscribe(assetSymbol, emitter));
        emitter.onError(e -> unsubscribe(assetSymbol, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("Connected to Trade Stream for " + assetSymbol));
        } catch (Exception e) {
            // 연결 직후 실패한 구독자는 목록에 남겨두면 이후 전파를 계속 방해한다.
            unsubscribe(assetSymbol, emitter);
        }
        return emitter;
    }

    /**
     * 해당 종목의 모든 구독자에게 이벤트를 전파한다.
     *
     * <p>구독자 한 명의 실패가 나머지 전파를 막아서는 안 된다. 이미 완료된 emitter에
     * {@code send}를 호출하면 {@code IllegalStateException}(검사 예외가 아님)이 던져지는데,
     * {@code IOException}만 잡으면 이 예외가 루프를 중단시켜 <b>뒤에 있는 구독자들이 이벤트를
     * 받지 못한다.</b> 끊긴 연결의 정리 콜백은 비동기로 실행되므로, 완료되었지만 아직 목록에
     * 남아 있는 구독자는 실제로 존재한다. 그래서 {@code Exception}을 구독자 단위로 격리한다.
     */
    public BroadcastResult broadcast(String assetSymbol, String eventName, String eventId, Object data) {
        List<SseEmitter> symbolEmitters = emitters.get(assetSymbol);
        if (symbolEmitters == null || symbolEmitters.isEmpty()) {
            return new BroadcastResult(0, 0);
        }

        int delivered = 0;
        int dropped = 0;
        for (SseEmitter emitter : symbolEmitters) {
            try {
                SseEmitter.SseEventBuilder event = SseEmitter.event().name(eventName).data(data);
                if (eventId != null) {
                    event = event.id(eventId);
                }
                emitter.send(event);
                delivered++;
            } catch (IOException | RuntimeException e) {
                log.debug("Dropping SSE subscriber for {}: {}", assetSymbol, e.getMessage());
                unsubscribe(assetSymbol, emitter);
                dropped++;
            }
        }
        return new BroadcastResult(delivered, dropped);
    }

    /** 현재 구독자 수. 0이면 종목 항목 자체가 없을 수도 있다. */
    public int subscriberCount(String assetSymbol) {
        List<SseEmitter> symbolEmitters = emitters.get(assetSymbol);
        return symbolEmitters == null ? 0 : symbolEmitters.size();
    }

    /**
     * 구독을 해제한다.
     *
     * <p>비어 있는 목록을 맵에서 제거할 때 {@code isEmpty()} 확인과 제거를 나누면,
     * 그 사이에 새 구독자가 같은 목록에 등록될 수 있다. 그러면 방금 등록한 구독자가 맵에서
     * 함께 사라져 <b>어떤 이벤트도 받지 못한다.</b> {@code compute}로 확인과 제거를 한 번에
     * 처리해 그 창을 없앤다.
     */
    private void unsubscribe(String assetSymbol, SseEmitter emitter) {
        emitters.compute(assetSymbol, (key, current) -> {
            if (current == null) {
                return null;
            }
            current.remove(emitter);
            return current.isEmpty() ? null : current;
        });
    }
}
