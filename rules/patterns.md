# Common Patterns

## API Response Format

```Java
@Getter
@Builder
public class ApiResponse<T> {
    private final boolean success;
    private final T data;
    private final ApiError error;
    private final Meta meta;

    @Getter
    @AllArgsConstructor
    public static class ApiError {
        private final String code;
        private final String message;
    }

    @Getter
    @Builder
    public static class Meta {
        private final long total;
        private final int page;
        private final int limit;
    }
}
```

## Service Layer Pattern

```Java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {
  private final SettlementRepository repository;
  private final RedisDistributedLock lock;

  @Transactional
  public SettlementResponse createSettlement(SettlementRequest request) {
  // Business Logic
  return SettlementResponse.from(repository.save(request.toEntity()));
}
}
```

## Repository Pattern (Spring Data JPA)

```Java
public interface GenericRepository<T, ID> extends JpaRepository<T, ID> {
    // Basic CRUD is inherited from JpaRepository

    // Custom query methods using Method Name Convention or @Query
    Optional<T> findByActiveTrue();
}

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Settlement s WHERE s.id = :id")
    Optional<Settlement> findByIdWithLock(@Param("id") Long id);
}
```

## Skeleton Projects & Module Implementation

This outlines the standard procedure when implementing new features within the project:

1. MSA Module-Based Discovery: Prioritize searching for battle-tested logic (e.g., payment, settlement) within the existing common module or similar service modules before creating new implementations.
2. Multi-Agent Analysis: Utilize specialized analysis to ensure high standards:
    - Security Review: Does the code comply with security-reviewer.md? (e.g., Use of BigDecimal for currency, input sanitization).
    - Architectural Fit: Does it align with the Saga or Transactional Outbox patterns defined in architect.md?
    - Efficiency: Are queries optimized to prevent N+1 problems?
3. Code Scaffolding: Verify build.gradle dependencies and replicate the established Controller-Service-Repository layer structure to maintain architectural consistency.
4. Iterative Refinement: Ensure the integrity of business logic through comprehensive unit testing (JUnit 5) and refine the implementation based on test results.