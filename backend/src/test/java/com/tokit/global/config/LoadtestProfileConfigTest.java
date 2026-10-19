package com.tokit.global.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("loadtest")
class LoadtestProfileConfigTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("loadtest 프로파일 활성화 시 SQL stdout 출력(show-sql, format_sql)이 비활성화되고 로그 레벨이 낮아진다.")
    void loadtestProfileDisablesSqlLogging() {
        assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.format_sql", Boolean.class)).isFalse();

        Logger tokitLogger = (Logger) LoggerFactory.getLogger("com.tokit");
        Logger hibernateSqlLogger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");

        assertThat(tokitLogger.getEffectiveLevel()).isEqualTo(Level.INFO);
        assertThat(hibernateSqlLogger.getEffectiveLevel()).isEqualTo(Level.WARN);
    }
}
