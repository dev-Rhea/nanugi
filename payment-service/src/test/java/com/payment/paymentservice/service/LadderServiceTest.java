package com.payment.paymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.common.domain.Member;
import com.payment.common.domain.Settlement;
import com.payment.common.domain.SettlementParticipant;
import com.payment.common.domain.SettlementStatus;
import com.payment.common.exception.CustomException;
import com.payment.paymentservice.dto.response.LadderResultResponse;
import com.payment.paymentservice.repository.LadderResultRepository;
import com.payment.paymentservice.repository.SettlementRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * LadderService는 이전까지 테스트가 하나도 없었다(포트폴리오가 스스로 인정한
 * 공백). 사다리 결과의 나머지 분배가 정확히 나머지 금액만큼만 분배되는지,
 * 그리고 사전 가드(호스트 여부, 상태, 중복 실행)가 실제로 막는지를 검증한다.
 * 분산 락 자체의 동시성 효과는 DistributedLockAspectTest가 별도로 검증한다
 * (이 테스트는 AOP 프록시 없이 직접 인스턴스화하므로 락은 적용되지 않는다).
 */
@ExtendWith(MockitoExtension.class)
class LadderServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private LadderResultRepository ladderResultRepository;

    private LadderService service;
    private Member host;

    @BeforeEach
    void setUp() {
        service = new LadderService(
            settlementRepository, ladderResultRepository, new CalculationService(), new ObjectMapper());

        host = Member.builder().name("host").nickname("host-nick").build();
        ReflectionTestUtils.setField(host, "id", 1L);
    }

    @Test
    void nonHostCannotExecuteLadder() {
        Settlement settlement = settlementOf(1000, 3);
        when(settlementRepository.findByIdWithParticipants(10L)).thenReturn(Optional.of(settlement));

        assertThatThrownBy(() -> service.executeLadder(10L, 999L))
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getCustomErrorCode().name())
            .isEqualTo("FORBIDDEN");
    }

    @Test
    void cannotExecuteWhenSettlementIsNotPending() {
        Settlement settlement = settlementOf(1000, 3);
        settlement.updateStatus(SettlementStatus.IN_PROGRESS);
        when(settlementRepository.findByIdWithParticipants(10L)).thenReturn(Optional.of(settlement));

        assertThatThrownBy(() -> service.executeLadder(10L, 1L))
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getCustomErrorCode().name())
            .isEqualTo("INVALID_REQUEST");
    }

    @Test
    void cannotExecuteWhenLadderAlreadyRan() {
        Settlement settlement = settlementOf(1000, 3);
        when(settlementRepository.findByIdWithParticipants(10L)).thenReturn(Optional.of(settlement));
        when(ladderResultRepository.existsBySettlementId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.executeLadder(10L, 1L))
            .isInstanceOf(CustomException.class)
            .extracting(e -> ((CustomException) e).getCustomErrorCode().name())
            .isEqualTo("INVALID_REQUEST");
    }

    @Test
    void distributesExactlyTheRemainderAcrossParticipantsAndMarksInProgress() {
        // 10000원 / 3명 = 3333원씩 + 나머지 1원 → 한 명만 3334원, 나머지는 3333원
        Settlement settlement = settlementOf(10000, 3);
        when(settlementRepository.findByIdWithParticipants(10L)).thenReturn(Optional.of(settlement));
        when(ladderResultRepository.existsBySettlementId(10L)).thenReturn(false);

        LadderResultResponse response = service.executeLadder(10L, 1L);

        assertThat(response).isNotNull();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.IN_PROGRESS);

        List<SettlementParticipant> participants = settlement.getParticipants();
        BigDecimal totalDistributed = participants.stream()
            .map(SettlementParticipant::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDistributed).isEqualByComparingTo(settlement.getTotalAmount());

        long extraWinners = participants.stream()
            .filter(p -> p.getExtraAmount().compareTo(BigDecimal.ZERO) > 0)
            .count();
        assertThat(extraWinners).isEqualTo(1); // remainder=1원이므로 정확히 한 명만 추가로 받아야 한다

        verify(ladderResultRepository).save(any());
    }

    @Test
    void noRemainderMeansEveryoneGetsTheSameAmount() {
        // 9000원 / 3명 = 정확히 3000원씩, 나머지 없음
        Settlement settlement = settlementOf(9000, 3);
        when(settlementRepository.findByIdWithParticipants(10L)).thenReturn(Optional.of(settlement));
        when(ladderResultRepository.existsBySettlementId(10L)).thenReturn(false);

        service.executeLadder(10L, 1L);

        List<SettlementParticipant> participants = settlement.getParticipants();
        assertThat(participants).allSatisfy(p ->
            assertThat(p.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(3000)));
    }

    @Test
    void getLadderResultReturnsPersistedResultForHost() {
        Settlement settlement = settlementOf(1000, 3);
        when(settlementRepository.findById(10L)).thenReturn(Optional.of(settlement));

        var ladderResult = com.payment.common.domain.LadderResult.builder()
            .settlement(settlement)
            .remainder(BigDecimal.ONE)
            .winnerUuids("uuid-0")
            .ladderSeed(42L)
            .ladderStructure("{}")
            .build();
        when(ladderResultRepository.findBySettlementId(10L)).thenReturn(Optional.of(ladderResult));

        LadderResultResponse response = service.getLadderResult(10L, 1L);

        assertThat(response).isNotNull();
    }

    private Settlement settlementOf(long totalAmount, int participantCount) {
        Settlement settlement = Settlement.builder()
            .title("테스트 정산")
            .totalAmount(BigDecimal.valueOf(totalAmount))
            .host(host)
            .build();
        ReflectionTestUtils.setField(settlement, "id", 10L);
        settlement.updateStatus(SettlementStatus.PENDING); // 빌더 기본값은 DRAFT라 사다리 실행 전제조건을 맞춰줌

        for (int i = 0; i < participantCount; i++) {
            SettlementParticipant participant = SettlementParticipant.builder()
                .settlement(settlement)
                .kakaoUuid("uuid-" + i)
                .nickname("참여자" + i)
                .build();
            settlement.addParticipant(participant);
        }
        return settlement;
    }
}
