# 🏛️ TOKIT STO — API & Development Guidelines

이 문서는 **TOKIT 토큰증권(STO) 매칭 엔진 및 거래소** 플랫폼 개발을 위한 API 표준, 개발 규칙, DTO 및 Controller 템플릿에 대한 가이드라인을 정의합니다. 

모든 신규 기능 추가 또는 도메인 리팩토링 시 아래 규칙을 준수하여 작성해야 합니다.

---

## 📌 1. API 개발 표준 규칙 (OpenAPI / Swagger)

### 1) 모든 Controller 및 API 엔드포인트는 어노테이션 필수
- **Controller 클래스**: `@Tag(name = "[도메인명]", description = "[도메인 설명]")`을 부여합니다.
- **메서드(엔드포인트)**: `@Operation(summary = "[기능 요약]", description = "[상세 동작 설명]")`을 명시합니다.
- **예외 응답 정의**: 특정 예외 응답(예: 400 Bad Request, 404 Not Found)이 예상될 경우 `@ApiResponses`를 통해 명세화합니다.

### 2) 공통 응답 구조 (`ApiResponse<T>`) 적용
- 모든 REST API 응답은 `com.tokit.global.dto.ApiResponse`로 래핑하여 리턴합니다.
- HTTP 상태코드와 `ApiResponse` 내부의 `status`를 일치시킵니다.
- **성공 응답 템플릿**:
  ```json
  {
    "status": 200,
    "message": "SUCCESS",
    "data": { ... }
  }
  ```
- **실패 응답 템플릿**:
  ```json
  {
    "status": 400,
    "message": "Insufficient token balance",
    "data": null
  }
  ```

### 3) 요청 검증 (Request Validation)
- 모든 Request Body DTO는 Java의 `record`를 사용하여 설계합니다.
- 불변성(Immutability)을 확보하고 입력값 검증용 어노테이션(`@NotBlank`, `@NotNull`, `@Positive`, `@Email` 등)을 추가합니다.
- Controller의 파라미터에는 `@Valid`를 반드시 선언하여 유효성 필터링을 활성화합니다.

---

## 🗂️ 2. Controller & DTO 표준 코드 템플릿

새로운 REST API나 도메인 서비스를 작성할 때는 아래 코드를 복사하여 템플릿으로 사용하십시오.

```java
package com.tokit.domain.example.controller;

import com.tokit.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "00. Example Domain (예시 도메인)", description = "도메인 개발 표준 예시 API")
@RestController
@RequestMapping("/api/examples")
@RequiredArgsConstructor
public class ExampleController {

    // --- Request DTO (Record 사용) ---
    public record CreateExampleRequest(
        @NotBlank(message = "Name is required") 
        String name,
        
        @NotNull(message = "Quantity is required") 
        Integer quantity
    ) {}

    // --- Response DTO (Record 사용) ---
    public record ExampleResponse(
        Long id,
        String name,
        Integer quantity
    ) {
        public static ExampleResponse from(Object entity) {
            // 엔티티를 DTO로 변환하는 정적 팩토리 메서드 작성
            return new ExampleResponse(1L, "Sample", 100);
        }
    }

    @PostMapping
    @Operation(summary = "샘플 리소스 생성", description = "입력 데이터를 기반으로 신규 샘플 리소스를 저장합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200", 
        description = "성공적으로 생성됨"
    )
    public ResponseEntity<ApiResponse<ExampleResponse>> createExample(
            @RequestBody @Valid CreateExampleRequest request
    ) {
        // 비즈니스 로직 연동
        ExampleResponse response = new ExampleResponse(1L, request.name(), request.quantity());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
```

---

## 🔄 3. 실시간 스트리밍 명세 (STOMP WebSocket & SSE)

### 1) STOMP WebSocket (호가창 전송)
- **주소**: `ws://localhost:8080/ws-tokit` (SockJS 연결 가능)
- **호가창 구독(Subscribe) Destination**: `/topic/orderbook/{symbol}`
- **데이터 포맷**:
  ```json
  {
    "symbol": "APPL-STO",
    "bids": [
      { "price": 150000, "quantity": 120.5 }
    ],
    "asks": [
      { "price": 151000, "quantity": 80.0 }
    ]
  }
  ```

### 2) SSE (체결 내역 스트리밍)
- **주소**: `GET /api/trades/subscribe/{symbol}`
- **스트림 종류**: `text/event-stream`
- **이벤트 이름**: `TRADE`
- **데이터 포맷**:
  ```json
  {
    "id": 1,
    "buyOrderId": 23,
    "sellOrderId": 24,
    "assetSymbol": "APPL-STO",
    "price": 150000.0,
    "quantity": 10.0,
    "createdAt": "2026-05-31T21:00:00"
  }
  ```

---

## 🛡️ 4. 블록체인 거래소 컴플라이언스 규칙 (Solidity ERC-1400)
- **규정 준수**: 모든 주식/증권 거래자는 컨트랙트 상의 `isWhitelisted` 매핑에 사전 등록(화이트리스트 등록)되어야만 합니다.
- **파티션 분할**: 토큰 전송은 파티션 단위(`DEFAULT` 등)로 추적되며, 반드시 `transferByPartition` 함수를 통해 화이트리스트 검증을 통과해야만 전송을 완료할 수 있습니다.
