# Runtime Validation Report

**Generated**: 2026-11-15
**Target**: ReabilitaCare API

## Summary

| Step | Status | Exit code | Evidence |
|---|---|---:|---|
| Startup | PASS | 0 | Spring Boot started on a random HTTP port during `mvnw clean verify`; PostgreSQL 17.12 ran through Testcontainers. |
| Integration tests | PASS | 0 | 50 tests in 6 suites; 0 failures, 0 errors, 0 skipped. |
| HTTP/API flows | PASS | 0 | Auth, patient and care-record integration tests exercised the running application and PostgreSQL. |
| OpenAPI contract | PASS | 0 | The integration-test context explicitly enabled `/v3/api-docs` and Swagger UI; both returned 200 and contract assertions verified routes and bearer security. They are disabled by default in application configuration. |
| Prometheus security | PASS | 0 | `/actuator/prometheus` returned 401 without a JWT and 200 with a valid JWT; JVM and HTTP histogram metrics were present. |
| Checkstyle | PASS | 0 | 0 violations. |
| JaCoCo | PASS | 0 | 96.61% line coverage (427 covered, 15 missed); the 70% gate passed. |
| GitHub Actions | CONFIGURED | N/A | Workflow added; it has not yet run on GitHub. |

**Overall**: PASS

## Environment and test strategy

- Docker: AVAILABLE — 29.3.1, Linux x86_64.
- Node.js: AVAILABLE — v20.19.6.
- Application type: REST API only; there is no browser UI, so Playwright/browser testing is not applicable.
- Infrastructure tier: PRIMARY — PostgreSQL 17.12 via Testcontainers.
- Startup/test command: `.\mvnw.cmd --batch-mode --no-transfer-progress clean verify` with JDK 21.
- HTTP tests run against the Spring Boot application on a random port. The existing PostgreSQL containers and volumes were not stopped or removed.

## HTTP evidence

- `GET /api/v1/health`: 200, with the correlation ID returned in `X-Correlation-ID`.
- `GET /v3/api-docs`: 200; OpenAPI 3 routes, responses and bearer security were asserted.
- `GET /swagger-ui/index.html`: 200.
- `GET /actuator/prometheus`: 401 without authentication; 200 with a valid JWT.
- The Prometheus response included `jvm_memory_used_bytes` and `http_server_requests_seconds_bucket`.

## Issues found and resolved

The Actuator test starter disables metric exporters in its test context by default. The Prometheus exporter is now explicitly enabled in application configuration, and the authenticated HTTP regression test passes without a test-only configuration override.

## Known gaps

- The GitHub Actions workflow was reviewed locally but has not been executed by GitHub.
- The documented SLOs are initial targets, not measured production guarantees. Alerting, external metric storage and a dedicated scraper identity are not configured.
- No browser testing was run because the application has no frontend.

## Test evidence locations

- Surefire XML reports: `target/surefire-reports/`
- JaCoCo HTML report: `target/site/jacoco/index.html`
