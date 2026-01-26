# Testing Requirements

## Minimum Test Coverage: 80%

Test Types (ALL required):
1. **Unit Tests** - Focus on individual business logic in @Service layers and utility classes.
2. **Integration Tests** 
   - Persistence: Use @DataJpaTest with Testcontainers (PostgreSQL) to verify repository queries.
   - API: Use MockMVC or WebTestClient to verify @Controller contracts.
   - Messaging: Use Spring Kafka Test to verify producers/consumers.
3. Contract Tests (Spring Cloud Contract): Required for all inter-service communication (e.g., between Order and Payment services).
4. **E2E Tests** - Critical user flows (Playwright)

## Test-Driven Development

MANDATORY workflow:
1. RED: Write a failing test case in src/test/java.
2. FAIL: Execute the test. It must fail (often with a compilation error or AssertionError).
3. GREEN: Write the minimal code in src/main/java to make the test pass.
4. PASS: Ensure all tests pass.
5. REFACTOR: Clean up code and optimize queries (check for N+1 issues).
6. VERIFY: Check Jacoco coverage reports to ensure 80%+ coverage.

## Troubleshooting Test Failures

1. Agent Support: Invoke the tdd-guide agent immediately.
2. Isolation: Ensure @SpringBootTest is not loading the entire context unnecessarily; use sliced tests (@WebMvcTest, etc.) for speed.
3. Environment: Verify that Docker is running if the test requires Testcontainers for PostgreSQL or Kafka.
4. Mock Integrity: Ensure @MockBean is used correctly to isolate external API dependencies (like Kakao API).
5. Fix Logic, Not Tests: Do not modify assertions to match buggy code.

## Agent Support

- **tdd-guide** - Use PROACTIVELY for new features. It enforces the "tests-first" mentality and ensures BigDecimal or Long is used for financial calculations.
- **e2e-runner** - Specialized in cross-service flow validation and UI testing for the settlement detail pages.
- **contract-checker** - Ensures that changes in one service do not break the API expectations of another.