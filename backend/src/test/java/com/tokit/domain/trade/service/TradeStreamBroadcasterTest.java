package com.tokit.domain.trade.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * 다중 구독자 환경에서 체결 스트리밍이 유실·누수 없이 동작하는지 검증한다.
 *
 * <p>스트리밍은 실패해도 조용하다. 구독자가 이벤트를 못 받아도 예외가 어디에도 남지 않고,
 * 사용자는 화면이 갱신되지 않는 것만 본다. 그래서 구독자 목록 관리와 전파 격리를
 * 명시적으로 검증한다.
 */
class TradeStreamBroadcasterTest {

    private static final String SYMBOL = "STREAM-TEST";

    private TradeStreamBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new TradeStreamBroadcaster();
    }

    @Test
    @DisplayName("구독자 전원이 동일한 체결 이벤트를 받는다.")
    void allSubscribersReceiveEvent() {
        for (int i = 0; i < 5; i++) {
            broadcaster.subscribe(SYMBOL);
        }

        var result = broadcaster.broadcast(SYMBOL, "TRADE", "1", "payload");

        assertSoftly(softly -> {
            softly.assertThat(result.delivered()).as("구독자 5명 전원에게 전달되어야 한다").isEqualTo(5);
            softly.assertThat(result.dropped()).as("끊긴 구독자가 없어야 한다").isZero();
            softly.assertThat(broadcaster.subscriberCount(SYMBOL)).isEqualTo(5);
        });
    }

    @Test
    @DisplayName("끊긴 구독자가 섞여 있어도 나머지 구독자는 이벤트를 받는다.")
    void brokenSubscriberMustNotBlockOthers() {
        SseEmitter first = broadcaster.subscribe(SYMBOL);
        SseEmitter broken = broadcaster.subscribe(SYMBOL);
        SseEmitter last = broadcaster.subscribe(SYMBOL);

        // 클라이언트가 끊긴 상황. 정리 콜백은 비동기로 실행되므로, 완료되었지만 아직
        // 목록에 남아 있는 구독자가 실제로 존재한다. 이때 send는 IllegalStateException을
        // 던지며, 이는 검사 예외가 아니라 IOException만 잡는 코드로는 막히지 않는다.
        broken.complete();

        var result = broadcaster.broadcast(SYMBOL, "TRADE", "1", "payload");

        assertSoftly(softly -> {
            softly.assertThat(result.delivered())
                    .as("끊긴 구독자 뒤에 있는 구독자도 이벤트를 받아야 한다")
                    .isEqualTo(2);
            softly.assertThat(result.dropped()).as("끊긴 구독자는 정리되어야 한다").isEqualTo(1);
            softly.assertThat(broadcaster.subscriberCount(SYMBOL))
                    .as("끊긴 구독자가 목록에 남으면 이후 전파를 계속 방해한다")
                    .isEqualTo(2);
        });
        assertThat(List.of(first, last)).doesNotContain(broken);
    }

    @Test
    @DisplayName("마지막 구독자 정리와 새 구독이 동시에 일어나도 새 구독자가 유실되지 않는다.")
    void concurrentCleanupMustNotDropNewSubscriber() throws Exception {
        // 구독자 목록이 비면 맵에서 종목 항목을 지운다. 비었는지 확인하는 시점과 지우는
        // 시점이 나뉘어 있으면, 그 사이에 등록한 구독자가 함께 사라져 어떤 이벤트도 받지
        // 못한다. 끊긴 구독자 정리(broadcast 경로)와 신규 구독을 동시에 반복해 그 창이
        // 없는지 확인한다.
        int rounds = 300;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> lostRounds = new ArrayList<>();

        try {
            for (int round = 0; round < rounds; round++) {
                broadcaster = new TradeStreamBroadcaster();

                // 이미 끊긴 구독자 하나만 등록해 둔다. broadcast가 이를 정리하면서
                // 목록이 비고, 맵에서 종목 항목이 지워지는 경로를 탄다.
                SseEmitter broken = broadcaster.subscribe(SYMBOL);
                broken.complete();

                CountDownLatch start = new CountDownLatch(1);
                CountDownLatch done = new CountDownLatch(2);

                pool.submit(() -> {
                    try {
                        start.await();
                        broadcaster.broadcast(SYMBOL, "TRADE", "1", "x");  // -> 정리 발생
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
                pool.submit(() -> {
                    try {
                        start.await();
                        broadcaster.subscribe(SYMBOL);                      // -> 신규 등록
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });

                start.countDown();
                done.await(5, TimeUnit.SECONDS);

                // 신규 구독자는 반드시 남아 있어야 한다. 0이면 등록과 동시에 유실된 것이다.
                if (broadcaster.subscriberCount(SYMBOL) == 0) {
                    lostRounds.add(round);
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(lostRounds)
                .as("신규 구독자가 유실된 라운드가 있다 (총 %d회 중 %d회)", rounds, lostRounds.size())
                .isEmpty();
    }
}
