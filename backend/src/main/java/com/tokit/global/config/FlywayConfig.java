package com.tokit.global.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Value("${spring.flyway.url}")
    private String url;

    @Value("${spring.flyway.user}")
    private String user;

    @Value("${spring.flyway.password}")
    private String password;

    @Bean
    public Flyway flyway() {
        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    public static BeanFactoryPostProcessor entityManagerFactoryDependsOnFlywayPostProcessor() {
        return beanFactory -> {
            try {
                BeanDefinition emfb = beanFactory.getBeanDefinition("entityManagerFactory");
                String[] dependsOn = emfb.getDependsOn();
                if (dependsOn == null) {
                    dependsOn = new String[]{"flyway"};
                } else {
                    String[] newDependsOn = new String[dependsOn.length + 1];
                    System.arraycopy(dependsOn, 0, newDependsOn, 0, dependsOn.length);
                    newDependsOn[dependsOn.length] = "flyway";
                    dependsOn = newDependsOn;
                }
                emfb.setDependsOn(dependsOn);
            } catch (Exception e) {
                // Ignore if entityManagerFactory is not defined
            }
        };
    }
}
