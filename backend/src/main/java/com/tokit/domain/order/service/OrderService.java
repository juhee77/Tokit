package com.tokit.domain.order.service;

import com.tokit.domain.asset.service.AssetService;
import com.tokit.domain.user.service.UserService;
import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderType;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.order.entity.OrderStatus;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.wallet.entity.Wallet;
import com.tokit.domain.wallet.repository.WalletRepository;
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
    private final UserService userService;
    private final AssetService assetService;
    private final WalletRepository walletRepository;
    private final OrderEventPublisher orderEventPublisher;

    @Transactional
    public Order placeOrder(Long userId, String assetSymbol, OrderType orderType, BigDecimal price, BigDecimal quantity) {
        // 회원 및 자산 유효성 검증
        User user = userService.getUserById(userId);
        Asset asset = assetService.getAssetBySymbol(assetSymbol);

        // 예치금 홀딩 (비관적 락)
        if (orderType == OrderType.BUY) {
            Wallet krwWallet = walletRepository.findKrwWalletByUserIdWithPessimisticLock(userId)
                    .orElseThrow(() -> new IllegalArgumentException("KRW 지갑을 찾을 수 없습니다."));
            BigDecimal totalAmount = price.multiply(quantity);
            if (krwWallet.getBalance().compareTo(totalAmount) < 0) {
                throw new IllegalArgumentException("매수 주문을 위한 예치금이 부족합니다.");
            }
            krwWallet.updateBalance(krwWallet.getBalance().subtract(totalAmount), krwWallet.getLockedBalance().add(totalAmount));
        } else {
            Wallet assetWallet = walletRepository.findAssetWalletByUserIdAndAssetIdWithPessimisticLock(userId, asset.getId())
                    .orElseThrow(() -> new IllegalArgumentException("매도할 자산 지갑을 찾을 수 없습니다."));
            if (assetWallet.getBalance().compareTo(quantity) < 0) {
                throw new IllegalArgumentException("매도 주문을 위한 자산 잔고가 부족합니다.");
            }
            assetWallet.updateBalance(assetWallet.getBalance().subtract(quantity), assetWallet.getLockedBalance().add(quantity));
        }

        // 주문 생성 및 저장
        Order order = Order.builder()
                .user(user)
                .asset(asset)
                .type(orderType)
                .price(price)
                .quantity(quantity)
                .remainQty(quantity)
                .status(OrderStatus.OPEN)
                .build();
        
        orderRepository.save(order);

        // RabbitMQ 주문 등록 이벤트 발행
        OrderEvent event = new OrderEvent(
                order.getId(),
                order.getUserId(),
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

    public List<Order> getOrdersByUser(Long userId) {
        return orderRepository.findByUserId(userId);
    }
}
