package com.tokit.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlywayConfigTest {

    @Mock
    private ConfigurableListableBeanFactory beanFactory;

    @Mock
    private BeanDefinition beanDefinition;

    @Test
    @DisplayName("entityManagerFactoryDependsOnFlywayPostProcessor: JPA EntityManagerFactory가 Flyway 마이그레이션 빈에 항상 의존하도록 dependsOn을 설정한다.")
    void entityManagerFactoryDependsOnFlywayPostProcessor_SetsDependsOnFlyway() {
        // Given
        when(beanFactory.getBeanDefinition("entityManagerFactory")).thenReturn(beanDefinition);
        when(beanDefinition.getDependsOn()).thenReturn(null);

        BeanFactoryPostProcessor postProcessor = FlywayConfig.entityManagerFactoryDependsOnFlywayPostProcessor();

        // When
        postProcessor.postProcessBeanFactory(beanFactory);

        // Then
        verify(beanDefinition, times(1)).setDependsOn(any());
    }
}
