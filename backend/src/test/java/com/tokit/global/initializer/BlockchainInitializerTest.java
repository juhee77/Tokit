package com.tokit.global.initializer;

import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.user.repository.UserRepository;
import com.tokit.domain.wallet.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BlockchainInitializerTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Test
    @DisplayName("서버 초기화 시 대규모 모의 투자자 100명 및 STO 자산 100개가 성공적으로 생성된다.")
    void testBulkInitialization() {
        long userCount = userRepository.count();
        long assetCount = assetRepository.count();
        long walletCount = walletRepository.count();

        assertThat(userCount).isGreaterThanOrEqualTo(100);
        assertThat(assetCount).isGreaterThanOrEqualTo(100);
        assertThat(walletCount).isGreaterThanOrEqualTo(200); 
    }
}
