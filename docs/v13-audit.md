# RailMind v13 — Requirement Audit

Source-verified against actual code on 2026-09-09 (not trusted from README or
prior-pass claims). Status values: IMPLEMENTED, PARTIALLY_IMPLEMENTED,
MISSING, BROKEN, FAKE/STUB, NEEDS_INTEGRATION, NEEDS_TESTING.

| Requirement | Status | Evidence |
|---|---|---|
| Auth (register/login/refresh/logout, rotation, revocation, reuse detection) | IMPLEMENTED | `auth/AuthService.java`, `auth/RefreshToken*.java` |
| Email verification, password reset/change | IMPLEMENTED | `auth` package, `V7` migration |
| RBAC (USER/ADMIN/SUPPORT_AGENT) | IMPLEMENTED | `SecurityConfig`, `@PreAuthorize` across controllers |
| Login lockout | IMPLEMENTED | `LoginAttemptService` (Redis-backed) |
| Station/train search, availability, fare | IMPLEMENTED | `train/TrainSearchService.java` |
| Journey ranking (Best Overall/Cheapest/Fastest/Overnight/Reliable) | IMPLEMENTED | `train/JourneyRankingService.java` |
| Booking, seat holds, hold expiry, concurrency safety | IMPLEMENTED | `booking/BookingService.java`, `SeatHoldExpiryService.java`, tested in `BookingConcurrencyIT` |
| Saved passengers, travel preferences | IMPLEMENTED | `profile` package, `V7` migration |
| Payment simulation, PNR | IMPLEMENTED | `payment`, `booking/BookingEntities.Pnr` |
| Cancellation/refund (deterministic policy) | IMPLEMENTED | `cancellation/RefundPolicyService.java` |
| Idempotency (booking, payment) | IMPLEMENTED | ownership-scoped, tested in `BookingIdempotencyOwnershipTest` |
| Notifications | IMPLEMENTED | `notification` package, hooked into payment/cancellation/disruption |
| AI provider abstraction (OpenAI/Anthropic/mock) | IMPLEMENTED | `ai/provider/*` |
| AI tool calling, 13 tools | IMPLEMENTED | `ai/tools/ToolExecutor.java` |
| RAG (pgvector, embeddings, grounded answers, sources) | IMPLEMENTED | `rag` package, `V8` migration |
| Kafka (producer/consumer) | IMPLEMENTED | `disruption/TrainEvent{Producer,Consumer}.java` |
| **Kafka retry + backoff + dead-letter topic** | **IMPLEMENTED this pass** | `config/KafkaConfig.java`, `disruption/DeadLetterConsumer.java`, proven in `KafkaDlqIT` |
| Disruption management, Trip Guardian, human-approved rebooking | IMPLEMENTED | `disruption/{DisruptionService,RebookingService}.java` |
| Admin CRUD | PARTIALLY_IMPLEMENTED | Only `admin/AdminStationController.java`, `AdminTrainController.java` (metadata-only create/update). No routes/schedules/coaches/seats/fares/users/bookings/refunds admin endpoints. |
| Support Agent endpoints | IMPLEMENTED | `support/SupportController.java`, RBAC-tested in `SupportAndAdminRbacIT` |
| **Admin analytics backend** | **IMPLEMENTED this pass** | `admin/AnalyticsService.java` (real SQL aggregation via `JdbcTemplate`), `admin/AnalyticsController.java` (all 8 spec endpoints), proven against real inserted rows in `AnalyticsServiceIT`. RAG query volume is now tracked too (`rag/PolicyQueryLog.java`, `V9` migration) — previously nothing logged RAG usage at all, so `/admin/analytics/rag` would have had nothing real to report.
| Frontend: auth, dashboard, train search, booking→payment→PNR→ticket, bookings list/detail, notifications, profile, AI assistant | IMPLEMENTED | `frontend/src/pages/**` |
| Frontend: standalone `/trip-guardian` page | MISSING | Trip Guardian is reachable only from within `/bookings/:id`, not its own route |
| Frontend: `/pnr` page | MISSING | No backend endpoint for a plain user to look up by PNR alone (only `/support/pnr/{pnr}`, role-gated) — would need a new backend endpoint first, not just a page |
| Frontend: `/admin/*` routes | MISSING | No admin frontend at all |
| Frontend: `/support/*` routes | MISSING | No support frontend at all |
| Five distinct AI agent classes | PARTIALLY_IMPLEMENTED | Real workflows exist (journey ranking, booking draft, RAG, disruption, Trip Guardian) but as one `AiOrchestratorService` + tool registry, not five separate named classes |
| Prometheus/Grafana | MISSING | Only default Spring Actuator (health/info/metrics), no Prometheus registry wired, no Grafana in docker-compose |
| Correlation IDs beyond Kafka consumer | PARTIALLY_IMPLEMENTED | MDC correlation ID exists in `TrainEventConsumer` only, doesn't flow through HTTP→AI→tool |
| CI/CD | PARTIALLY_IMPLEMENTED | GitHub Actions workflow exists for backend only, no frontend lint/test/build steps |
| `docs/` folder | PARTIALLY_IMPLEMENTED | This file is the first one; architecture.md, api.md, ai-agents.md, rag.md, database.md, security.md, deployment.md, booking-flow.md, kafka-events.md, redis.md, testing.md, troubleshooting.md do not exist yet |
| Docker (frontend/backend/postgres+pgvector/redis/kafka) | IMPLEMENTED | `docker-compose.yml` |
| Docker (prometheus/grafana) | MISSING | Not in `docker-compose.yml` |
| Business rules (no double-booking, no past-date booking, ownership checks, no duplicate refund, etc.) | IMPLEMENTED | Enforced across `BookingService`/`CancellationService`/`PaymentService` |

## Agreed priority order for remaining work

1. ~~Admin analytics backend (real DB aggregation)~~ — done, see above
2. Full admin CRUD + admin frontend
3. `/trip-guardian` + `/pnr` pages (the latter needs a new backend endpoint)
4. Support frontend
5. Split AI orchestrator into 5 named agent classes
6. Observability (Prometheus/Grafana, correlation ID propagation)
7. CI/CD frontend steps
8. Remaining `docs/` files
