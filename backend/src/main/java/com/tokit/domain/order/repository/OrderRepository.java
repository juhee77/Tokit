package com.tokit.domain.order.repository;

import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser_Id(Long userId);
    List<Order> findByAsset_Symbol(String symbol);
    List<Order> findByAsset_SymbolAndStatusIn(String symbol, List<OrderStatus> statuses);
}
