package com.payment.paymentservice.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payment.paymentservice.config.TestConfig;
import com.payment.paymentservice.domain.PaymentBatchItem;
import com.payment.paymentservice.domain.PaymentBatchItem.PaymentBatchItemStatus;
import com.payment.paymentservice.payment.PaymentGateway.PaymentResult;
import com.payment.paymentservice.repository.PaymentBatchItemRepository;
import com.payment.paymentservice.service.ParticipantPaymentExecutor;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * BatchPaymentJobConfig의 실제 동작 — PENDING 상태만 읽는 Reader가 멱등성을
 * 보장하는지, 같은 groupBuyId로 재실행하면 Spring Batch 자체가 중복 실행을
 * 막는지를 real H2 DB + JobLauncher로 검증한다.
 */
@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@Import(TestConfig.class)
class BatchPaymentJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job batchPaymentJob;

    @Autowired
    private PaymentBatchItemRepository paymentBatchItemRepository;

    @MockitoBean
    private ParticipantPaymentExecutor participantPaymentExecutor;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(batchPaymentJob);
        paymentBatchItemRepository.deleteAll();
    }

    @Test
    void processesOnlyPendingItemsAndMarksThemPaidOrFailed() throws Exception {
        Long groupBuyId = 999L;
        stage(groupBuyId, 1L, "A");
        stage(groupBuyId, 2L, "B");
        PaymentBatchItem alreadyPaid = stage(groupBuyId, 3L, "C");
        alreadyPaid.markSuccess("old-key", "old-url");
        paymentBatchItemRepository.save(alreadyPaid);

        when(participantPaymentExecutor.executeParticipantPayment(eq(groupBuyId), eq(1L), any(), any(), any(), any()))
            .thenReturn(PaymentResult.success("pay-1", "order-1", BigDecimal.TEN, "receipt-1"));
        when(participantPaymentExecutor.executeParticipantPayment(eq(groupBuyId), eq(2L), any(), any(), any(), any()))
            .thenReturn(PaymentResult.failure("order-2", "ERR", "card declined"));

        JobExecution execution = jobLauncherTestUtils.launchJob(groupBuyIdParams(groupBuyId));

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThat(paymentBatchItemRepository.findByGroupBuyIdAndParticipantId(groupBuyId, 1L).orElseThrow().getStatus())
            .isEqualTo(PaymentBatchItemStatus.PAID);
        assertThat(paymentBatchItemRepository.findByGroupBuyIdAndParticipantId(groupBuyId, 2L).orElseThrow().getStatus())
            .isEqualTo(PaymentBatchItemStatus.FAILED);

        // 참가자 3은 이미 PAID였으므로 PENDING 전용 Reader가 건드리지 않아야 한다
        verify(participantPaymentExecutor, never())
            .executeParticipantPayment(eq(groupBuyId), eq(3L), any(), any(), any(), any());
    }

    @Test
    void rerunningWithSameGroupBuyIdIsRejectedByBatchMetadata() throws Exception {
        Long groupBuyId = 1000L;
        stage(groupBuyId, 1L, "A");

        when(participantPaymentExecutor.executeParticipantPayment(any(), any(), any(), any(), any(), any()))
            .thenReturn(PaymentResult.success("pay-1", "order-1", BigDecimal.TEN, "receipt-1"));

        JobParameters jobParameters = groupBuyIdParams(groupBuyId);
        jobLauncherTestUtils.launchJob(jobParameters);

        // 같은 JobParameters로 다시 실행하면 Spring Batch가 이미 COMPLETED된
        // JobInstance를 스스로 거부한다 — "실행마다 timestamp를 섞어 항상
        // 새 인스턴스를 만드는" 방식이 아니라 진짜 멱등성이 성립함을 보여준다.
        assertThatThrownBy(() -> jobLauncherTestUtils.launchJob(jobParameters))
            .isInstanceOf(JobInstanceAlreadyCompleteException.class);

        verify(participantPaymentExecutor, times(1))
            .executeParticipantPayment(any(), any(), any(), any(), any(), any());
    }

    private JobParameters groupBuyIdParams(Long groupBuyId) {
        return new JobParametersBuilder().addLong("groupBuyId", groupBuyId).toJobParameters();
    }

    private PaymentBatchItem stage(Long groupBuyId, Long participantId, String nickname) {
        PaymentBatchItem item = PaymentBatchItem.builder()
            .groupBuyId(groupBuyId)
            .participantId(participantId)
            .nickname(nickname)
            .billingKey("billing-" + participantId)
            .productName("테스트 상품")
            .amount(BigDecimal.TEN)
            .build();
        return paymentBatchItemRepository.save(item);
    }
}
