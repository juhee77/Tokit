package com.tokit.domain.user.controller;

import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.service.UserService;
import com.tokit.global.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    public record SignUpRequest(
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Wallet Address is required") String walletAddress
    ) {}

    public record UserResponse(
        Long id,
        String email,
        String name,
        String walletAddress
    ) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getWalletAddress());
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signUp(@RequestBody @Valid SignUpRequest request) {
        User user = userService.signUp(request.email(), request.name(), request.walletAddress());
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable("id") Long id) {
        User user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)));
    }
}
