package com.tokit.domain.user.service;

import com.tokit.domain.user.dto.MyPageResponse;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.user.controller.UserController.UserResponse;
import com.tokit.domain.wallet.repository.WalletRepository;
import com.tokit.domain.wallet.dto.WalletResponse;
import com.tokit.domain.order.repository.OrderRepository;
import com.tokit.domain.order.controller.OrderController.OrderResponse;
import com.tokit.domain.trade.repository.TradeRepository;
import com.tokit.domain.trade.controller.TradeController.TradeResponse;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;

    public MyPageResponse getMyPageData(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<WalletResponse> wallets = walletRepository.findAllByUserId(userId).stream()
                .map(WalletResponse::from)
                .toList();

        List<OrderResponse> orders = orderRepository.findByUser_Id(userId).stream()
                .map(OrderResponse::from)
                .toList();

        List<TradeResponse> trades = tradeRepository.findByUserIdOrderByTradedAtDesc(userId).stream()
                .map(TradeResponse::from)
                .toList();

        return new MyPageResponse(
                UserResponse.from(user),
                wallets,
                orders,
                trades
        );
    }
}
