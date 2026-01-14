package com.payment.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;           // 카카오 실명

    @Column(unique = true)
    private String nickname;       // 서비스 내 별명 (수정 가능)

    private String email;          // 이메일

    private String phone;    // 전화번호

    private String profileImageUrl; // 프로필 사진 URL (수정 가능)

    @Enumerated(EnumType.STRING)
    private Role role;             // USER, ADMIN

    @Enumerated(EnumType.STRING)
    private Bank bank;             // 은행명

    private String accountNumber;  // 계좌번호

    private String kakaoId;        // 카카오 고유 식별값 (연동용)

    @Builder
    public Member(String name, String nickname, String email, String profileImageUrl, String kakaoId) {
        this.name = name;
        this.nickname = nickname;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.kakaoId = kakaoId;
        this.role = Role.USER;     // 기본값
        this.bank = Bank.NONE;     // 기본값
    }

    // 별명 및 프로필 수정 편의 메서드
    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    // 계좌 정보 등록/수정
    public void updateAccount(Bank bank, String accountNumber) {
        this.bank = bank;
        this.accountNumber = accountNumber;
    }

}