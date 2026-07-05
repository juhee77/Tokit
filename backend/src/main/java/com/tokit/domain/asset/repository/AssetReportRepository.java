package com.tokit.domain.asset.repository;

import com.tokit.domain.asset.entity.AssetReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetReportRepository extends JpaRepository<AssetReport, Long> {
    List<AssetReport> findByAsset_IdOrderByCreatedAtDesc(Long assetId);
}
