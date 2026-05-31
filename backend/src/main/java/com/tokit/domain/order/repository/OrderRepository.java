package com.tokit.domain.order.repository;

import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByMemberId(Long memberId);
    List<Order> findByAssetSymbol(String assetSymbol);
    List<Order> findByAssetSymbolAndStatusIn(String assetSymbol, List<OrderStatus> statuses);
}
