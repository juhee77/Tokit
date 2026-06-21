package com.tokit.domain.order.repository;

import com.tokit.domain.order.entity.Order;
import com.tokit.domain.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUser_Id(Long userId);
    List<Order> findByAsset_Symbol(String symbol);
    List<Order> findByAsset_SymbolAndStatusIn(String symbol, List<OrderStatus> statuses);

    @Query("select o from Order o join fetch o.asset where o.id = :id")
    Optional<Order> findByIdWithAsset(@Param("id") Long id);
}
