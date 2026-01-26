# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Nanugi는 복잡한 공동 구매와 소셜 정산을 돕는 MSA 기반 결제 및 정산 플랫폼입니다. 
사용자가 직접 입력하거나 OCR로 인식한 내역을 1/N로 계산하고, 소수점 단위의 잔액은 사다리 타기로 공정하게 배분합니다. 
또한, 공동 구매 시 '노쇼' 방지를 위한 결제 예약(빌링키) 및 일괄 승인 시스템을 제공합니다.

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

## Architecture

### Multi-Module Structure

## 기술 스택
- Backend: Java 17, Spring Boot 4.0.1, Spring Cloud 2025.1.0
- Database: PostgreSQL (Persistence), Redis (Distributed Lock & Token Storage)
- Messaging: Apache Kafka (Event-Driven Architecture)
- Batch: Spring Batch (Bulk Payment Processing)
- External API: Kakao OAuth2 & Message API, 스마트택배 API
- Testing: JUnit 5, Mockito, Testcontainers

## File Structure
- :common: Shared library containing domain entities (Member, Role), global exception handling, and common utilities.
- :auth-service: Authentication microservice managing Kakao OAuth2 integration and JWT lifecycle (Access/Refresh tokens).
- :payment-service: Core service handling settlement logic (Ladder Game), payment execution, billing keys, and batch jobs.
- :product-service: Manages group purchase product information and external link scraping.
- :notification-service: Handles Kakao AlimTalk and internal notification events.

## Dependencies

- Spring Boot 4.0.1
- Spring Cloud 2025.1.0 (OpenFeign for inter-service communication)
- Spring Security + OAuth2 Client
- Spring Data JPA + Redis
- PostgreSQL
- jjwt (JWT library)

## Key Rules
1. Financial Integrity: All currency calculations must use Long (for KRW units) or BigDecimal.The use of floating-point types (float, double) is strictly prohibited for money.~~~~
2. Event-Driven Communication: Asynchronous events via Kafka are used for inter-service synchronization. The Transactional Outbox pattern must be used to ensure reliable message publishing.
3. Security & Authorization: Every API endpoint must enforce authorization checks (e.g., @PreAuthorize). Sensitive PII (account numbers, card details) must be masked in logs and UI.
4. Concurrency Control: To prevent duplicate payments or settlement creation, Redis Distributed Locks and Idempotency Keys are mandatory for critical transactions.
5. Test-First Mentality: Maintain at least 80% test coverage using TDD (Test-Driven Development) to ensure the stability of core business logic.

## Code Style
- Layered Architecture: Strictly follow the Controller - Service - Repository pattern.
- DTO Pattern: Never expose entities directly to the outside; use Request/Response DTOs for data transfer.
- Naming Conventions: Use camelCase for variables/methods, PascalCase for classes, and snake_case for database tables/columns.
- Audit Logging: All entities must extend BaseEntity to automatically record CreatedAt and UpdatedAt timestamps.
- Git Flow: Follow Conventional Commits (feat:, fix:, docs:, refactor:) for all pull requests and commits.