package com.tokit.domain.reconciliation.repository;

import com.tokit.domain.reconciliation.entity.ReconciliationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconciliationLogRepository extends JpaRepository<ReconciliationLog, Long> {
    List<ReconciliationLog> findByUserId(Long userId);
    List<ReconciliationLog> findByAssetId(Long assetId);
}
