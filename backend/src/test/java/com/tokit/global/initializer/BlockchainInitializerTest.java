package com.tokit.global.initializer;

import com.tokit.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BlockchainInitializerTest {

    @Mock private UserRepository userRepository;

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
}
