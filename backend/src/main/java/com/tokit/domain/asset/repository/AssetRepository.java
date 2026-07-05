package com.tokit.domain.asset.repository;

import com.tokit.domain.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    Optional<Asset> findBySymbol(String symbol);
    Optional<Asset> findByContractAddress(String contractAddress);
    List<Asset> findByIssuer_Id(Long issuerId);
}
