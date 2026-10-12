# RailMind V14 — Complete Feature Audit

Source-code inspection of `railmind-v14.zip` against every one of the 80
numbered sections in `RailMind-Development.docx`. This file was originally
scoped as a stand-alone audit; it is being formalized here (retroactively)
because Phase 1 backend work referenced it directly. Classifications:
**IMPLEMENTED / PARTIALLY_IMPLEMENTED / MISSING / BROKEN / FAKE_OR_STUB /
NEEDS_INTEGRATION / NEEDS_TESTING**.

A fuller per-section coverage table (all 80 spec sections) lives in
`docs/v15-roadmap.md`. This file focuses on the 19 requested inspection
areas plus a running log of what's changed since.

---

## 1. Backend

**IMPLEMENTED.** Modular monolith (Spring Boot 3.3.4 / Java 21), packages
per domain (`auth`, `booking`, `payment`, `cancellation`, `train`, `station`,
`disruption`, `notification`, `ai`, `rag`, `admin`, `support`, `audit`).
No stub/TODO methods found anywhere in the backend source tree (verified by
exhaustive grep). Code quality is materially higher than the repo's own
README suggests (see NEEDS_TESTING note below).

**NEEDS_TESTING.** The backend has never been confirmed to actually
compile. The README states this explicitly ("I still cannot compile or run
this"), and this environment cannot verify it either — Maven Central
(`repo.maven.apache.org`) returns a hard `403 Forbidden` from this
sandbox's network egress policy. **Hard evidence found during Phase 1:**
`BookingConcurrencyIT.java` called `new PassengerRequest("Test Passenger",
30, "M", "LOWER")` — a 4-argument call against a 5-field record — which
would not have compiled. This is a genuine, pre-existing bug, now fixed
(see Phase 1 log below). Its presence confirms the test source tree was
never actually built.

## 2. Frontend

**PARTIALLY_IMPLEMENTED — and this section describes less than what was
once built.** Admin frontend (13 pages), support frontend (4 pages),
`/pnr`, and `/trip-guardian` were built in Phases 3–5, but were lost to a
mid-session container reset during Phase 7 and could not be restored (no
saved backup zip included the `frontend` directory before that point — see
`docs/phase7-observability-report.md` §0 for the full incident account).
The description below is accurate for the frontend **as it exists in the
repository right now**, which is the original pre-Phase-3 state, not a
reflection of backend capability — every backend API Phases 3–5 built
(admin, support, PNR, Trip Guardian) is intact and unaffected; only its
frontend consumer is missing and needs to be rebuilt.

React 18 + TypeScript + Vite + Tailwind + Zustand. Confirmed to build,
lint, and test cleanly (`npm run build`/`lint`/`test` all pass, re-verified
after restoration). Auth, search, booking, payment, PNR-in-booking-detail,
and profile pages are complete and polished. Landing page and dashboard
are intentionally minimal stubs (landing page is Hero-only; dashboard has
no upcoming/past split, no saved journeys, no AI recommendations panel).
Zero `/admin`, `/support`, `/trip-guardian`, or standalone `/pnr` routes
exist **in the current repository state**. Tailwind `darkMode: 'class'` is
configured but **zero** `dark:` classes are used anywhere — dark mode does
not functionally exist. No `@testing-library/react` dependency — component/
form tests are structurally impossible until it's added.

## 3. Database migrations

**IMPLEMENTED**, with one normalization gap. All core tables exist and are
seeded (15 stations, 10 trains, 4 travel classes: SL/AC3/AC2/AC1, 30 days of
schedules). `roles` is a denormalized comma-separated string column on
`users` rather than normalized `roles`/`user_roles` tables (spec section 5
asks for the latter) — functionally correct, RBAC works, but not literally
schema-normalized. No migration for passenger identity fields or dynamic
pricing rules exists (spec sections 16–17) — tracked as MISSING. **Updated
in Phase 2:** migrations V10–V13 add `kafka_event_log`, correlation-ID
columns on `audit_logs`/`ai_tool_executions`/`kafka_event_log`, demo
bookings/disruption/notification seed data, and `active` flags on
stations/trains/coaches. All 13 migrations verified to apply cleanly in
order against a real local Postgres+pgvector instance — see
`docs/phase2-database-report.md` for the full verification record. The
`roles` normalization gap above is a **deliberate, disclosed decision to
skip** (see that report): the risk of touching `SecurityConfig`/
`CustomUserDetailsService` without a working compiler available outweighs
the benefit of a cosmetic schema change.

## 4. API endpoints

**PARTIALLY_IMPLEMENTED.** Public train search/details/availability/fare,
auth, bookings, payments, cancellations, notifications, profile, RAG policy
search, disruption/Trip Guardian, and a slice of admin (trains, stations,
analytics, disruption simulation) and support (booking/PNR lookup) all
exist and are well-formed (DTOs in/out, no raw entities exposed, consistent
`ApiError` shape via `GlobalExceptionHandler`). **Missing** prior to Phase
1: `GET /api/v1/pnr/{pnr}` (spec section 61's literal endpoint) — now
**IMPLEMENTED** (see Phase 1 log). Still **MISSING**: admin route/coach/
seat/pricing/user/booking management endpoints (tracked for Phase 3, out of
backend-core scope). One **BROKEN** item found and fixed: `POST
/api/v1/bookings` returned `200 OK` instead of `201 Created` — now fixed.

## 5. Authentication and RBAC

**IMPLEMENTED.** JWT + refresh tokens, BCrypt, login-attempt lockout via
Redis, centralized RBAC via `SecurityConfig`'s `authorizeHttpRequests`
(`/api/v1/admin/**` → `ROLE_ADMIN`, `/api/v1/support/**` →
`ROLE_SUPPORT_AGENT`/`ROLE_ADMIN`), reinforced by `@PreAuthorize` on every
admin/support controller as defense-in-depth. **One gap found and fixed**:
`AdminDisruptionController` was the only admin controller missing the
`@PreAuthorize` annotation (still correctly blocked by the URL-pattern rule,
but inconsistent with the codebase's own defense-in-depth convention) — now
fixed.

## 6. Booking and payment

**IMPLEMENTED**, with real business-rule gaps closed in Phase 1.
Transactional booking creation, optimistic-locking seat holds with bounded
retry, idempotency-key-scoped booking and payment creation, ownership
checks throughout, seat-hold expiry job. **Gaps found and fixed in Phase
1:** (a) no check preventing booking a schedule whose departure had already
elapsed (spec section 70: "Cannot book in the past") — now enforced; (b)
berth preference was captured on `BookingPassenger` but never actually used
to select a seat — `holdOneSeat` now prefers a matching-berth-type seat when
one is open, falling back gracefully otherwise; (c) no validation
restricting `travelClass` to real values or capping passengers per booking
— both added as `@Pattern`/`@Size` constraints.

## 7. Cancellation and refund

**IMPLEMENTED**, with one business-rule gap closed. Deterministic
time-based refund policy engine, full-refund path for disruption-driven
cancellations, seat release, audit logging, notifications. **Gap found and
fixed:** no check preventing cancellation of an already-completed journey
(spec section 70) — added to the user-initiated `cancel()` path only,
delay-aware (a delayed-but-not-yet-departed train can still be cancelled),
deliberately excluded from `cancelDueToDisruption()` so the disruption/
rebooking workflow is unaffected.

## 8. AI agents and tools

**IMPLEMENTED.** 15 tools now routed through 5 named specialist agents
(`JourneyPlanningAgent`, `BookingAgent`, `PolicyRagAgent`, `DisruptionAgent`,
`TripGuardianAgent`), each with a declared, verified permission set, behind
a fail-closed `AgentRouter` that enforces one-owner-per-tool at startup and
refuses any unregistered tool at runtime before `ToolExecutor` is ever
called. HITL confirmation for sensitive actions and per-tool ownership
checks are unchanged and re-verified. Full detail, including the tool
ownership table and every safety property traced against actual source,
is in `docs/phase6-ai-agent-report.md`. Backend compile/test remains
NOT VERIFIED (Maven Central 403, consistent with every prior phase) —
verification instead used source inspection, PostgreSQL execution, and
hand-traced test logic.

## 9. RAG

**IMPLEMENTED.** Policy documents chunked and embedded, hashing-based
embedding provider, category-scoped retrieval (`CANCELLATION`, `REFUNDS`,
`BOOKING`, `WAITING_LIST`, `QUOTA`, `PASSENGER`, `BAGGAGE`,
`GENERAL_TRAVEL` — matches spec's category list), covered by
`PolicyRetrievalServiceTest`/`DocumentChunkerTest`/`HashingEmbeddingProviderTest`.

## 10. Kafka

**IMPLEMENTED.** All 6 topics now exist: `train-events` (prior phases,
unchanged) plus `booking-events`, `payment-events`, `notification-events`,
`ai-events`, `audit-events` (Phase 7). Every event carries
eventId/eventType/eventVersion/correlationId/occurredAt/payload. Retry/
backoff/DLQ apply to all six via the existing generic error handler.
Idempotent, upsert-based consumers throughout. See
`docs/phase7-observability-report.md` for full detail, including a
disclosed, deliberate scope reduction: booking/payment event publishing
is routed through `AuditService` rather than instrumented directly inside
`BookingService`/`PaymentService`, to avoid touching those services'
several existing direct-construction test files without a compiler
available to verify the fix.

## 11. Redis

**IMPLEMENTED.** Station/train-detail caching with proper eviction, login-
attempt-lockout counters, no seat-availability caching (re-confirmed in
Phase 7 — the double-booking risk that would create was never present).
Cache hit/miss *counters* (as opposed to existing caching behavior) were
not added in Phase 7 — noted as a minor gap in that phase's report.

## 12. Notifications

**IMPLEMENTED.** Fires correctly and synchronously on booking confirmation,
payment failure, cancellation, and refund, with a real (if in-memory/mock)
`EmailSender` abstraction. Delivery is synchronous rather than Kafka-driven
(see Kafka section above) — a valid, working design, just not the spec's
literal event-driven diagram. No functional bug found; Phase 1 did not
modify notification wiring.

## 13. Disruption management

**IMPLEMENTED.** `AdminDisruptionController` (now also audit-logged and
`@PreAuthorize`-protected — see Phase 1 log) publishes a real Kafka event,
`TrainEventConsumer`/`DisruptionService` process it, alternative schedules
are found and surfaced as `TripAlert`s.

## 14. Trip Guardian

**IMPLEMENTED (backend).** Enable/disable toggle
(`BookingService.setTripGuardianEnabled`, wired through
`TripGuardianController`), trip-alert history, and rebooking approval
(`RebookingService.approveRebooking`) all exist and are well-tested
(`RebookingServiceTest`). The safety property — secure the new seats first,
only cancel the old booking after success — is implemented correctly. No
frontend page exists yet (tracked for Phase 5, frontend work, not started).

## 15. Admin functionality

**PARTIALLY_IMPLEMENTED.** Train/station metadata CRUD and real
(non-mocked) analytics aggregation exist, each properly RBAC-gated. Route,
coach, seat, pricing, user (block/role-change), and booking management from
the admin side are all **MISSING** — tracked for Phase 3, explicitly out of
scope for this backend-core-completion pass per the agreed roadmap
sequencing.

## 16. Support functionality

**PARTIALLY_IMPLEMENTED.** Exact-ID booking lookup and exact-PNR lookup
both exist, correctly role-gated (`SUPPORT_AGENT`/`ADMIN`) and audit-logged
per lookup. Search-by-customer and a read view of AI tool-execution history
are **MISSING** — tracked for Phase 4.

## 17. Tests

**PARTIALLY_IMPLEMENTED / was BROKEN, now improved.** 24 backend test files
after Phase 1 (was 20; 4 added this pass) covering RBAC, idempotency,
concurrency (Testcontainers), refund policy, RAG, disruption, and now the
new business rules. One genuinely broken pre-existing test
(`BookingConcurrencyIT`'s malformed `PassengerRequest` constructor call) was
found and fixed. Frontend has exactly one test file (an API client test);
no component/workflow tests exist or are possible without
`@testing-library/react` (tracked for Phase 8).

## 18. Docker and CI/CD

**MISSING (CI/CD) / PARTIALLY_IMPLEMENTED (Docker).**
`docker-compose.yml` brings up backend, frontend, Postgres (pgvector),
Redis, and Kafka correctly; no Prometheus/Grafana services. **No
`.github/workflows` directory exists at all** — confirmed by direct
inspection of the zip contents, not just absence of output. Both tracked
for Phase 7/9, infrastructure work out of scope for this pass.

## 19. Documentation

**PARTIALLY_IMPLEMENTED.** `README.md` exists but is an informal
pass-by-pass engineering changelog rather than the spec-shaped document
(Project overview / Architecture / AI architecture / RAG architecture / etc.)
requested in spec section 52. None of the twelve `docs/*.md` files the spec
calls for (`architecture.md`, `api.md`, `ai-agents.md`, `rag.md`,
`database.md`, `security.md`, `deployment.md`, `booking-flow.md`,
`kafka-events.md`, `redis.md`, `testing.md`, `troubleshooting.md`) exist yet.
Tracked for Phase 9. This audit file and `docs/v15-roadmap.md` are the first
two `docs/*.md` files to exist.

---

## Phase 1 (Backend Core Completion) — change log

Applied against the codebase described above. Scope was deliberately
limited to real, verifiable gaps in booking/payment/cancellation/PNR/
ownership/admin-support-authorization/disruption/validation/audit-logging —
per the agreed Phase 1 boundaries in `docs/v15-roadmap.md`. Kafka topic
expansion, admin/support new surface area, AI agent separation, dark mode,
and CI/CD remain explicitly out of scope for this pass.

### Fixed
1. **Cannot book a departed schedule** (spec 70) — `BookingService.createBooking`
   now rejects a schedule whose original departure date/time has already
   elapsed, regardless of its stored status.
2. **Cannot cancel a completed journey** (spec 70) — `CancellationService.cancel`
   now rejects cancelling a booking whose (delay-adjusted) departure has
   already passed. Deliberately scoped to the user-initiated path only;
   `cancelDueToDisruption` is unaffected.
3. **Berth preference was decorative, not honored** — `BookingService.holdOneSeat`
   now prefers a seat matching the passenger's requested berth type when one
   is open, falling back to any available seat in the class otherwise.
4. **Missing public PNR lookup** (spec 61: `GET /api/pnr/{pnr}`) — new
   `PnrController` + `BookingService.getBookingByPnr`, user-scoped, returns
   an identical 404 whether the PNR doesn't exist or belongs to someone
   else (no enumeration leak).
5. **Wrong HTTP status on booking creation** — `POST /api/v1/bookings` now
   returns `201 Created` with a `Location` header instead of `200 OK`.
6. **Missing class/passenger-count validation** (spec 70: "cannot select
   invalid class" / "cannot exceed passenger limits") — `CreateBookingRequest`
   now validates `travelClass` against the four real classes
   (`SL|AC3|AC2|AC1`) and caps passengers at 6 per booking.
   `PassengerRequest.berthPreference` is now validated against the five real
   berth types when provided.
7. **`AdminDisruptionController` missing defense-in-depth authorization and
   audit logging** — added `@PreAuthorize("hasRole('ADMIN')")` (every other
   admin controller already had it) and an `AuditService.log(...)` call for
   the simulate-disruption action, which is sensitive and previously had no
   trail of its own outside the Kafka event payload.
8. **Pre-existing broken test** — `BookingConcurrencyIT` called
   `PassengerRequest`'s 5-field record constructor with only 4 arguments
   (missing `savedPassengerId`). This would not have compiled. Fixed by
   adding the missing `null` argument. This is concrete, first-hand
   evidence that the backend test source tree has never actually been
   built, consistent with the README's own admission.

### Reviewed and confirmed already correct (no change needed)
- Transaction boundaries across `BookingService`, `PaymentService`,
  `CancellationService`, `RebookingService`, `DisruptionService`,
  `NotificationService` — every mutating public method is `@Transactional`.
- Idempotency on booking creation and payment (owner-scoped, replay-safe).
- Optimistic-locking seat concurrency with bounded retry.
- Ownership checks on every user-facing booking/payment/cancellation/
  Trip-Guardian endpoint.
- Admin and support RBAC (URL-pattern + method-level, consistent except for
  the one `AdminDisruptionController` gap above, now fixed).
- Global exception handling → consistent `ApiError` JSON, correct HTTP
  status per exception type, no leaked stack traces.
- No stub/TODO/placeholder methods anywhere in the backend.

### Explicitly NOT done this pass (by design, per roadmap phase boundaries)
- Kafka topic expansion beyond `train-events` (Phase 7)
- General API rate limiting beyond login lockout (Phase 8)
- Admin route/coach/seat/pricing/user/booking management endpoints (Phase 3)
- Support search-by-customer / AI-history view (Phase 4)
- AI five-agent structural separation (Phase 6)
- `roles`/`user_roles` normalization (Phase 2)
- Any frontend work (explicitly deferred per instruction)

### Verification status — read carefully
**Full `mvn compile`/`mvn test` could not be executed in this environment.**
Maven was installed successfully via `apt-get`, but Maven Central
(`repo.maven.apache.org`) is not on this sandbox's network allow-list and
returns `403 Forbidden` on every dependency-resolution attempt (confirmed
directly, not assumed). No local `.m2` cache pre-existed either. Every
change above was instead verified by:
- Full manual re-read of every modified file for syntax/type correctness
  (import correctness, method signatures, field names cross-checked
  against entity/DTO definitions).
- Cross-referencing every entity field, repository method, and DTO record
  used against its actual declaration in the codebase.
- Discovering and fixing one genuine pre-existing compile error in the test
  suite (see item 8 above), which is direct evidence this level of manual
  review can and did catch real mistakes.

This is **not** a substitute for a real compiler/test run. The next
opportunity to run `mvn test` in an environment with Maven Central access
(or a pre-warmed `.m2` cache) should be treated as a required, not optional,
verification step before this code is considered production-ready.

---

## Phase 2 (Database and Seed Data) — change log

Full detail, including every SQL verification query and its actual result,
lives in `docs/phase2-database-report.md`. Summary here for continuity with
the Phase 1 log above.

### Fixed / added
1. **`V2__seed_data.sql` reserved-word syntax bug** — `generate_series(0, 29)
   AS d(offset)` renamed to `d(day_offset)`; `offset` is a reserved word in
   Postgres. This is hard evidence V2 could never have run against real
   Postgres before this fix.
2. **`V10__kafka_event_tracking_and_correlation_ids.sql`** (new) — durable
   `kafka_event_log` table plus `correlation_id` columns on `audit_logs` and
   `ai_tool_executions`.
3. **`V11__demo_bookings_disruptions_notifications.sql`** (new) — 3 demo
   bookings (plain confirmed, confirmed+Trip-Guardian-enabled+active alert,
   cancelled+refunded) for the existing demo user, fully connected
   (passengers, seats, PNRs, payments/cancellations/refunds, notifications).
   Fixed a real idempotency gap found by deliberately forcing a duplicate
   application: two standalone notification `INSERT` statements had no
   constraint protecting them (unlike the booking inserts, which correctly
   fail via `bookings_idempotency_key_key`), so a forced re-run created 2
   duplicate notification rows. Added `WHERE NOT EXISTS` guards; re-tested
   the forced-duplicate scenario and confirmed zero duplicates result now.
4. **`V13__kafka_event_log_correlation_id.sql`** (new) — `kafka_event_log`
   was missing its own `correlation_id` column (V10 only added it to
   `audit_logs`/`ai_tool_executions`); added, since `event_id` and
   `correlation_id` are conceptually different (an event's own identity vs.
   what caused it).
5. **`CorrelationIdFilter`** (new) — the HTTP-level correlation-ID mechanism
   that didn't exist before this pass: accepts `X-Correlation-Id` or
   generates one, puts it in MDC for the request's duration, echoes it back
   in the response header, always clears MDC in a `finally` block. Registered
   as a plain servlet filter outside Spring Security's own chain entirely —
   it makes no authorization decisions and cannot affect existing auth
   behaviour.
6. **`TrainEventMessage`** gained a `correlationId` field (distinct from
   `eventId`), captured from MDC inside the existing `newEvent(...)` factory
   — no call site needed to change.
7. **`AiToolExecution`** entity was missing `correlationId` even though the
   DB column existed after V10 — added the field and wired
   `AiOrchestratorService.logToolExecution` to populate it from MDC. This
   was a genuine "column exists, nothing writes to it" gap.
8. **`TrainEventConsumer`** now uses the event's own `correlationId` (not
   `eventId`) for MDC/log correlation and persists it on `KafkaEventLog`.
9. **`DeadLetterConsumer`** now attempts to recover the original
   `eventId`/`correlationId` by re-parsing the dead-lettered payload (the
   normal case, since most DLQ arrivals failed in business logic, not
   deserialization), falling back to synthetic identifiers only when the
   payload is genuinely unparseable.

### Verified (real execution, not static review)
- PostgreSQL 16 + pgvector installed and running in this sandbox.
- All 13 migrations (V1–V13) applied cleanly, in order, on a freshly reset
  schema — twice, once before and once after the V11 idempotency fix.
- Full relational/integrity verification via `docs/sql/phase2-verification.sql`
  (28 checks) run against the live database — all passed.
- Deliberately forced a duplicate application of V11 to test the "no
  invalid duplicates on re-run" requirement directly rather than assuming
  it: found a real gap (see item 3 above), fixed it, re-tested, confirmed
  clean.
- 5 new backend test files written (`CorrelationIdFilterTest`,
  `TrainEventConsumerTest`, `DeadLetterConsumerTest`, `AuditServiceTest`,
  plus reuse of the existing hand-mocked style) — **not executable in this
  environment**, same Maven Central `403 Forbidden` blocker as Phase 1.

### Deliberately not done
- `roles`/`user_roles` normalization — considered, explicitly skipped as a
  disclosed risk/reward decision (see `docs/phase2-database-report.md`).
- Passenger identity fields, dynamic pricing tables — out of Phase 2's
  scope as defined by this pass's instructions.

---

## Phase 6 (AI Agent Architecture and Safety) — change log

Full detail in `docs/phase6-ai-agent-report.md`. Summary here for
continuity with the Phase 1/2 logs above.

### Added
- 5 agent classes + `Agent` interface + `AbstractToolAgent` shared
  enforcement + `AgentRouter` (fail-closed, one-owner-per-tool enforced at
  construction) + `AgentDtos` (`AgentToolRequest`/`AgentToolResponse`).
- `ai_tool_executions.agent_name` (V14 migration, verified against real
  Postgres via a full clean V1–V14 re-run plus a simulated insert/read
  round-trip).

### Fixed
- `ToolExecutorAuthorizationTest` was a second genuinely pre-existing
  broken test (missing 2 constructor args - same class of bug as Phase 1's
  `BookingConcurrencyIT` discovery), found and fixed this phase.

### Verified this closure pass (not just carried over from the initial report)
- Tool ownership table re-derived from actual source (grep against
  `ToolExecutor`'s case labels and each agent's `ALLOWED_TOOLS`), not
  assumed: 15/15 tools, each owned exactly once.
- Fail-closed routing, admin-tool isolation, mutation safety (drafts never
  call a write method), no-fabricated-data, and RAG insufficient-info
  fallback all re-traced line by line against current source.
- One disclosed, pre-existing, out-of-scope note: `ToolExecutor` reads
  several repositories directly (read-only, parameterized) rather than
  exclusively through a service layer — not introduced or changed this
  phase, flagged rather than silently passed over.
- Full clean migration re-run V1–V14; `agent_name` column confirmed
  present with correct type/nullability/index; real round-trip write/read
  test performed and cleaned up.
- Backend compile re-attempted and re-confirmed blocked (Maven Central 403,
  identical error to every prior phase).

### Final Phase 6 status: PARTIAL — implementation complete and verified by
every means available in this environment; backend compile/test execution
remains blocked.

---

## Phase 7 (Kafka, Redis and Observability) — change log

Full detail in `docs/phase7-observability-report.md`, including a critical
incident account (§0: mid-session container reset lost all Phase 3–5
frontend work — not recoverable, needs a dedicated rebuild).

### Added
- `booking-events`, `payment-events`, `notification-events`, `ai-events`,
  `audit-events` Kafka topics, sharing one `EventEnvelope` record (all 6
  required fields), one `DomainEventPublisher`, one `DomainEventConsumer`
  (idempotent upsert, all 5 topics), one `DomainEventDeadLetterConsumer`
  (topic-pattern DLQ, all 5 topics). `train-events` unchanged.
- `micrometer-registry-prometheus` dependency (the `/actuator/prometheus`
  endpoint was already configured but had no registry to back it).
- `AppMetrics` — booking/payment/kafka/AI/RAG/error counters, wired into
  `GlobalExceptionHandler`, the new Kafka consumers, and `AiOrchestratorService`
  (all zero/low-risk integration points — see report §9 for why
  booking/payment/RAG counters are defined but not wired at the service
  level).
- `logstash-logback-encoder` + `logback-spring.xml` — structured JSON logs
  with `correlationId` as a real field.
- Prometheus + Grafana in `docker-compose.yml`, with provisioned datasource
  and a 9-panel dashboard (`infrastructure/grafana/provisioning/`).

### Verified
- Full clean V1–V14 migration re-run (after reinstalling Postgres
  post-incident): all applied cleanly. 28-check integrity script: all
  passed. No new migration needed.
- Backend compile re-confirmed blocked (Maven itself had to be reinstalled
  post-incident; same `403 Forbidden` as every prior phase once it was).
- Restored original frontend confirmed to build/lint/test cleanly — but see
  the incident note above for what's missing from it.

### Final Phase 7 status: PARTIAL — see `docs/phase7-observability-report.md`
§13 for the full rationale.
