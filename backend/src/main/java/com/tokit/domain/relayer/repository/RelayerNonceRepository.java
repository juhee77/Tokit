package com.tokit.domain.relayer.repository;

import com.tokit.domain.relayer.entity.RelayerNonce;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelayerNonceRepository extends JpaRepository<RelayerNonce, String> {
}
