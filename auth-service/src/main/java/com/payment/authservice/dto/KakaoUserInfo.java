package com.payment.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserInfo(
    Long id,

    @JsonProperty("kakao_account")
    KakaoAccount kakaoAccount
) {
    public record KakaoAccount(
        String email,
        Profile profile
    ) {
        public record Profile(
            String nickname,

            @JsonProperty("profile_image_url")
            String profileImageUrl
        ) {
        }
    }

    public String getNickname() {
        if (kakaoAccount != null && kakaoAccount.profile() != null) {
            return kakaoAccount.profile().nickname();
        }
        return null;
    }

    public String getEmail() {
        return kakaoAccount != null ? kakaoAccount.email() : null;
    }

    public String getProfileImageUrl() {
        if (kakaoAccount != null && kakaoAccount.profile() != null) {
            return kakaoAccount.profile().profileImageUrl();
        }
        return null;
    }
}
