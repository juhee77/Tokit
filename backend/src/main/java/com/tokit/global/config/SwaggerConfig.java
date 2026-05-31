package com.tokit.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        // Swagger UI에서 JWT 인증을 테스트할 수 있도록 SecurityScheme 설정
        String securitySchemeName = "BearerAuth";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(securitySchemeName);
        Components components = new Components().addSecuritySchemes(securitySchemeName,
                new SecurityScheme()
                        .name(securitySchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
        );

        return new OpenAPI()
                .info(new Info()
                        .title("TOKIT STO Matching Engine & Exchange API")
                        .description("토큰증권(STO) 매칭 엔진 및 실시간 거래소 플랫폼 백엔드 API 문서")
                        .version("1.0.0")
                )
                .addSecurityItem(securityRequirement)
                .components(components);
    }
}
