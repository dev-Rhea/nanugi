# 처리량 테스트 기록

두 가지 방식으로 측정했다.

1. **JMeter (HTTP 전체 경로)**: docker compose로 띄운 서비스에 HTTP 요청을 보내 잰 값. 보안 필터, JWT 검증, PostgreSQL 저장까지 포함한다.
2. **JUnit (서비스 메서드)**: 서비스 메서드를 같은 JVM 안에서 동시에 호출해 잰 값. H2와 Mock 인프라를 쓴다.

# 1. JMeter 부하 테스트

## 측정 조건

| 항목 | 값 |
| --- | --- |
| 측정일 | 2026-10-31 |
| 장비 | Apple M1 Pro (10코어), 16 GB. JMeter와 서비스가 같은 장비에서 CPU를 나눠 씀 |
| 실행 환경 | Docker Desktop (10 CPU, 8 GB), `docker-compose.yml`의 postgres·redis·kafka·payment-service·analysis-service |
| 도구 | Apache JMeter 5.6.3, OpenJDK 21.0.5 |
| 인증 | 서비스와 같은 `JWT_SECRET`으로 만든 테스트 토큰 (`docs/load-test/make-token.py`) |
| 데이터 | 회원 1명, 지출 기록 1,000건 (`docs/load-test/seed.sql`, JUnit 테스트와 같은 분포) |
| 방식 | 시나리오마다 30초 예열 후 70초씩 3회 측정. 램프업 5초 + 5초를 뺀 60초 구간만 집계 |

## 시나리오

| 시나리오 | 동시 사용자 | 요청 |
| --- | --- | --- |
| 정산 생성 | 100 | `POST /api/settlements` (참여자 없는 정산 생성, PostgreSQL 저장) |
| 분석 필터 조회 | 50 | `GET /api/analytics/spending` 필터 4종(최소 금액, 최대 금액, 카테고리, 금액 범위+유형)을 번갈아 호출. 응답 평균 약 130 KB |

## 결과 (3회)

| 시나리오 | 처리량 | P50 | P95 | P99 | 실패 |
| --- | --- | --- | --- | --- | --- |
| 정산 생성 | 4,293 ~ 4,405 TPS | 20 ~ 21ms | 55 ~ 56ms | 83 ~ 86ms | 0 / 781,759 |
| 분석 필터 조회 | 787 ~ 860 QPS | 51 ~ 55ms | 109 ~ 120ms | 140 ~ 162ms | 0 / 147,098 |

측정 후 `settlements` 테이블에 정산이 실제로 저장된 것을 확인했다.

## 해석할 때 주의할 점

- 노트북 한 대에서 JMeter와 서비스, DB를 함께 돌린 값이다. 네트워크 지연이 거의 없고 JMeter도 CPU를 나눠 쓰므로, 운영 환경 처리량으로 보면 안 된다.
- 정산 생성 경로에는 분산 락이 없고 참여자도 0명이다. 참여자 등록, 확정, 결제 같은 경로는 측정하지 않았다.
- 서비스 인스턴스는 각 1개다. 수평 확장 시의 처리량은 측정하지 않았다.
- 아래 JUnit 값보다 높게 나온 이유: JUnit 테스트는 예열 없이 100건(50건)만 한 번 보내 전체 시간을 나눈 값이라 스레드 생성과 JIT 시간이 크게 섞인다. JMeter는 예열 후 60초 동안의 정상 구간만 집계했다.

## 실행

```bash
./gradlew :payment-service:bootJar :analysis-service:bootJar
docker compose up -d postgres redis kafka payment-service analysis-service
docker compose exec -T postgres psql -U postgres -d nanugi < docs/load-test/seed.sql
docs/load-test/run.sh          # 결과: docs/load-test/results/
```

# 2. JUnit 동시 요청 테스트

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
