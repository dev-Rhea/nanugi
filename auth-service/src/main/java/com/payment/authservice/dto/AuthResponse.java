package com.payment.authservice.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken
) {
}
