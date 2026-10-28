package com.tokit.domain.order.repository;

import com.tokit.domain.order.entity.FailedOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FailedOrderEventRepository extends JpaRepository<FailedOrderEvent, Long> {

    /** 같은 주문의 아직 종결되지 않은 실패 기록. 재발행 후 다시 실패하면 이 행을 갱신한다. */
    Optional<FailedOrderEvent> findFirstByOrderIdAndStatusNot(Long orderId, FailedOrderEvent.Status status);

    List<FailedOrderEvent> findByStatusOrderByLastFailedAtDesc(FailedOrderEvent.Status status);

    List<FailedOrderEvent> findAllByOrderByLastFailedAtDesc();
}
