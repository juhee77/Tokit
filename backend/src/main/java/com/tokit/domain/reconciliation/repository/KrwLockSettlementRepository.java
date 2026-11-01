package com.tokit.domain.reconciliation.repository;

import com.tokit.domain.reconciliation.entity.KrwLockSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KrwLockSettlementRepository extends JpaRepository<KrwLockSettlement, Long> {
    List<KrwLockSettlement> findByUserId(Long userId);
}
