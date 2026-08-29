package com.tokit.domain.kyc.repository;

import com.tokit.domain.kyc.entity.KycVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KycVerificationRepository extends JpaRepository<KycVerification, Long> {

    List<KycVerification> findByUser_IdOrderByVerifiedAtDesc(Long userId);

    Optional<KycVerification> findFirstByUser_IdOrderByVerifiedAtDesc(Long userId);
}
