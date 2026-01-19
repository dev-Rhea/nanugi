package com.payment.authservice.controller;

import com.payment.authservice.dto.AuthResponse;
import com.payment.authservice.dto.KakaoLoginRequest;
import com.payment.authservice.dto.LogoutRequest;
import com.payment.authservice.dto.RefreshTokenRequest;
import com.payment.authservice.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/kakao/login")
    public ResponseEntity<AuthResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        AuthResponse response = authService.kakaoLogin(request.code());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        HttpServletRequest request,
        @RequestBody(required = false) LogoutRequest logoutRequest
    ) {
        String accessToken = resolveToken(request);
        String refreshToken = logoutRequest != null ? logoutRequest.refreshToken() : null;

        authService.logout(accessToken, refreshToken);

        return ResponseEntity.noContent().build();
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
