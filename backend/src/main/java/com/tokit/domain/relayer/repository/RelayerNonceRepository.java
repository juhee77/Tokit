package com.tokit.domain.relayer.repository;

import com.tokit.domain.relayer.entity.RelayerNonce;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RelayerNonceRepository extends JpaRepository<RelayerNonce, String> {

    /** 같은 지갑의 논스 검증과 증가를 직렬화하기 위해 행을 쓰기 잠금으로 조회합니다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM RelayerNonce n WHERE n.walletAddress = :walletAddress")
    Optional<RelayerNonce> findByWalletAddressWithPessimisticLock(@Param("walletAddress") String walletAddress);
}
