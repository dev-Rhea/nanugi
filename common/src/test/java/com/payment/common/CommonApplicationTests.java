package com.payment.common;

import com.payment.common.domain.Bank;
import com.payment.common.domain.Member;
import com.payment.common.domain.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommonApplicationTests {

    @Test
    void memberEntityCreation() {
        Member member = Member.builder()
                .name("Test User")
                .kakaoId("12345")
                .build();

        assertEquals("Test User", member.getName());
        assertEquals("12345", member.getKakaoId());
        assertEquals(Role.USER, member.getRole());
        assertEquals(Bank.NONE, member.getBank());
    }

    @Test
    void memberProfileUpdate() {
        Member member = Member.builder()
                .name("Test User")
                .kakaoId("12345")
                .build();

        member.updateProfile("new_nickname", "new_profile.jpg");

        assertEquals("new_nickname", member.getNickname());
        assertEquals("new_profile.jpg", member.getProfileImageUrl());
    }

    @Test
    void memberAccountUpdate() {
        Member member = Member.builder()
                .name("Test User")
                .kakaoId("12345")
                .build();

        member.updateAccount(Bank.SHINHAN, "123-456-789");

        assertEquals(Bank.SHINHAN, member.getBank());
        assertEquals("123-456-789", member.getAccountNumber());
    }

    @Test
    void memberKakaoAccessTokenUpdate() {
        Member member = Member.builder()
                .name("Test User")
                .kakaoId("12345")
                .build();

        member.updateKakaoAccessToken("new_kakao_token");

        assertEquals("new_kakao_token", member.getKakaoAccessToken());
    }
}
