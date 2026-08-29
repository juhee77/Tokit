package com.tokit.domain.user.controller;

import com.tokit.domain.user.dto.MyPageResponse;
import com.tokit.domain.user.service.MyPageService;
import com.tokit.global.dto.ApiResponse;
import com.tokit.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01. User (사용자)", description = "사용자 관리 및 마이페이지 통합 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;

    @GetMapping("/me/mypage")
    @Operation(summary = "마이페이지 통합 조회", description = "로그인한 사용자의 프로필 정보, 원화 및 토큰 지갑 잔액, 주문 내역, 체결 내역을 일괄 조회합니다.")
    public ResponseEntity<ApiResponse<MyPageResponse>> getMyPage(@AuthenticationPrincipal AuthUser authUser) {
        MyPageResponse response = myPageService.getMyPageData(authUser.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
