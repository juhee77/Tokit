package com.tokit.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigTest {

    private SwaggerConfig swaggerConfig;

    @BeforeEach
    void setUp() {
        swaggerConfig = new SwaggerConfig();
    }

    @Test
    @DisplayName("openAPI: Swagger API 명세서의 제목, 버전(1.0.0) 및 BearerAuth 보안 체계가 구성된다.")
    void openAPI_ConfiguresMetadataAndSecuritySchemes() {
        // When
        OpenAPI openAPI = swaggerConfig.openAPI();

        // Then
        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("TOKIT STO Matching Engine & Exchange API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("1.0.0");
        assertThat(openAPI.getComponents().getSecuritySchemes()).containsKey("BearerAuth");
    }
}
