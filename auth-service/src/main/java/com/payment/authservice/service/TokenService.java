package com.payment.authservice.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final String ACCESS_TOKEN_PREFIX = "access_token:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String MEMBER_TOKENS_PREFIX = "member_tokens:";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public void saveTokens(Long memberId, String accessToken, String refreshToken) {
        String memberIdStr = String.valueOf(memberId);

        // Save access token -> memberId mapping
        redisTemplate.opsForValue().set(
            ACCESS_TOKEN_PREFIX + accessToken,
            memberIdStr,
            accessTokenExpiration,
            TimeUnit.MILLISECONDS
        );

        // Save refresh token -> memberId mapping
        redisTemplate.opsForValue().set(
            REFRESH_TOKEN_PREFIX + refreshToken,
            memberIdStr,
            refreshTokenExpiration,
            TimeUnit.MILLISECONDS
        );

        // Save memberId -> tokens mapping (for finding all tokens of a user)
        redisTemplate.opsForHash().put(
            MEMBER_TOKENS_PREFIX + memberIdStr,
            "accessToken",
            accessToken
        );
        redisTemplate.opsForHash().put(
            MEMBER_TOKENS_PREFIX + memberIdStr,
            "refreshToken",
            refreshToken
        );
        redisTemplate.expire(
            MEMBER_TOKENS_PREFIX + memberIdStr,
            refreshTokenExpiration,
            TimeUnit.MILLISECONDS
        );
    }

    public boolean isAccessTokenValid(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ACCESS_TOKEN_PREFIX + accessToken));
    }

    public boolean isRefreshTokenValid(String refreshToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REFRESH_TOKEN_PREFIX + refreshToken));
    }

    public Long getMemberIdByAccessToken(String accessToken) {
        String memberId = redisTemplate.opsForValue().get(ACCESS_TOKEN_PREFIX + accessToken);
        return memberId != null ? Long.parseLong(memberId) : null;
    }

    public Long getMemberIdByRefreshToken(String refreshToken) {
        String memberId = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshToken);
        return memberId != null ? Long.parseLong(memberId) : null;
    }

    public void deleteTokens(String accessToken, String refreshToken) {
        if (accessToken != null) {
            String memberId = redisTemplate.opsForValue().get(ACCESS_TOKEN_PREFIX + accessToken);
            redisTemplate.delete(ACCESS_TOKEN_PREFIX + accessToken);

            if (memberId != null) {
                redisTemplate.delete(MEMBER_TOKENS_PREFIX + memberId);
            }
        }

        if (refreshToken != null) {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
        }
    }

    public void deleteAllTokensForMember(Long memberId) {
        String memberIdStr = String.valueOf(memberId);
        String memberTokensKey = MEMBER_TOKENS_PREFIX + memberIdStr;

        String accessToken = (String) redisTemplate.opsForHash().get(memberTokensKey, "accessToken");
        String refreshToken = (String) redisTemplate.opsForHash().get(memberTokensKey, "refreshToken");

        if (accessToken != null) {
            redisTemplate.delete(ACCESS_TOKEN_PREFIX + accessToken);
        }
        if (refreshToken != null) {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
        }
        redisTemplate.delete(memberTokensKey);
    }
}
