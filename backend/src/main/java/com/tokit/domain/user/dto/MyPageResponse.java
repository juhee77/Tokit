package com.tokit.domain.user.dto;

import com.tokit.domain.user.controller.UserController.UserResponse;
import com.tokit.domain.wallet.dto.WalletResponse;
import com.tokit.domain.order.controller.OrderController.OrderResponse;
import com.tokit.domain.trade.controller.TradeController.TradeResponse;

import java.util.List;

public record MyPageResponse(
    UserResponse user,
    List<WalletResponse> wallets,
    List<OrderResponse> orders,
    List<TradeResponse> trades
) {}
