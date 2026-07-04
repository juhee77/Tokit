package com.tokit.domain.dividend.repository;

import com.tokit.domain.dividend.entity.DividendPayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DividendPayoutRepository extends JpaRepository<DividendPayout, Long> {
    List<DividendPayout> findByAsset_IdOrderByPayoutDateDesc(Long assetId);
}
