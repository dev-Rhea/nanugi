package com.payment.authservice.service;

import com.payment.authservice.client.KakaoApiClient;
import com.payment.authservice.client.KakaoAuthClient;
import com.payment.authservice.dto.AuthResponse;
import com.payment.authservice.dto.FriendResponse;
import com.payment.authservice.dto.KakaoFriendsResponse;
import com.payment.authservice.dto.KakaoTokenResponse;
import com.payment.authservice.dto.KakaoUserInfo;
import com.payment.authservice.dto.MemberResponse;
import com.payment.authservice.repository.MemberRepository;
import com.payment.authservice.security.JwtProvider;
import com.payment.common.domain.Bank;
import com.payment.common.domain.Member;
import com.payment.common.exception.CustomErrorCode;
import com.payment.common.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoAuthClient kakaoAuthClient;
    private final KakaoApiClient kakaoApiClient;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final TokenService tokenService;

    @Value("${kakao.client-id}")
    private String kakaoClientId;

    @Value("${kakao.redirect-uri}")
    private String kakaoRedirectUri;

    @Transactional
    public AuthResponse kakaoLogin(String code) {
        KakaoTokenResponse kakaoToken = kakaoAuthClient.getAccessToken(
            "authorization_code",
            kakaoClientId,
            kakaoRedirectUri,
            code
        );

        KakaoUserInfo userInfo = kakaoApiClient.getUserInfo("Bearer " + kakaoToken.accessToken());

        Member member = findOrCreateMember(userInfo, kakaoToken.accessToken());

        // Delete existing tokens for this member (single session)
        tokenService.deleteAllTokensForMember(member.getId());

        String accessToken = jwtProvider.generateAccessToken(member.getId());
        String refreshToken = jwtProvider.generateRefreshToken(member.getId());

        // Store tokens in Redis
        tokenService.saveTokens(member.getId(), accessToken, refreshToken);

        return new AuthResponse(accessToken, refreshToken);
    }

    private Member findOrCreateMember(KakaoUserInfo userInfo, String kakaoAccessToken) {
        String kakaoId = String.valueOf(userInfo.id());

        return memberRepository.findByKakaoId(kakaoId)
            .map(member -> {
                member.updateKakaoAccessToken(kakaoAccessToken);
                return member;
            })
            .orElseGet(() -> {
                Member newMember = Member.builder()
                    .kakaoId(kakaoId)
                    .name(userInfo.getNickname() != null ? userInfo.getNickname() : "사용자")
                    .nickname(userInfo.getNickname())
                    .email(userInfo.getEmail())
                    .profileImageUrl(userInfo.getProfileImageUrl())
                    .build();
                newMember.updateKakaoAccessToken(kakaoAccessToken);
                return memberRepository.save(newMember);
            });
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new CustomException(CustomErrorCode.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다.");
        }

        // Check if refresh token exists in Redis
        if (!tokenService.isRefreshTokenValid(refreshToken)) {
            throw new CustomException(CustomErrorCode.UNAUTHORIZED, "만료되거나 유효하지 않은 리프레시 토큰입니다.");
        }

        Long memberId = jwtProvider.getMemberIdFromToken(refreshToken);

        memberRepository.findById(memberId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND, "존재하지 않는 회원입니다."));

        // Delete old tokens
        tokenService.deleteAllTokensForMember(memberId);

        String newAccessToken = jwtProvider.generateAccessToken(memberId);
        String newRefreshToken = jwtProvider.generateRefreshToken(memberId);

        // Store new tokens in Redis
        tokenService.saveTokens(memberId, newAccessToken, newRefreshToken);

        return new AuthResponse(newAccessToken, newRefreshToken);
    }

    @Transactional(readOnly = true)
    public MemberResponse getCurrentMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND, "존재하지 않는 회원입니다."));

        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse updateProfile(Long memberId, String nickname, String profileImageUrl) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND, "존재하지 않는 회원입니다."));

        member.updateProfile(nickname, profileImageUrl);

        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse updateAccount(Long memberId, Bank bank, String accountNumber) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND, "존재하지 않는 회원입니다."));

        member.updateAccount(bank, accountNumber);

        return MemberResponse.from(member);
    }

    @Transactional(readOnly = true)
    public FriendResponse getFriends(Long memberId, int offset, int limit) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new CustomException(CustomErrorCode.NOT_FOUND, "존재하지 않는 회원입니다."));

        if (member.getKakaoAccessToken() == null) {
            throw new CustomException(CustomErrorCode.UNAUTHORIZED, "카카오 토큰이 만료되었습니다. 다시 로그인해주세요.");
        }

        KakaoFriendsResponse kakaoResponse = kakaoApiClient.getFriends(
            "Bearer " + member.getKakaoAccessToken(),
            offset,
            limit
        );

        return FriendResponse.from(kakaoResponse);
    }

    public void logout(String accessToken, String refreshToken) {
        // Add tokens to blacklist
        if (accessToken != null && jwtProvider.validateToken(accessToken)) {
            long accessTokenExpiration = jwtProvider.getRemainingExpiration(accessToken);
            if (accessTokenExpiration > 0) {
                tokenBlacklistService.blacklistToken(accessToken, accessTokenExpiration);
            }
        }

        if (refreshToken != null && jwtProvider.validateToken(refreshToken)) {
            long refreshTokenExpiration = jwtProvider.getRemainingExpiration(refreshToken);
            if (refreshTokenExpiration > 0) {
                tokenBlacklistService.blacklistToken(refreshToken, refreshTokenExpiration);
            }
        }

        // Delete tokens from Redis
        tokenService.deleteTokens(accessToken, refreshToken);

        log.info("User logged out successfully");
    }
}
