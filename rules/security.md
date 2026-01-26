# Security Guidelines

## Mandatory Security Checks

Before ANY commit:
- [ ] No hardcoded secrets (API keys, passwords, tokens)
- [ ] All user inputs validated
- [ ] SQL injection prevention (parameterized queries)
- [ ] XSS prevention (sanitized HTML)
- [ ] CSRF protection enabled
- [ ] Authentication/authorization verified
- [ ] Rate limiting on all endpoints
- [ ] Error messages don't leak sensitive data

## Secret Management

```Java
// ❌ NEVER: 소스 코드에 직접 기입
private String apiKey = "sk-proj-xxxxx";

// ✅ ALWAYS: application.yml 환경변수 주입 및 검증
@Component
public class ApiConfig {
  @Value("${openai.api.key}")
  private String apiKey;

  @PostConstruct
  public void validate() {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY must be configured in environment variables.");
    }
  }
}
```

## Security Response Protocol

If security issue found:
1. STOP immediately
2. Use **security-reviewer** agent
3. Fix CRITICAL issues before continuing
4. Rotate any exposed secrets
5. Review entire codebase for similar issues