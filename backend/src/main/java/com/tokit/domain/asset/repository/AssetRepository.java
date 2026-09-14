package com.tokit.domain.asset.repository;

import com.tokit.domain.asset.entity.Asset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findBySymbol(String symbol);
    Optional<Asset> findByContractAddress(String contractAddress);
    List<Asset> findByIssuer_Id(Long issuerId);

    /**
     * 종목 행에 배타 락을 걸어 해당 종목의 매칭을 직렬화한다.
     *
     * <p>매칭은 "활성 주문 조회 → 체결 수량 계산 → 잔량 갱신"의 read-modify-write이므로,
     * 두 스레드가 같은 잔량을 읽으면 같은 매도 주문이 중복 체결되어 없는 토큰이 팔린다.
     *
     * <p>Redis 분산 락 대신 DB 행 락을 쓰는 이유는 락의 수명이 트랜잭션 커밋과 정확히
     * 일치하기 때문이다. 애플리케이션 레벨 락은 메서드가 끝날 때 풀리는데 그 시점은 커밋
     * 이전이라, 락을 놓은 직후 다른 스레드가 아직 커밋되지 않은 잔량을 읽는 창이 남는다.
     *
     * <p>락 단위가 종목이므로 서로 다른 종목의 매칭은 그대로 병렬로 진행된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Asset a WHERE a.symbol = :symbol")
    Optional<Asset> findBySymbolForUpdate(@Param("symbol") String symbol);
}
