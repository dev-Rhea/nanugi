package com.payment.authservice.dto;

import com.payment.common.domain.Member;

public record MemberResponse(
    Long id,
    String name,
    String nickname,
    String email,
    String profileImageUrl,
    String bank,
    String accountNumber
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
            member.getId(),
            member.getName(),
            member.getNickname(),
            member.getEmail(),
            member.getProfileImageUrl(),
            member.getBank() != null ? member.getBank().getBankName() : null,
            member.getAccountNumber()
        );
    }
}
