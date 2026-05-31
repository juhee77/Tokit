package com.tokit.domain.order.service;

import com.tokit.domain.asset.service.AssetService;
import com.tokit.domain.member.service.MemberService;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import com.tokit.infra.rabbitmq.OrderEvent;
import com.tokit.infra.rabbitmq.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final MemberService memberService;
    private final AssetService assetService;
    private final OrderEventPublisher orderEventPublisher;

    @Transactional
    public Order placeOrder(Long memberId, String assetSymbol, OrderType orderType, BigDecimal price, BigDecimal quantity) {
        // 회원 및 자산 유효성 검증
        memberService.getMemberById(memberId);
        assetService.getAssetBySymbol(assetSymbol);

        // 주문 생성 및 저장
        Order order = Order.builder()
                .memberId(memberId)
                .assetSymbol(assetSymbol)
                .orderType(orderType)
                .price(price)
                .quantity(quantity)
                .build();
        
        orderRepository.save(order);

        // RabbitMQ 주문 등록 이벤트 발행
        OrderEvent event = new OrderEvent(
                order.getId(),
                order.getMemberId(),
                order.getAssetSymbol(),
                order.getOrderType(),
                order.getPrice(),
                order.getQuantity()
        );
        orderEventPublisher.publishOrder(event);

        return order;
    }

    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.cancel();
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    public List<Order> getOrdersByMember(Long memberId) {
        return orderRepository.findByMemberId(memberId);
    }
}
