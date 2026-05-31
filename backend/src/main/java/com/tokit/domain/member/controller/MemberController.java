package com.tokit.domain.member.controller;

import com.tokit.domain.member.entity.Member;
import com.tokit.domain.member.service.MemberService;
import com.tokit.global.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    public record SignUpRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Wallet Address is required") String walletAddress
    ) {}

    public record MemberResponse(
        Long id,
        String email,
        String name,
        String walletAddress
    ) {
        public static MemberResponse from(Member member) {
            return new MemberResponse(member.getId(), member.getEmail(), member.getName(), member.getWalletAddress());
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signUp(@RequestBody @Valid SignUpRequest request) {
        Member member = memberService.signUp(request.email(), request.name(), request.walletAddress());
        return ResponseEntity.ok(ApiResponse.success(MemberResponse.from(member)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MemberResponse>> getMember(@PathVariable("id") Long id) {
        Member member = memberService.getMemberById(id);
        return ResponseEntity.ok(ApiResponse.success(MemberResponse.from(member)));
    }
}
