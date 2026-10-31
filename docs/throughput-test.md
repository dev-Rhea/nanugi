# 처리량 테스트 기록

JUnit 기반 동시 요청 테스트의 측정 조건과 결과. 별도 부하 도구(JMeter 등)로 HTTP 경로 전체를 잰 값이 아니라,
애플리케이션 서비스 메서드를 같은 JVM 안에서 동시에 호출해 잰 값이다.

## 측정 조건

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-10-31 |
| 장비 | Apple M1 Pro, 16 GB, OpenJDK 21.0.5 |
| DB | H2 in-memory (테스트 프로파일) |
| Redis·Kafka | Mock (정산 생성 경로에는 분산 락이 걸려 있지 않음) |
| 반복 | 6회 (`--rerun-tasks`) |

## 테스트

| 테스트 | 시나리오 | 통과 기준 |
| --- | --- | --- |
| `SettlementThroughputTest` | 100개 스레드가 동시에 `SettlementService.createSettlement` 호출 (정산 생성) | 전건 성공, TPS ≥ 20, P99 ≤ 3000ms |
| `AnalyticsQueryThroughputTest` | 지출 기록 1,000건 적재 후 50개 스레드가 필터 조건 4종을 번갈아 조회 | 전건 성공, QPS ≥ 50, P99 ≤ 1000ms |

## 결과 (6회)

| 테스트 | 처리량 범위 | P99 범위 | 실패 |
| --- | --- | --- | --- |
| 정산 생성 | 1,333 ~ 1,613 TPS | 60 ~ 74ms | 0 |
| 분석 필터 조회 | 91.6 ~ 96.7 QPS | 516 ~ 546ms | 0 |

## 해석할 때 주의할 점

- 요청이 100건(정산)·50건(조회)뿐이라 전체 소요 시간이 70ms·530ms 수준이다. 처리량은 이 짧은 구간을 나눈 값이므로 실행마다 흔들린다.
- H2 메모리 DB와 Mock 인프라 기준이다. PostgreSQL·실제 Redis·Kafka를 포함한 운영 유사 환경의 처리량을 뜻하지 않는다.
- 분석 필터는 기간 조건으로 DB에서 가져온 뒤 금액·카테고리·유형 조건을 메모리에서 거른다. 기록이 많아지면 DB 쿼리로 내리는 것이 다음 개선 대상이다.

## 실행

```bash
./gradlew :payment-service:test --tests "*SettlementThroughputTest" \
          :analysis-service:test --tests "*AnalyticsQueryThroughputTest" --rerun-tasks -i \
  | grep -E "\] (TPS|QPS)="
```
