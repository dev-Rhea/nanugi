package com.payment.common.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Bank {
    KAKA("카카오뱅크"),
    KOOKMIN("KB국민은행"),
    SHINHAN("신한은행"),
    TOSS("토스뱅크"),
    HANA("하나은행"),
    WOORI("우리은행"),
    NONE("없음");

    private final String bankName;
}