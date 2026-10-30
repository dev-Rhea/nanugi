package com.payment.paymentservice.batch;

import com.payment.paymentservice.domain.PaymentBatchItem;
import com.payment.paymentservice.domain.PaymentBatchItem.PaymentBatchItemStatus;
import com.payment.paymentservice.payment.PaymentGateway.PaymentResult;
import com.payment.paymentservice.repository.PaymentBatchItemRepository;
import com.payment.paymentservice.service.ParticipantPaymentExecutor;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.data.RepositoryItemReader;
import org.springframework.batch.infrastructure.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.JpaItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JpaItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 공동구매 일괄 결제를 Spring Batch chunk 지향 처리로 구현한다. 참가자 수가
 * 수천 명으로 늘어나도 한 번에 메모리에 올리지 않고 CHUNK_SIZE 단위로
 * 읽기-처리-쓰기-커밋을 반복하며, PENDING 상태만 조회하는 Reader가 멱등성을
 * 보장한다(PaymentBatchItem 참고).
 */
@Configuration
public class BatchPaymentJobConfig {

    public static final String JOB_NAME = "batchPaymentJob";
    private static final int CHUNK_SIZE = 50;

    @Bean(name = JOB_NAME)
    public Job batchPaymentJob(JobRepository jobRepository, Step batchPaymentStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .start(batchPaymentStep)
            .build();
    }

    @Bean
    public Step batchPaymentStep(JobRepository jobRepository,
                                  PlatformTransactionManager transactionManager,
                                  RepositoryItemReader<PaymentBatchItem> paymentBatchItemReader,
                                  ItemProcessor<PaymentBatchItem, PaymentBatchItem> participantPaymentProcessor,
                                  ItemWriter<PaymentBatchItem> paymentBatchItemWriter) {
        return new StepBuilder("batchPaymentStep", jobRepository)
            .<PaymentBatchItem, PaymentBatchItem>chunk(CHUNK_SIZE, transactionManager)
            .reader(paymentBatchItemReader)
            .processor(participantPaymentProcessor)
            .writer(paymentBatchItemWriter)
            .build();
    }

    @Bean
    @StepScope
    public RepositoryItemReader<PaymentBatchItem> paymentBatchItemReader(
            PaymentBatchItemRepository repository,
            @Value("#{jobParameters['groupBuyId']}") Long groupBuyId) {
        return new RepositoryItemReaderBuilder<PaymentBatchItem>()
            .name("paymentBatchItemReader")
            .repository(repository)
            .methodName("findByGroupBuyIdAndStatus")
            .arguments(List.of(groupBuyId, PaymentBatchItemStatus.PENDING))
            .sorts(Map.of("id", Sort.Direction.ASC))
            .pageSize(CHUNK_SIZE)
            .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<PaymentBatchItem, PaymentBatchItem> participantPaymentProcessor(
            ParticipantPaymentExecutor participantPaymentExecutor,
            @Value("#{jobParameters['groupBuyId']}") Long groupBuyId) {
        return item -> {
            String orderId = "GB%d_P%d_%s".formatted(
                groupBuyId, item.getParticipantId(), UUID.randomUUID().toString().substring(0, 8));
            String orderName = "[공동구매] %s - %s".formatted(item.getProductName(), item.getNickname());

            PaymentResult result = participantPaymentExecutor.executeParticipantPayment(
                groupBuyId, item.getParticipantId(), item.getBillingKey(),
                orderId, item.getAmount(), orderName);

            if (result.success()) {
                item.markSuccess(result.paymentKey(), result.receiptUrl());
            } else {
                item.markFailed(result.errorMessage());
            }
            return item;
        };
    }

    @Bean
    public JpaItemWriter<PaymentBatchItem> paymentBatchItemWriter(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<PaymentBatchItem>()
            .entityManagerFactory(entityManagerFactory)
            .build();
    }
}
