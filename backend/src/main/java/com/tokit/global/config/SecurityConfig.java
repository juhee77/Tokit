package com.tokit.global.config;

import com.tokit.global.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** 인증 없이 접근 가능한 경로. 그 외 모든 요청은 유효한 액세스 토큰을 요구합니다. */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/login",
            "/api/users/signup",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/ws-tokit/**",
            // 관리 엔드포인트는 별도 포트(management.server.port)로 분리되어 있어
            // 공개 API 표면에 노출되지 않습니다. 외부에서는 방화벽으로 차단하세요.
            "/actuator/**"
    };

    /** 시세·호가처럼 로그인 없이도 열람 가능한 공개 시장 데이터 (조회 전용). */
    private static final String[] PUBLIC_MARKET_DATA = {
            "/api/assets/**",
            "/api/trades/**"
    };

    /** 운영자 전용 경로. 자산 발행, 배당 집행, 대사 이력, 계정 권한 변경이 여기 해당합니다. */
    private static final String[] ADMIN_ENDPOINTS = {
            "/api/admin/**",
            "/api/users/admin/**",
            "/api/kyc/admin/**",
            "/api/reconciliation/**",
            "/api/issuer/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                // 청약은 투자자 본인이 수행하므로 자산 등록(ADMIN)보다 먼저 매칭시킵니다.
                .requestMatchers(HttpMethod.POST, "/api/assets/*/subscribe").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/assets").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/assets/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/assets/**").hasRole("ADMIN")

                .requestMatchers(ADMIN_ENDPOINTS).hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, PUBLIC_MARKET_DATA).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler((request, response, denied) ->
                        response.sendError(HttpStatus.FORBIDDEN.value()))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
