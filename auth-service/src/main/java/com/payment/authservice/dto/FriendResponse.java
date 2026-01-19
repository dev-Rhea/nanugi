package com.payment.authservice.dto;

import java.util.List;

public record FriendResponse(
    List<Friend> friends,
    int totalCount
) {
    public record Friend(
        String uuid,
        String nickname,
        String profileImageUrl,
        boolean favorite
    ) {
        public static Friend from(KakaoFriendsResponse.KakaoFriend kakaoFriend) {
            return new Friend(
                kakaoFriend.uuid(),
                kakaoFriend.profileNickname(),
                kakaoFriend.profileThumbnailImage(),
                kakaoFriend.favorite() != null && kakaoFriend.favorite()
            );
        }
    }

    public static FriendResponse from(KakaoFriendsResponse kakaoResponse) {
        List<Friend> friends = kakaoResponse.elements() != null
            ? kakaoResponse.elements().stream().map(Friend::from).toList()
            : List.of();

        return new FriendResponse(
            friends,
            kakaoResponse.totalCount() != null ? kakaoResponse.totalCount() : 0
        );
    }
}
