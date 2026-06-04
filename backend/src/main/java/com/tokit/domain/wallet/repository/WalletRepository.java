package com.tokit.domain.wallet.repository;

import com.tokit.domain.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    // 원화(KRW) 지갑 조회를 위한 메서드 (asset_id IS NULL)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId AND w.asset IS NULL")
    Optional<Wallet> findKrwWalletByUserIdWithPessimisticLock(@Param("userId") Long userId);

    // 특정 토큰 자산 지갑 조회를 위한 메서드
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId AND w.asset.id = :assetId")
    Optional<Wallet> findAssetWalletByUserIdAndAssetIdWithPessimisticLock(@Param("userId") Long userId, @Param("assetId") Long assetId);
}
