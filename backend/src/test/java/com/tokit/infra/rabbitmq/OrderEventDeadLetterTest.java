package com.tokit.infra.rabbitmq;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.issuer.entity.Issuer;
import com.tokit.domain.issuer.repository.IssuerRepository;
import com.tokit.domain.matching.service.MatchingService;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.global.config.RabbitMQConfig;
import com.tokit.infra.alert.SlackAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * 매칭 실패 시 주문이 조용히 유실되지 않는지 검증한다.
 *
 * <p>주문은 접수 시점에 이미 예치금/자산을 홀딩한다. 따라서 매칭이 실패하면 주문은 체결도
 * 취소도 되지 않은 채 자금만 묶인 상태로 남는다. 체결(TradeEvent) 실패와 달리 이를 뒤늦게
 * 잡아줄 대사 배치도 없으므로, 실패는 반드시 사람에게 도달해야 한다.
 *
 * <p>검증 대상은 큐 깊이가 아니라 <b>운영자가 인지하는가</b>이다. DLQ에 메시지가 쌓이기만
 * 하고 아무도 보지 않으면 유실과 다를 바 없기 때문이다.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderEventDeadLetterTest {

    @Autowired private AmqpTemplate amqpTemplate;
    @Autowired private AmqpAdmin amqpAdmin;
    // 조회 호출 횟수가 곧 재시도 횟수다. 실제 구현은 그대로 두고 횟수만 센다.
    @MockitoSpyBean private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private IssuerRepository issuerRepository;

    /** 매칭을 항상 실패시켜 "처리 불가능한 주문" 상황을 만든다. */
    @MockitoBean private MatchingService matchingService;

    /** 알림 전송 여부가 이 테스트의 검증 대상이므로 실제 전송은 막는다. */
    @MockitoBean private SlackAlertService slackAlertService;

    @BeforeEach
    void setUp() {
        amqpAdmin.purgeQueue(RabbitMQConfig.ORDER_QUEUE_NAME, true);
        amqpAdmin.purgeQueue(RabbitMQConfig.ORDER_DLQ_NAME, true);
        doThrow(new IllegalStateException("매칭 엔진 장애 상황 재현"))
                .when(matchingService).matchOrder(any());
    }

    /** 실제로 DB에 존재하는 주문을 만든다. (매칭 단계까지 도달시키기 위함) */
    private Order persistOrder(OrderType type) {
        String uid = java.util.UUID.randomUUID().toString();
        Issuer issuer = issuerRepository.save(Issuer.builder()
                .companyName("DLQ Test Issuer").bizRegNo("dlq-" + uid).build());
        Asset asset = assetRepository.save(Asset.builder()
                .issuer(issuer).name("DLQ Test Asset").symbol("DLQ-" + uid.substring(0, 5))
                .totalSupply(BigDecimal.valueOf(1000)).issuePrice(BigDecimal.valueOf(10000))
                .status("거래중").contractAddress("0xDlqTestContract").build());
        User user = userRepository.save(User.builder()
                .name("DLQ Tester").email("dlq-" + uid + "@test.com")
                .password("{noop}test-password").walletAddress("0xDLQ" + uid.substring(0, 10))
                .kycStatus(true).build());

        return orderRepository.save(Order.builder()
                .user(user).asset(asset).type(type)
                .price(BigDecimal.valueOf(10000))
                .quantity(BigDecimal.ONE).remainQty(BigDecimal.ONE)
                .status(OrderStatus.OPEN).build());
    }

    private void publish(long orderId, OrderType type) {
        amqpTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ORDER_ROUTING_KEY,
                new OrderEvent(orderId, 1L, "STO-001", type,
                        BigDecimal.valueOf(10000), BigDecimal.ONE));
    }

    @Test
    @DisplayName("매칭이 반복 실패한 주문은 폐기되지 않고 운영자에게 알림으로 도달한다.")
    void failedOrderEventReachesOperator() {
        Order order = persistOrder(OrderType.BUY);
        long orderId = order.getId();
        publish(orderId, OrderType.BUY);

        // 복구에 필요한 주문 ID가 알림 본문에 포함되어야 한다.
        await().atMost(20, SECONDS).untilAsserted(() ->
                verify(slackAlertService, atLeastOnce()).sendAlert(
                        eq("주문 매칭 실패 (DLQ)"),
                        contains(String.valueOf(orderId))));
    }

    @Test
    @DisplayName("매칭 도중 실패한 주문은 포기 전에 여러 번 재시도된다.")
    void transientFailureIsRetriedBeforeGivingUp() {
        // 주문 행이 실제로 있어야 매칭 단계까지 도달한다.
        Order order = persistOrder(OrderType.SELL);
        publish(order.getId(), OrderType.SELL);

        // 한 번 실패하고 곧장 포기하면 락 대기나 커넥션 순단에도 주문이 죽는다.
        await().atMost(20, SECONDS).untilAsserted(() ->
                verify(matchingService, atLeast(2)).matchOrder(any()));
    }

    @Test
    @DisplayName("존재하지 않는 주문은 재시도하지 않고 단 한 번 만에 DLQ로 보낸다.")
    void permanentFailureSkipsRetry() {
        long orderId = 999_997L;
        publish(orderId, OrderType.BUY);

        await().atMost(20, SECONDS).untilAsserted(() ->
                verify(slackAlertService, atLeastOnce()).sendAlert(
                        eq("주문 매칭 실패 (DLQ)"), contains(String.valueOf(orderId))));

        // 다시 시도해도 결과가 달라지지 않는 실패에까지 재시도 예산(3회 × 최대 2초 백오프)을
        // 쓰면, 단일 컨슈머에서는 그 대기가 그대로 큐 전체의 처리량 상한이 된다.
        verify(orderRepository, times(1)).findByIdWithAsset(orderId);
        verify(matchingService, never()).matchOrder(any());
    }
}
