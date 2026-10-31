# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Nanugi는 공동 구매와 소셜 정산을 돕는 MSA 기반 결제·정산 플랫폼입니다.
정산 금액을 1/N로 나누고, 사다리 타기로 부담자를 정합니다.
공동 구매에서는 '노쇼'를 막기 위해 빌링키로 결제를 예약해 두었다가 확정 시 일괄 승인합니다.

## Build Commands

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :auth-service:build
./gradlew :common:build

# Run tests
./gradlew test

# Run single test class
./gradlew :auth-service:test --tests "com.payment.authservice.AuthServiceApplicationTests"

# Run auth-service application
./gradlew :auth-service:bootRun

# Clean build
./gradlew clean build
```

로컬 통합 실행은 루트의 `docker-compose.yml`을 사용합니다.

## 기술 스택
- Backend: Java 21, Spring Boot 4.0.1, Spring Cloud 2025.1.0 (OpenFeign)
- Database: PostgreSQL, Redis (Redisson 분산락)
- Messaging: Apache Kafka
- Batch: Spring Batch (payment-service 일괄 결제)
- Security: Spring Security, JWT (jjwt), Kakao OAuth2 (auth-service)
- External API: 카카오 API, 스마트택배 배송 조회
- Testing: JUnit 5, Mockito, Testcontainers(일부), H2 테스트 프로필

## Module Structure
- `:common`: 공유 도메인 엔티티(Member, GroupBuy, Settlement, Outbox 등), 분산락 AOP(`@DistributedLock`), 빌링키 암호화 컨버터, 공통 예외 처리
- `:auth-service`: 카카오 OAuth2 로그인과 JWT(Access/Refresh) 발급
- `:payment-service`: 정산·사다리 타기, 결제 실행(현재 `MockPaymentGateway`), 빌링키, Spring Batch 일괄 결제, 결제·알림 이벤트 발행과 재시도
- `:order-service`: 공동 구매 참여, 배송 정보·배송 조회
- `:product-service`: 외부 상품 링크 크롤링(Python 스크립트 호출)
- `:analysis-service`: 정산·공동 구매·지출 분석 API

별도의 notification-service 모듈은 없습니다. 알림 이벤트 발행과 재시도는 payment-service가 담당합니다.

## Key Rules
1. **금액 타입**: 금액은 `Long`(원 단위) 또는 `BigDecimal`만 사용합니다. `float`/`double`은 금액에 쓰지 않습니다. 평균 인원 같은 통계 값에만 허용합니다.
2. **이벤트 발행 (현재 구현)**: Kafka로 먼저 발행하고, 발행이 실패하면 Outbox 테이블(`PaymentEventOutbox`, `NotificationOutbox`)에 저장한 뒤 지수 백오프(1, 2, 4분…)로 재발행합니다. DB 커밋과 Outbox 저장을 한 트랜잭션으로 묶는 Transactional Outbox는 **아닙니다**. 커밋 직후 프로세스가 종료되면 이벤트가 유실될 수 있습니다. Transactional Outbox로의 전환은 남은 과제입니다.
3. **동시성 제어**: 중복 결제·중복 정산을 막기 위해 핵심 트랜잭션에는 Redisson 분산락(`@DistributedLock`)과 JPA `@Version` 낙관적 락을 사용합니다. 요청 단위 멱등성 키(Idempotency Key)는 아직 구현되지 않았습니다.
4. **보안**: 서비스별 Spring Security와 JWT 필터로 인증합니다. 메서드 단위 인가(`@PreAuthorize`)는 아직 적용되지 않았습니다. 빌링키는 DB에 AES로 암호화해 저장하고, 응답에서는 결제수단 정보를 마스킹합니다. 새 코드에서도 계좌·카드 등 민감 정보를 로그와 응답에 그대로 남기지 않습니다.
5. **테스트**: 새 기능과 버그 수정에는 테스트를 함께 작성합니다. 커버리지 측정(JaCoCo)은 아직 설정하지 않았습니다. 처리량 테스트의 측정 조건과 결과는 `docs/throughput-test.md`에 있습니다.

## Code Style
- Layered Architecture: Controller - Service - Repository 구조를 따릅니다.
- DTO Pattern: 엔티티를 외부에 직접 노출하지 않고 Request/Response DTO를 사용합니다.
- Naming Conventions: 변수·메서드는 camelCase, 클래스는 PascalCase, DB 테이블·컬럼은 snake_case를 사용합니다.
- Git Flow: Conventional Commits(feat:, fix:, docs:, refactor:, test:)를 따릅니다.
