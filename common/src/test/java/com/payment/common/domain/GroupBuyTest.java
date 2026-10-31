package com.payment.common.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupBuyTest {

    private GroupBuy groupBuy(BigDecimal price, int min, int max, LocalDateTime deadline) {
        return GroupBuy.builder()
            .productName("상품")
            .productPrice(price)
            .minParticipants(min)
            .maxParticipants(max)
            .recruitmentDeadline(deadline)
            .build();
    }

    private GroupBuyParticipant participant(GroupBuy groupBuy, String uuid) {
        return GroupBuyParticipant.builder()
            .groupBuy(groupBuy)
            .kakaoUuid(uuid)
            .nickname(uuid)
            .build();
    }

    private void join(GroupBuy groupBuy, int count) {
        for (int i = 0; i < count; i++) {
            groupBuy.addParticipant(participant(groupBuy, "user-" + i));
        }
    }

    @Test
    @DisplayName("생성 직후에는 모집 중 상태이며 공유 토큰이 발급된다")
    void startsRecruitingWithShareToken() {
        GroupBuy groupBuy = groupBuy(BigDecimal.valueOf(10_000), 2, 4, LocalDateTime.now().plusDays(1));

        assertThat(groupBuy.getStatus()).isEqualTo(GroupBuyStatus.RECRUITING);
        assertThat(groupBuy.getShareToken()).isNotBlank();
        assertThat(groupBuy.isRecruitmentOpen()).isTrue();
    }

    @Test
    @DisplayName("최소 인원에 도달해야 isMinParticipantsReached가 참이 된다")
    void minParticipantsReached() {
        GroupBuy groupBuy = groupBuy(BigDecimal.valueOf(10_000), 3, 5, LocalDateTime.now().plusDays(1));

        join(groupBuy, 2);
        assertThat(groupBuy.isMinParticipantsReached()).isFalse();

        join(groupBuy, 1);
        assertThat(groupBuy.isMinParticipantsReached()).isTrue();
    }

    @Test
    @DisplayName("최대 인원이 차면 모집이 닫힌다")
    void recruitmentClosesWhenFull() {
        GroupBuy groupBuy = groupBuy(BigDecimal.valueOf(10_000), 1, 2, LocalDateTime.now().plusDays(1));

        join(groupBuy, 2);

        assertThat(groupBuy.isMaxParticipantsReached()).isTrue();
        assertThat(groupBuy.isRecruitmentOpen()).isFalse();
    }

    @Test
    @DisplayName("마감 시각이 지나면 인원이 남아 있어도 모집이 닫힌다")
    void recruitmentClosesAfterDeadline() {
        GroupBuy groupBuy = groupBuy(BigDecimal.valueOf(10_000), 1, 5, LocalDateTime.now().minusMinutes(1));

        assertThat(groupBuy.isRecruitmentOpen()).isFalse();
    }

    @Test
    @DisplayName("모집 중이 아닌 상태에서는 모집이 닫힌다")
    void recruitmentClosesWhenNotRecruiting() {
        GroupBuy groupBuy = groupBuy(BigDecimal.valueOf(10_000), 1, 5, LocalDateTime.now().plusDays(1));

        groupBuy.updateStatus(GroupBuyStatus.CONFIRMED);

        assertThat(groupBuy.isRecruitmentOpen()).isFalse();
    }

    @Test
    @DisplayName("CONFIRMED·PAID로 바뀔 때 각각의 시각이 기록된다")
    void recordsConfirmedAndPaidTimestamps() {
        GroupBuy groupBuy = groupBuy(BigDecimal.valueOf(10_000), 1, 5, LocalDateTime.now().plusDays(1));

        groupBuy.updateStatus(GroupBuyStatus.CONFIRMED);
        assertThat(groupBuy.getConfirmedAt()).isNotNull();
        assertThat(groupBuy.getPaidAt()).isNull();

        groupBuy.updateStatus(GroupBuyStatus.PAID);
        assertThat(groupBuy.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("1인당 금액은 원 단위로 올림한다 — 나누어떨어지면 그대로")
    void pricePerPersonDividesEvenly() {
        GroupBuy groupBuy = groupBuy(BigDecimal.valueOf(30_000), 1, 5, LocalDateTime.now().plusDays(1));
        join(groupBuy, 3);

        assertThat(groupBuy.getPricePerPerson()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("1인당 금액은 원 단위로 올림한다 — 나누어떨어지지 않으면 1원 올림")
    void pricePerPersonRoundsUp() {
        GroupBuy groupBuy = groupBuy(BigDecimal.valueOf(10_000), 1, 5, LocalDateTime.now().plusDays(1));
        join(groupBuy, 3);

        // 10000 / 3 = 3333.33… → 3334. 합계(10002)가 상품가보다 2원 크다는 점은 현재 정책의 특성이다.
        assertThat(groupBuy.getPricePerPerson()).isEqualByComparingTo("3334");
    }

    @Test
    @DisplayName("참여자가 없으면 1인당 금액은 상품가 그대로다 (0으로 나누지 않음)")
    void pricePerPersonWithoutParticipants() {
        GroupBuy groupBuy = groupBuy(BigDecimal.valueOf(10_000), 1, 5, LocalDateTime.now().plusDays(1));

        assertThat(groupBuy.getPricePerPerson()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("참여자를 제거하면 인원 수가 줄어든다")
    void removeParticipant() {
        GroupBuy groupBuy = groupBuy(BigDecimal.valueOf(10_000), 1, 5, LocalDateTime.now().plusDays(1));
        GroupBuyParticipant p = participant(groupBuy, "leaver");
        groupBuy.addParticipant(p);

        groupBuy.removeParticipant(p);

        assertThat(groupBuy.getCurrentParticipantCount()).isZero();
    }
}
