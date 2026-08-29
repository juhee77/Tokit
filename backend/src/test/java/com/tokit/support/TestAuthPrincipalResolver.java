package com.tokit.support;

import com.tokit.domain.user.entity.Role;
import com.tokit.global.security.AuthUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * standaloneSetup MockMvc는 Spring Security 필터를 거치지 않으므로
 * @AuthenticationPrincipal이 비어 있습니다. 테스트에서 고정된 인증 주체를 주입합니다.
 */
public class TestAuthPrincipalResolver implements HandlerMethodArgumentResolver {

    private final AuthUser authUser;

    public TestAuthPrincipalResolver(Long userId, String email) {
        this(userId, email, Role.USER);
    }

    public TestAuthPrincipalResolver(Long userId, String email, Role role) {
        this.authUser = new AuthUser(userId, email, role);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                && AuthUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return authUser;
    }
}
