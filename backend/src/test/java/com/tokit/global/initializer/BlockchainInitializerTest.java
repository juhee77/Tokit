package com.tokit.global.initializer;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.infra.blockchain.ContractService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BlockchainInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private AssetRepository assetRepository;
    @Mock private ContractService contractService;

    @InjectMocks
    private BlockchainInitializer blockchainInitializer;

    @Test
    @DisplayName("테스트 환경(JUnit 감지) 시에는 기본적으로 벌크 데이터 생성을 건너뛴다.")
    void testSkipInTestProfile() throws Exception {
        // Given
        blockchainInitializer.forceExecute = false;

        // When
        blockchainInitializer.run();

        // Then
        verify(userRepository, never()).findAll();
        verify(userRepository, never()).save(any());
    }

    private Asset asset(String symbol, String contractAddress) {
        return Asset.builder().symbol(symbol).name(symbol).contractAddress(contractAddress)
                .totalSupply(BigDecimal.ONE).issuePrice(BigDecimal.ONE).status("거래중").build();
    }

    @Test
    @DisplayName("예전 시드의 가짜 컨트랙트 주소만 기본 컨트랙트 주소로 바꾸고, 실제 주소는 건드리지 않는다.")
    void repointsOnlySeedMockAddresses() {
        String defaultAddress = "0x5FbDB2315678afecb367f032d93F642f64180aa3";
        Asset mock1 = asset("STO-001", "0x00000000000000000000000000000000000003e9");
        Asset mock2 = asset("STO-100", "0x000000000000000000000000000000000000044c");
        Asset real = asset("HDYT", "0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512");
        when(contractService.getDefaultContractAddress()).thenReturn(defaultAddress);
        when(assetRepository.findAll()).thenReturn(List.of(mock1, mock2, real));

        blockchainInitializer.repointSeedAssetsToDefaultContract();

        assertThat(mock1.getContractAddress()).isEqualTo(defaultAddress);
        assertThat(mock2.getContractAddress()).isEqualTo(defaultAddress);
        assertThat(real.getContractAddress()).isEqualTo("0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512");
    }
}
