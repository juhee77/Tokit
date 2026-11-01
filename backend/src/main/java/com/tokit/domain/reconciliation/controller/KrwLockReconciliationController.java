package com.tokit.domain.reconciliation.controller;

import com.tokit.domain.reconciliation.dto.KrwLockMismatch;
import com.tokit.domain.reconciliation.dto.KrwLockSettlementReport;
import com.tokit.domain.reconciliation.service.KrwLockReconciliationService;
import com.tokit.global.dto.ApiResponse;
import com.tokit.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 매수 홀딩 잔여 정산 (#27). {@code /api/admin/**}이므로 ADMIN만 호출할 수 있다. */
@Tag(name = "08. Reconciliation (온-오프체인 데이터 대사)", description = "온-오프체인 잔고 대조 및 배치 실행 API")
@RestController
@RequestMapping("/api/admin/reconciliation/krw-locks")
@RequiredArgsConstructor
public class KrwLockReconciliationController {

    private final KrwLockReconciliationService krwLockReconciliationService;

    @GetMapping
    @Operation(summary = "매수 홀딩 불일치 조회(드라이런)",
            description = "KRW 지갑의 홀딩 잔고와 활성 매수 주문 잔량 홀딩액 합이 다른 지갑을 조회합니다. 아무것도 바꾸지 않습니다.")
    public ResponseEntity<ApiResponse<List<KrwLockMismatch>>> preview() {
        return ResponseEntity.ok(ApiResponse.success(krwLockReconciliationService.preview()));
    }

    @PostMapping("/settle")
    @Operation(summary = "매수 홀딩 잔여 정산",
            description = "잘못 묶인 홀딩 잔여를 가용 잔고로 돌려주고 내역을 남깁니다. 홀딩이 모자란 지갑은 정산하지 않고 보고만 합니다.")
    public ResponseEntity<ApiResponse<KrwLockSettlementReport>> settle(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.success(krwLockReconciliationService.settleAll(authUser.id())));
    }
}
