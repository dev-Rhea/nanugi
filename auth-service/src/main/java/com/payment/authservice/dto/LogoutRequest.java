package com.payment.authservice.dto;

public record LogoutRequest(
    String refreshToken
) {
}
