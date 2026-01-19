package com.payment.authservice.client;

import com.payment.authservice.dto.KakaoFriendsResponse;
import com.payment.authservice.dto.KakaoMessageResponse;
import com.payment.authservice.dto.KakaoUserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "kakaoApiClient", url = "https://kapi.kakao.com")
public interface KakaoApiClient {

    @GetMapping("/v2/user/me")
    KakaoUserInfo getUserInfo(@RequestHeader("Authorization") String authorization);

    @GetMapping("/v1/api/talk/friends")
    KakaoFriendsResponse getFriends(
        @RequestHeader("Authorization") String authorization,
        @RequestParam(value = "offset", defaultValue = "0") int offset,
        @RequestParam(value = "limit", defaultValue = "100") int limit
    );

    @PostMapping(value = "/v1/api/talk/friends/message/default/send", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    KakaoMessageResponse sendMessage(
        @RequestHeader("Authorization") String authorization,
        @RequestParam("receiver_uuids") String receiverUuids,
        @RequestParam("template_object") String templateObject
    );
}
