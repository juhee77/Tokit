package com.tokit.domain.asset.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.global.exception.BusinessException;
import com.tokit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;

    @Transactional
    public Asset registerAsset(String symbol, String name, String contractAddress, BigDecimal totalSupply) {
        if (assetRepository.findBySymbol(symbol).isPresent()) {
            throw new BusinessException("Symbol already exists", ErrorCode.INVALID_INPUT_VALUE);
        }
        Asset asset = Asset.builder()
                .symbol(symbol)
                .name(name)
                .contractAddress(contractAddress)
                .totalSupply(totalSupply)
                .build();
        return assetRepository.save(asset);
    }

    public List<Asset> getAllAssets() {
        return assetRepository.findAll();
    }

    public Asset getAssetBySymbol(String symbol) {
        return assetRepository.findBySymbol(symbol)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
    }
}
