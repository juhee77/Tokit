package com.tokit.global.security;

import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.isValid(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            Long userId = jwtTokenProvider.parseUserId(token);
            // 토큰 발급 이후 삭제된 계정의 토큰을 거르기 위해 실제 존재 여부를 확인합니다.
            User user = userRepository.findById(userId).orElse(null);

            if (user != null) {
                AuthUser principal = new AuthUser(user.getId(), user.getEmail(), user.getRole());
                // 권한은 DB의 현재 값을 사용합니다. 토큰에 담아두면 권한 회수가 만료 시점까지 지연됩니다.
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
