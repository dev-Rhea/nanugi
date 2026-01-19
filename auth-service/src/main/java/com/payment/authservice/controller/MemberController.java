package com.payment.authservice.controller;

import com.payment.authservice.dto.AccountUpdateRequest;
import com.payment.authservice.dto.FriendResponse;
import com.payment.authservice.dto.MemberResponse;
import com.payment.authservice.dto.ProfileUpdateRequest;
import com.payment.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<MemberResponse> getCurrentMember(@AuthenticationPrincipal Long memberId) {
        MemberResponse response = authService.getCurrentMember(memberId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<MemberResponse> updateProfile(
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody ProfileUpdateRequest request
    ) {
        MemberResponse response = authService.updateProfile(
            memberId,
            request.nickname(),
            request.profileImageUrl()
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me/account")
    public ResponseEntity<MemberResponse> updateAccount(
        @AuthenticationPrincipal Long memberId,
        @Valid @RequestBody AccountUpdateRequest request
    ) {
        MemberResponse response = authService.updateAccount(
            memberId,
            request.bank(),
            request.accountNumber()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me/friends")
    public ResponseEntity<FriendResponse> getFriends(
        @AuthenticationPrincipal Long memberId,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "100") int limit
    ) {
        FriendResponse response = authService.getFriends(memberId, offset, limit);
        return ResponseEntity.ok(response);
    }
}
