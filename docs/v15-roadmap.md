# RailMind v15 — Prioritized Implementation Roadmap

Source: gaps confirmed by source-code inspection against every one of the 80
numbered sections in `RailMind-Development.docx`, cross-checked with
`docs/v13-audit.md` (prior pass) and the v14 audit pass. All items below are
things that are **MISSING / PARTIALLY_IMPLEMENTED / BROKEN / FAKE_OR_STUB /
NEEDS_INTEGRATION / NEEDS_TESTING** in the current `railmind-v14` codebase —
nothing here re-does work that already exists and works (auth, RBAC, train
search/ranking, booking/payment/PNR, cancellation/refund, notifications,
Kafka DLQ, RAG core, Trip Guardian backend, support view/lookup, analytics
backend, and the wired frontend pages all stay as-is).

Nothing in this file has been implemented yet. This is planning only.

---

## Coverage check — every documented requirement accounted for

Before laying out phases, confirming no section of the spec was dropped from
the gap analysis:

| Spec section(s) | Status | Where it lands below |
|---|---|---|
| 1–4 Vision/journey/stack/architecture | No gaps — modular monolith matches | — |
| 5 Database architecture | Gap: `roles`/`user_roles` not normalized; passenger identity field missing | Phase 2 |
| 6 Auth/RBAC | Gap: no general API rate limiting | Phase 8 |
| 7 Landing page | Gap: only Hero exists, 8 of 9 sections missing | Phase 1 |
| 8–9 Search engine / train details | No gaps | — |
| 10–14 AI journey/travel/booking agent, HITL, seat rec | No gaps in behavior; structural gap is agent separation | Phase 6 |
| 15–17 Booking, passengers, fare | Gap: passenger identity field, dynamic pricing | Phase 1 |
| 18–20 Payment, PNR, digital ticket | Gap: no public `/api/v1/pnr/{pnr}` lookup | Phase 1, 5 |
| 21 Cancellation/refund | No gaps | — |
| 22–23 RAG, AI support | No gaps | — |
| 24–25 Disruption, Trip Guardian | Backend complete; no dedicated frontend page | Phase 5 |
| 26 Notifications | Gap: not Kafka-driven as spec's example diagram shows | Phase 7 |
| 27 User dashboard | Gap: sections mostly stubbed (Saved Journeys, AI Recommendations, Trip Guardian) | Phase 1 |
| 28 Admin dashboard | Gap: only train/station metadata CRUD exists; no coach/seat/pricing/route/user/booking admin | Phase 3 |
| 29 Admin analytics | No gaps (backend); frontend charts missing | Phase 3 |
| 30 Redis | No gaps | — |
| 31 Kafka | Gap: only `train-events` topic; 4 of 5 topics from spec unused | Phase 7 |
| 32 AI event logging | No gaps | — |
| 33 AI security architecture | No gaps | — |
| 34 Frontend design requirements | Gap: dark mode configured but 0% implemented; no animation library | Phase 1, 6 (as it touches AI UI), spread across frontend work |
| 35 Frontend routes | Gap: `/pnr`, `/trip-guardian`, all `/admin/*` missing from router | Phase 3, 5 |
| 36 User profile | No gaps | — |
| 37 Error handling | No gaps | — |
| 38 Observability | Gap: no Prometheus/Micrometer registry, no Grafana | Phase 7 |
| 39 Testing | Gap: no frontend component/form/workflow tests, no RTL dependency | Phase 8 |
| 40 Booking concurrency | No gaps | — |
| 41 Idempotency | No gaps (booking/payment); cancellation idempotency is a minor optional add | Phase 1 (optional) |
| 42 API documentation | Springdoc present; needs endpoint-group tags/descriptions completed | Phase 9 |
| 43–44 Seed data / demo accounts | No gaps | — |
| 45–46 Docker / Docker architecture | Gap: no Prometheus/Grafana services | Phase 7, 9 |
| 47 CI/CD | Gap: **no `.github/workflows` exists at all** | Phase 9 |
| 48 Env config | No gaps | — |
| 49–51 Project/package/frontend structure | No gaps | — |
| 52 Documentation | Gap: README is an informal pass-log, not spec-shaped; `docs/architecture.md`, `api.md`, `ai-agents.md`, `rag.md`, `database.md`, `security.md`, `deployment.md`, `booking-flow.md`, `kafka-events.md`, `redis.md`, `testing.md`, `troubleshooting.md` all missing | Phase 9 |
| 53–54 Five AI agents / agent architecture | Gap: one orchestrator class, not five named agents | Phase 6 |
| 55–58 RAG architecture, AI memory, tool/output validation | No gaps | — |
| 59–61 Modern UI pages, AI assistant UI, train result card | Gaps only in admin/support/trip-guardian/PNR pages (already tracked) | Phase 3, 4, 5 |
| 62–64 Booking flow UI, payment UI, Trip Guardian UI | Booking/payment UI complete; Trip Guardian UI missing | Phase 5 |
| 65 Admin AI analytics | Backend endpoint exists; no chart frontend | Phase 3 |
| 66 Audit logging | Gap: no admin endpoint to **view** audit logs; AI tool calls logged to a separate table, not the shared `audit_logs` table | Phase 3, 8 |
| 67 Support agent role | Gap: only exact-ID booking/PNR lookup; no search-by-customer, no "review AI actions" view | Phase 4 |
| 68 Search performance | No gaps | — |
| 69 API design | No gaps | — |
| 70 Business rules | No gaps | — |
| 71 Demo data/simulation | Admin `/simulate` endpoint exists; no admin UI trigger | Phase 3 |
| 72 Demo scenario (flagship) | Fully achievable backend-only today; blocked end-to-end by missing Trip Guardian page | Phase 5, 10 |
| 73 Final verification checklist | This *is* Phase 10 | Phase 10 |
| 74–76 Code quality, performance, accessibility | No structural gaps found; accessibility unverified (no automated check exists) | Phase 8 |
| 77–78 Responsive design, dark mode | Dark mode gap tracked above; responsiveness unverified without visual test | Phase 8 |
| 79–80 Search experience, AI+normal search integration | No gaps — both flows already coexist | — |

Every section is accounted for. Nothing from the spec was silently dropped.

One standing risk carried into every phase: **the backend has never been
compiled or run** (the repo's own README says so, and this sandbox has no
Maven Central access to verify it either). Phase 1 therefore opens with a
build verification step before any new feature work, and Phase 10 repeats
full build verification as the final gate.

---

## Phase 1: Backend core completion

**Exact features**
- Verify the backend actually compiles and boots (blocking pre-check before any other phase-1 work).
- Add passenger identity field (`identity_type`, `identity_number` — demo-safe, e.g. masked/last-4-digits only) to passenger records.
- Add a simple deterministic dynamic-pricing rule (e.g. quota- or occupancy-based fare multiplier) to the fare engine, still backend-sourced-of-truth.
- Add a public PNR lookup endpoint (`GET /api/v1/pnr/{pnr}`) scoped to the requesting user's own bookings (support's `/support/pnr/{pnr}` stays role-gated and separate).
- Fill out the landing page: Popular Routes, AI-Powered Features, How RailMind Works, Travel Statistics, Safety/Security, Testimonials, FAQ, Footer sections.
- Fill out the dashboard: split Upcoming/Past Trips, Saved Journeys, AI Recommendations panel (real data, not placeholder).

**Backend files**
- `backend/src/main/java/com/railmind/booking/BookingEntities.java` (Passenger identity fields)
- `backend/src/main/resources/db/migration/V10__passenger_identity.sql` (new)
- `backend/src/main/java/com/railmind/train/Fare.java`, `JourneyRankingService.java`, new `PricingRuleService.java`
- `backend/src/main/java/com/railmind/booking/BookingController.java` or new `PnrController.java`
- `backend/src/main/java/com/railmind/booking/dto/BookingDtos.java`

**Frontend files**
- `frontend/src/pages/LandingPage.tsx` (expand)
- `frontend/src/pages/DashboardPage.tsx` (expand)
- New: `frontend/src/components/landing/*` (PopularRoutes, Testimonials, FAQ, Footer, etc.)
- `frontend/src/pages/booking/BookingPage.tsx` (passenger identity fields)
- `frontend/src/api/bookings.ts`, new `frontend/src/api/pnr.ts`

**APIs**
- `GET /api/v1/pnr/{pnr}` (new, user-scoped)
- `PATCH /api/v1/passengers/{id}` extended request shape (identity fields)

**Database changes**
- `V10__passenger_identity.sql`: add `identity_type`, `identity_number` columns to `passengers`
- Optional: `pricing_rules` table if dynamic pricing needs configurability beyond a hardcoded rule

**Tests**
- Backend: unit test for pricing-rule application, ownership test for new PNR endpoint (user A cannot look up user B's booking by guessing PNR pattern — PNR itself is the auth boundary, so test that a non-existent/foreign PNR returns 404 not data)
- Frontend: no automated test yet possible until Phase 8 adds RTL (defer assertions, but do a manual smoke pass)

**Definition of done**
- `mvn -q compile` (or equivalent) succeeds with zero errors, confirmed on a machine with Maven Central access
- Passenger identity field is stored, validated, and never fully echoed back unmasked
- `GET /api/v1/pnr/{pnr}` returns only the requesting user's own booking, 404 otherwise
- Landing page has no empty white sections and no placeholder text
- Dashboard shows real upcoming/past trip data split, not a single generic list

---

## Phase 2: Database and seed data

**STATUS: PARTIAL — see `docs/phase2-database-report.md` for the full record.**
What actually shipped in this phase: migrations V10–V13 (Kafka event
tracking + correlation IDs across `audit_logs`/`ai_tool_executions`/
`kafka_event_log`, demo bookings/disruption/notification seed data, and
`active`/deactivation flags), an HTTP-level `CorrelationIdFilter`, and a
real fix to a demo-data idempotency gap found by deliberately forcing a
duplicate migration run. **Deliberately not done:** the `roles`/
`user_roles` normalization described below. Reasoning: doing it safely
requires touching `User`, `SecurityConfig`, and `CustomUserDetailsService`
— the most safety-critical, already-verified-working part of the app —
with no working Java compiler available in this environment to catch a
mistake. The existing comma-separated `roles` column is functionally
correct (RBAC works, is tested) and normalizing it is a schema-purity
improvement, not a functional gap; the risk/reward didn't justify it
without compile verification. This is a disclosed scope decision, not a
silently dropped task — revisit once Maven Central access or a
Docker-capable test environment is available.

**Exact features**
- Normalize `roles` (currently a comma-separated string column) into `roles` + `user_roles` tables, matching spec section 5 literally.
- Add `pricing_rules` table if Phase 1's dynamic pricing needs it (coordinate with Phase 1; either phase can own the migration, tracked here for DB completeness).
- Confirm and document all `created_at`/`updated_at`/status-field conventions are consistent across every table (audit pass, not new tables).

**Backend files**
- `backend/src/main/java/com/railmind/auth/User.java`, `UserRepository.java` (switch from string roles to relation)
- `backend/src/main/java/com/railmind/auth/SecurityConfig.java`, `CustomUserDetailsService.java` (role resolution)
- New: `backend/src/main/java/com/railmind/auth/Role.java`, `UserRoleRepository.java` (if kept as JPA entities rather than pure SQL join)

**Frontend files**
- None directly — role display in `frontend/src/pages/ProfilePage.tsx` and admin user list (Phase 3) reads from the same `/auth/me`-style payload shape, so no frontend change if the API contract is preserved

**APIs**
- No new public endpoints; `/api/v1/auth/**` payloads must keep the same `roles: string[]` shape after the migration so nothing downstream breaks

**Database changes**
- `V10__roles_normalization.sql` (or renumber depending on Phase 1's migration order): create `roles`, `user_roles`, backfill from the existing comma-separated column, drop the old column only after backfill is verified — **still not implemented; see STATUS note above**
- Keep migrations strictly additive/backward-compatible until backfill is verified in a lower environment

**Tests**
- Backend: migration test (Testcontainers) verifying pre-existing seeded users retain correct roles after migration
- Regression run of `AuthServiceCredentialResetTest`, `LoginAttemptServiceIT`, `SupportAndAdminRbacIT` to confirm RBAC still resolves correctly post-migration

**Definition of done**
- `roles`/`user_roles` exist as normalized tables with FKs — **not met, deliberately deferred**
- All three demo accounts (`user@`, `admin@`, `support@railmind.demo`) still authenticate and receive correct authorities — unaffected, since no auth code was touched
- Every existing RBAC test still passes unmodified — unaffected

---

## Phase 3: Admin backend and admin frontend

**Exact features**
- Admin route management (create/update/deactivate routes and route-stations)
- Admin coach management (1A/2A/3A/SL/CC/2S per spec section 28)
- Admin seat management (generate seats, configure coaches, view availability)
- Admin pricing management (base fare, class fare — building on Phase 1's dynamic pricing hook)
- Admin user management (view users, block/unblock, role management)
- Admin booking management (search, view, cancel, refund from the admin side)
- Admin view of audit logs
- Admin UI trigger for the existing `/api/v1/admin/disruptions/simulate` endpoint (Simulate Delay / Cancellation / Platform Change buttons)
- Admin analytics frontend (charts consuming the already-implemented `AnalyticsController` endpoints)
- Full `/admin`, `/admin/trains`, `/admin/stations`, `/admin/routes`, `/admin/bookings`, `/admin/users`, `/admin/analytics` frontend routes

**Backend files**
- New: `backend/src/main/java/com/railmind/admin/AdminRouteController.java`
- New: `backend/src/main/java/com/railmind/admin/AdminCoachController.java`, `AdminSeatController.java`
- New: `backend/src/main/java/com/railmind/admin/AdminPricingController.java`
- New: `backend/src/main/java/com/railmind/admin/AdminUserController.java`
- New: `backend/src/main/java/com/railmind/admin/AdminBookingController.java`
- New: `backend/src/main/java/com/railmind/audit/AdminAuditController.java`
- Extend: `backend/src/main/java/com/railmind/admin/dto/AdminDtos.java`
- Extend: `backend/src/main/java/com/railmind/train/TrainRepositories.java`, `booking/BookingRepositories.java` for admin-scoped queries (no ownership filter, RBAC-gated instead)

**Frontend files**
- New: `frontend/src/pages/admin/AdminDashboardPage.tsx`
- New: `frontend/src/pages/admin/AdminTrainsPage.tsx`, `AdminStationsPage.tsx`, `AdminRoutesPage.tsx`
- New: `frontend/src/pages/admin/AdminBookingsPage.tsx`, `AdminUsersPage.tsx`
- New: `frontend/src/pages/admin/AdminAnalyticsPage.tsx` (Recharts-based, per spec's stated dependency)
- New: `frontend/src/pages/admin/AdminAuditLogPage.tsx`
- New: `frontend/src/api/admin.ts`
- Extend: `frontend/src/App.tsx` (register all `/admin/*` routes under a role-gated layout)
- New: `frontend/src/routes/guards.tsx` extension for `RequireRole('ADMIN')`

**APIs**
- `GET/POST/PUT /api/v1/admin/routes`, `/api/v1/admin/routes/{id}`
- `GET/POST/PUT /api/v1/admin/coaches`, `/api/v1/admin/seats`
- `GET/PUT /api/v1/admin/pricing`
- `GET /api/v1/admin/users`, `PATCH /api/v1/admin/users/{id}/block`, `PATCH /api/v1/admin/users/{id}/roles`
- `GET /api/v1/admin/bookings`, `POST /api/v1/admin/bookings/{id}/cancel`, `POST /api/v1/admin/bookings/{id}/refund`
- `GET /api/v1/admin/audit-logs`
- (Existing, reused as-is) `GET /api/v1/admin/analytics/**`, `POST /api/v1/admin/disruptions/simulate`

**Database changes**
- No new tables required (all entities — routes, coaches, seats, users, bookings, audit_logs — already exist per Phase-1/2 migrations); only new queries/DTO projections

**Tests**
- Backend: RBAC tests for every new admin endpoint (extend `SupportAndAdminRbacIT` pattern) — confirm USER and SUPPORT_AGENT roles get 403
- Backend: unit tests for block/unblock logic (blocked user cannot log in) and admin-initiated cancel/refund
- Frontend: once RTL exists (Phase 8), component tests for admin tables and the block/unblock confirmation dialog

**Definition of done**
- Every admin capability listed in spec section 28 has a working backend endpoint and a working frontend page
- A non-admin user gets 403 on every admin endpoint and cannot reach `/admin/*` in the UI (redirected, not a dead page)
- Admin analytics page renders real charts from real data, no mock numbers
- Admin can block a user and that user is immediately unable to log in

---

## Phase 4: Support portal

**Exact features**
- Support agent search-by-customer (name/email/phone) in addition to existing exact-PNR/booking-ID lookup
- Support agent view of a user's cancellation and refund status (already partially exposed via booking detail — confirm and surface explicitly)
- Support agent review of AI tool-execution history for a given user/booking (read-only)
- Full `/support/*` frontend routes and pages (currently zero support frontend exists)

**Backend files**
- Extend: `backend/src/main/java/com/railmind/support/SupportController.java` (add search endpoint)
- Reuse: `backend/src/main/java/com/railmind/ai/AiHistoryService.java` (add a support-scoped read path, still never exposing another user's data outside the support role's legitimate need)
- Extend: `backend/src/main/java/com/railmind/support/dto/SupportDtos.java` (new, if not already present)

**Frontend files**
- New: `frontend/src/pages/support/SupportDashboardPage.tsx`
- New: `frontend/src/pages/support/SupportBookingLookupPage.tsx`
- New: `frontend/src/pages/support/SupportAiHistoryPage.tsx`
- New: `frontend/src/api/support.ts`
- Extend: `frontend/src/App.tsx` (register `/support/*` under `RequireRole('SUPPORT_AGENT')`)

**APIs**
- `GET /api/v1/support/customers/search?query=`
- `GET /api/v1/support/bookings/{id}/ai-history`
- (Existing, reused) `GET /api/v1/support/bookings/{id}`, `GET /api/v1/support/pnr/{pnrNumber}`

**Database changes**
- None — all underlying data already modeled; this phase is API surface + UI only

**Tests**
- Backend: RBAC test confirming a support agent can search/view but cannot mutate bookings, cancel, or refund (that stays admin/user-only)
- Backend: test that support search never returns another support agent's or admin's account, only regular USER accounts, if that's the intended scope (confirm business rule with stakeholder before finalizing test)

**Definition of done**
- A support agent can find a customer by name/email/phone, open their booking, see AI tool history, without any write access
- Support role is fully RBAC-isolated from admin capabilities (verified by test, not just by convention)

---

## Phase 5: PNR and Trip Guardian pages

**Exact features**
- Standalone `/pnr` page: a plain user enters a PNR and sees status (uses Phase 1's new `GET /api/v1/pnr/{pnr}` endpoint)
- Standalone `/trip-guardian` page: journey timeline (Booking confirmed → Check-in reminder → Platform update → Delay → Alternative recommendation → Journey completed), live disruption banner, "View Alternatives" / "Keep Current Booking" actions — all backed by the already-implemented `TripGuardianController`/`RebookingService`

**Backend files**
- None expected to be new — `backend/src/main/java/com/railmind/disruption/TripGuardianController.java` and `RebookingService.java` already exist and are functionally complete; this phase is frontend-only unless gaps are found while wiring the UI (e.g., a missing "enable Trip Guardian" toggle endpoint — verify during implementation and add if absent)

**Frontend files**
- New: `frontend/src/pages/PnrLookupPage.tsx`
- New: `frontend/src/pages/TripGuardianPage.tsx`
- New: `frontend/src/components/tripGuardian/JourneyTimeline.tsx`, `DisruptionBanner.tsx`, `AlternativeCard.tsx`
- New: `frontend/src/api/tripGuardian.ts`
- Extend: `frontend/src/App.tsx` (register `/pnr`, `/trip-guardian`, `/trip-guardian/:bookingId`)
- Extend: `frontend/src/pages/bookings/BookingDetailPage.tsx` (link into `/trip-guardian/:bookingId` instead of/alongside any inline widget)

**APIs**
- (Existing, reused) `GET /api/v1/trips/{bookingId}/status`, whatever `TripGuardianController` currently exposes — confirm exact paths during implementation and document them in `docs/api.md` (Phase 9)
- Possible new: `POST /api/v1/bookings/{id}/trip-guardian/enable` if no toggle currently exists (verify against `TripGuardianController` before assuming — mark NEEDS_INTEGRATION until confirmed)

**Database changes**
- None expected — `trip_alerts` table already exists per `V6__trip_guardian_disruptions.sql`

**Tests**
- Backend: if a new enable/disable toggle endpoint is added, unit + ownership tests for it
- Frontend: manual E2E smoke test for the full flagship demo scenario (spec section 72) end to end, since this page is the missing link that currently blocks that demo from being fully click-through-able

**Definition of done**
- A user can navigate to `/pnr`, enter a PNR, and see live status without going through `/bookings`
- A user can navigate to `/trip-guardian` for an active booking and see the full timeline plus any live disruption and alternative-journey actions
- The full spec-section-72 flagship demo (search → AI recommend → book → pay → PNR → enable Trip Guardian → admin simulates delay → Kafka event → disruption agent → alternative found → user notified → user approves → rebooking processed) is click-through-able start to finish

---

## Phase 6: AI agent separation and safety

**STATUS: PARTIAL — implementation complete and verified by every means
available in this sandboxed environment; see `docs/phase6-ai-agent-report.md`
for the full closure record. Backend compile/test execution remains
NOT VERIFIED (Maven Central 403, the same standing blocker as every phase
of this project) — verified instead via source inspection, a full clean
PostgreSQL migration re-run (V1–V14), a simulated audit-row round-trip, and
hand-traced test logic (12 new + 1 fixed pre-existing test case, all
re-checked against current source signatures this closure pass). The
correlation-ID propagation item below was already done in Phase 2, not
this phase — confirmed unchanged and still correct by trace.**

**Exact features**
- Split the single `AiOrchestratorService` into five named, spec-matching components: Journey Planning Agent, Booking Agent, Policy/RAG Agent, Disruption Agent, Trip Guardian Agent — each with its own defined responsibilities per spec section 53, coordinated by a thin orchestrator that keeps today's proven tool-calling/HITL/authorization behavior unchanged.
- No behavior change intended in this phase — this is a structural refactor for spec fidelity and portfolio clarity, not new AI capability. All existing tool-authorization, HITL confirmation, and tool-execution-logging behavior must be preserved exactly.
- Correlation-ID propagation from HTTP request through AI orchestration through tool execution (currently only present in the Kafka consumer), so a single request can be traced end-to-end in logs.

**Backend files**
- Refactor: `backend/src/main/java/com/railmind/ai/AiOrchestratorService.java` → split into `backend/src/main/java/com/railmind/ai/agents/JourneyPlanningAgent.java`, `BookingAgent.java`, `PolicyRagAgent.java`, `DisruptionAgent.java`, `TripGuardianAgent.java`, plus a slim `AgentRouter.java`/`AiOrchestratorService.java` that dispatches to them
- `backend/src/main/java/com/railmind/ai/tools/ToolExecutor.java` (no behavior change expected, but agent-name tagging added to tool-execution logs)
- `backend/src/main/java/com/railmind/ai/AiEntities.java` (if `ai_tool_executions` needs an `agent_name` column — check current schema first, since `V1`'s `ai_tool_executions` table may already have this field; only add if genuinely absent)
- New: correlation-ID filter/interceptor, likely `backend/src/main/java/com/railmind/common/CorrelationIdFilter.java`, wired through MDC into the AI call chain and tool executor

**Frontend files**
- None required — this is a backend-internal refactor; the `/ai/chat` contract must not change

**APIs**
- No new endpoints, no contract changes to `POST /api/v1/ai/**` — this phase must be provably backward-compatible

**Database changes**
- Only if `ai_tool_executions.agent_name` is genuinely missing (verify against `V1__init_schema.sql` before adding a migration) — otherwise none

**Tests**
- Backend: full regression of `AiHistoryServiceTest`, `ToolExecutorAuthorizationTest` — hand-traced this closure pass against current source; both remain structurally compatible with the refactor (`AiHistoryServiceTest` never constructs `ToolExecutionDto`/`AiToolExecution` directly, so the new `agentName` field is a non-breaking addition; `ToolExecutorAuthorizationTest` was fixed, not merely re-verified — it was missing 2 constructor args, a genuine pre-existing compile-breaking bug). Actual compiler execution remains blocked (Maven Central 403).
- Backend: new unit tests per agent class confirming each only calls the tools within its documented responsibility — done (`AgentAuthorizationTest`, 5 cases), including the specific "Policy/RAG Agent never calls `createBookingDraft`" property this bullet names.
- Backend: correlation ID presence traced from HTTP entry through tool execution — confirmed via MDC thread-local semantics (Phase 2's mechanism, unchanged); no new test needed since no new propagation code was added.

**Definition of done**
- Five distinct, named agent classes exist matching spec section 53's responsibilities exactly — **met**, verified against actual source this closure pass
- `AiOrchestratorService`'s public contract and all existing tests are unchanged/still passing — **met** for the contract (no change to `/ai/chat`'s request/response shape); "still passing" is hand-traced, not compiler-verified (blocked)
- A single correlation ID can be grep'd across HTTP → AI → tool → (Kafka, where relevant) logs for one request — **met**, unchanged from Phase 2, re-confirmed by trace
- See `docs/phase6-ai-agent-report.md` for the full verification record and the final status: **PARTIAL — implementation complete and verified by every means available in this environment; backend compile/test execution remains blocked by Maven Central 403.**

---

## Phase 7: Kafka, Redis and observability

**STATUS: PARTIAL — see `docs/phase7-observability-report.md` for the full
record, including a critical incident (mid-session container reset lost
all Phase 3–5 frontend work, not recoverable). Kafka topics/envelope/
idempotency/structured-logging/Prometheus+Grafana/correlation-path are
implemented and verified by every means available in this environment.
Booking/payment/RAG metrics are a disclosed, deliberate scope reduction
(routed through `AuditService` instead of direct service instrumentation,
to avoid touching several already-tested service constructors without a
compiler available). Backend compile/test remains blocked (Maven Central
403, the standing issue across every phase of this project).**

**Exact features**
- Add the remaining Kafka topics from spec section 31 (`booking-events`, `payment-events`, `notification-events`, `ai-events`) and move at least the notification fan-out (spec's own example: `BookingCreated → Kafka → Email / Notification / Analytics`) from today's synchronous direct call onto Kafka, so Kafka is used for a real asynchronous workflow beyond train disruption events, per the spec's explicit "do not add Kafka only for decoration" instruction.
- Wire Micrometer + a Prometheus registry into Spring Boot Actuator.
- Add Grafana + Prometheus services to `docker-compose.yml` with a starter dashboard covering API latency, error rate, DB performance, Kafka events, AI latency/tool failures, and cache hit rate (the exact metric list from spec section 38).
- Document Redis cache invalidation strategy (currently implemented correctly in code/comments but not written up — see Phase 9 for the doc, this phase is the metric/eviction verification).

**Backend files**
- `backend/src/main/java/com/railmind/config/KafkaConfig.java` (register additional topics/producers)
- New: `backend/src/main/java/com/railmind/notification/NotificationEventProducer.java`, `NotificationEventConsumer.java`
- Refactor: `backend/src/main/java/com/railmind/payment/PaymentService.java`, `cancellation/CancellationService.java` to publish events instead of calling `NotificationService` synchronously (keep a synchronous fallback path only if the async path is proven reliable in testing — do not regress delivery guarantees)
- `backend/pom.xml` (add `micrometer-registry-prometheus`)
- `backend/src/main/resources/application.yml` (expose `/actuator/prometheus`, tune management endpoints)

**Frontend files**
- None

**APIs**
- No new public REST APIs; `/actuator/prometheus` is a new internal-facing endpoint

**Database changes**
- None

**Tests**
- Backend: extend `KafkaDlqIT` pattern to cover the new topics' consumers, including a DLQ path test for each
- Backend: integration test confirming a booking confirmation still results in a notification being created, now via the async path (assert eventual consistency, not synchronous completion)
- Manual: confirm `/actuator/prometheus` scrape output contains the metrics named in spec section 38

**Definition of done**
- All 5 Kafka topics from the spec exist and are used for genuine async workflows, each with retry/backoff/DLQ matching the existing `train-events` pattern
- Prometheus scrapes the backend successfully; Grafana shows a working dashboard for the required metric list
- `docker compose up` brings up Prometheus and Grafana alongside existing services with health checks

---

## Phase 8: Testing and security

**Exact features**
- Add `@testing-library/react` (+ `@testing-library/jest-dom`, `@testing-library/user-event`) to the frontend and write component/form/critical-workflow tests (auth forms, train search, booking flow, payment flow) — spec section 39 explicitly requires this and it's currently structurally impossible (dependency doesn't exist).
- Add general API rate limiting (e.g. Bucket4j or Spring's built-in resilience patterns) on top of the existing Redis-backed login lockout, covering all public/auth-adjacent endpoints per spec section 6.
- Add an accessibility pass: verify semantic HTML, focus states, labeled form fields, and keyboard navigation across the auth, search, and booking flows (spec section 76) — currently unverified by any automated tooling.
- Verify responsive behavior (mobile/tablet/laptop/desktop) across train cards, booking flow, and admin tables (spec section 77) — currently unverified.
- Implement real dark mode (spec section 34/78): apply `dark:` variants across shared components (`ui.tsx`, `AppShell.tsx`) and page-level content, not just the Tailwind config flag that exists today with zero actual usage.
- Confirm the full backend build/test suite actually compiles and runs (carried over as a hard blocker from Phase 1, re-verified here as part of security/testing sign-off).

**Backend files**
- New: `backend/src/main/java/com/railmind/common/RateLimitFilter.java` (or equivalent Bucket4j config)
- `backend/pom.xml` (rate-limiting dependency)
- `backend/src/main/resources/application.yml` (rate-limit thresholds as env-configurable values)

**Frontend files**
- `frontend/package.json` (add testing-library deps)
- New: `frontend/src/pages/auth/__tests__/LoginPage.test.tsx`, `RegisterPage.test.tsx`
- New: `frontend/src/pages/trains/__tests__/TrainSearchPage.test.tsx`
- New: `frontend/src/pages/booking/__tests__/BookingPage.test.tsx`, `PaymentPage.test.tsx`
- `frontend/src/components/ui.tsx`, `frontend/src/components/AppShell.tsx` (dark-mode variants)
- Broad pass across `frontend/src/pages/**` and `frontend/src/components/**` for `dark:` classes and accessibility attributes (`aria-*`, `role`, focus rings)
- New: `frontend/src/store/themeStore.ts` (dark-mode toggle persisted in memory/local state, not `localStorage` inside artifacts context, but this is a real app so browser localStorage is fine here — confirm with the person before assuming a preference)

**APIs**
- No new endpoints — rate limiting is middleware, not a new API surface

**Database changes**
- None

**Tests**
- Frontend: the component/form/workflow tests listed above are the deliverable of this phase
- Backend: unit test for rate-limit rejection (429) after threshold exceeded, and that it resets after the configured window
- Manual: keyboard-only walkthrough of login → search → book → pay; screen-reader spot check on the same flow

**Definition of done**
- `npm run test` covers auth, search, and booking flows with real assertions, not just the existing single API-client test
- Exceeding the configured rate limit returns a 429 with the same consistent API error shape as spec section 37
- Dark mode is toggleable and every page remains readable with no broken contrast
- Full backend test suite (`mvn test` or equivalent) is confirmed to actually compile and pass on a machine with Maven Central access — this closes out the standing "never verified to compile" risk carried since Phase 1

---

## Phase 9: Docker, CI/CD and documentation

**Exact features**
- Create `.github/workflows/` from scratch (none currently exists): checkout → install deps → lint → test → build → Docker build, for both backend and frontend, per spec section 47's exact pipeline shape.
- Add Prometheus + Grafana services to `docker-compose.yml` (coordinated with Phase 7's backend wiring).
- Rewrite `README.md` into the spec-required shape: Project overview, Features, Architecture, Technology stack, AI architecture, Agent workflow, RAG architecture, Database architecture, Kafka architecture, Redis architecture, Security, API documentation, Local setup, Environment variables, Docker setup, Testing, Demo credentials, Screenshots/placeholders, Future improvements — replacing today's informal "Pass N" changelog style (the changelog content can move to a `CHANGELOG.md` instead of living in the README).
- Create the missing `docs/` files: `architecture.md`, `api.md`, `ai-agents.md`, `rag.md`, `database.md`, `security.md`, `deployment.md`, `booking-flow.md`, `kafka-events.md`, `redis.md`, `testing.md`, `troubleshooting.md`.
- Complete OpenAPI/Swagger grouping and descriptions per spec section 42's endpoint-group list (Auth, Users, Stations, Trains, Schedules, Availability, Bookings, Passengers, Payments, PNR, Cancellation, Refund, AI, RAG, Disruption, Notifications, Admin, Analytics).

**Backend files**
- `backend/src/main/java/com/railmind/**/*Controller.java` (add/verify `@Tag`, `@Operation`, `@ApiResponse` annotations across all controllers for complete Swagger docs)

**Frontend files**
- None

**APIs**
- No new endpoints — this phase documents and pipelines the existing (plus Phases 1–8's new) surface

**Database changes**
- None

**Tests**
- CI: the new GitHub Actions workflow itself is the test — it must actually run backend tests, frontend tests (from Phase 8), and both builds, and fail the pipeline on any red step
- Manual: verify `docker compose up --build` brings up every service (frontend, backend, postgres, redis, kafka, prometheus, grafana) with passing health checks

**Definition of done**
- A PR triggers a GitHub Actions run that lints, tests, and builds both backend and frontend, and builds both Docker images
- `README.md` matches every item in spec section 52's required list
- All 12 `docs/*.md` files listed in the spec exist with real, accurate content (no placeholder sections)
- Swagger UI at `/docs` (or documented path) shows every endpoint grouped and described per spec section 42

---

## Phase 10: Final end-to-end verification

**Exact features**
- Run the complete spec-section-73 self-audit checklist against the finished system, item by item, with evidence for each line (not a checkbox ticked from memory).
- Execute the full spec-section-72 flagship demo scenario end to end in a real running environment (not a code-reading exercise): login → AI search → recommendation → booking draft → approval → payment → PNR → Trip Guardian enabled → admin simulates delay → Kafka event → disruption agent → alternative found → notification → user approval → rebooking processed.
- Confirm zero TODOs, zero "Coming Soon" placeholders, zero dead links, and zero hardcoded secrets anywhere in the final codebase.

**Backend files**
- None new — this phase audits, it doesn't build

**Frontend files**
- None new — this phase audits, it doesn't build

**APIs**
- None new — full contract review of everything shipped in Phases 1–9 against `docs/api.md`

**Database changes**
- None — final migration-history review only (confirm every migration is additive, ordered, and applied cleanly on a fresh database)

**Tests**
- Full backend suite (`mvn test`) green on a real build machine
- Full frontend suite (`npm run test`) green
- Full manual click-through of every route in spec section 35's route list, confirming no dead links and no placeholder content
- Fresh-clone `docker compose up --build` from a clean checkout, confirming the system comes up with no manual intervention beyond `.env` setup

**Definition of done**
- Every line of the spec-section-73 checklist is checked off with a specific piece of evidence (a passing test, a screenshot, a log line, or a file reference) recorded in a final `docs/v15-verification.md`
- The flagship demo (section 72) runs start to finish without a single manual database edit or code change mid-demo
- No TODO/"Coming Soon"/placeholder text and no hardcoded secret exists anywhere in the repository (verified by `grep -rn "TODO\|Coming Soon\|FIXME" ` and a secrets-pattern scan)

---

## Cross-phase notes

- **Ordering rationale**: Phases 1–2 unblock everything downstream (a stable backend and correct schema). Phases 3–5 deliver the missing user-facing surfaces in order of spec emphasis (admin is the biggest functional gap, support and PNR/Trip Guardian are smaller but block the flagship demo). Phase 6 is a structural refactor best done once the tool surface is stable (post Phase 1–5) so there's less churn to re-verify. Phase 7 (Kafka/Redis/observability) and Phase 8 (testing/security) are infrastructure hardening, sequenced after functional completeness so they measure the real system rather than a moving target. Phase 9 packages and documents everything. Phase 10 is the audit gate.
- **No phase deletes or rewrites working functionality.** Every phase above is additive or a scoped, test-covered refactor (Phase 6) with an explicit "existing tests must still pass" requirement.
- **Nothing in this roadmap has been implemented.** This file is planning output only, per instruction.
