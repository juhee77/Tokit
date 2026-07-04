package com.tokit.domain.dividend.repository;

import com.tokit.domain.dividend.entity.DividendPayoutDetail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DividendPayoutDetailRepository extends JpaRepository<DividendPayoutDetail, Long> {
    List<DividendPayoutDetail> findByPayout_Id(Long payoutId);
}
