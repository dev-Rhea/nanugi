package com.payment.authservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KakaoFriendsResponse(
    List<KakaoFriend> elements,

    @JsonProperty("total_count")
    Integer totalCount,

    @JsonProperty("after_url")
    String afterUrl,

    @JsonProperty("favorite_count")
    Integer favoriteCount
) {
    public record KakaoFriend(
        Long id,

        String uuid,

        @JsonProperty("profile_nickname")
        String profileNickname,

        @JsonProperty("profile_thumbnail_image")
        String profileThumbnailImage,

        Boolean favorite
    ) {
    }
}
