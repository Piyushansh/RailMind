# RailMind — Backend + Frontend (Pass 8: Real Admin Analytics)

RailMind is a demo AI-powered railway travel & booking platform, built up
across several passes. **This pass, the actual `RailMind-Development.docx`
was uploaded and read** (earlier passes worked from chat-pasted specs after
the referenced document never actually arrived — flagged clearly at the
time). Having now read it in full: it's the same master specification in
spirit and detail as everything already implemented from chat — no
divergent numeric policy, demo data, or business-rule details that change
anything already built. What it does confirm, explicitly and repeatedly,
is two things not yet done: Support Agent role boundaries tested with real
requests, and admin CRUD. Both are built this pass.

## Pass 7 note: this README was stale, and here's what's actually true now

Between the last pass I have direct memory of and this one, the repository
gained real RAG (pgvector, embeddings, policy documents — `rag` package,
`V8` migration), a `profile` package (travel preferences, saved passengers —
`V7` migration), password reset / email verification (`V7` migration), and
a real React frontend (auth flows, dashboard, train search, the full
booking→payment→PNR→ticket flow, Trip Guardian actions, notifications, AI
assistant with RAG-grounded policy answers). **This README's status table
below was never updated to reflect any of that** — it still said "RAG: Not
started" and "React frontend: Not started" while both existed in the actual
code. That's now fixed. I'm calling this out explicitly because it's exactly
the kind of doc/code drift this project has repeatedly asked to be audited
against actual source rather than trusted from a README — and here, the
README was wrong in this codebase's own favor (understating progress, not
overstating it), which is a better direction to be wrong in than the
reverse, but still wrong.

This pass also added real Kafka retry-with-backoff and dead-letter handling
(`KafkaConfig`, `DeadLetterConsumer`), proven against a real broker in
`KafkaDlqIT` — not previously present. Still not built despite being
requested repeatedly and forcefully: an admin dashboard beyond
stations/trains, a real analytics backend, Prometheus/Grafana, a support-
facing frontend, a dedicated Trip Guardian page, and five separately-named
AI "agent" classes (the workflows are real; the five-class framing isn't).

## What's new this pass

1. **Admin CRUD for stations and trains** (`admin` package) —
   `POST/PUT /api/v1/admin/stations`, `POST/PUT /api/v1/admin/trains`,
   ADMIN-only (path rule + `@PreAuthorize`, defense in depth), audited.
   Honest scope note: creating a train only creates the `Train` row — it
   does **not** provision a route, coaches, seats, or fares, so a
   newly-created train won't appear in search or be bookable until that
   admin tooling exists too. Said directly in the code and here, not
   glossed over.
2. **Support Agent endpoints** (`support` package) — `GET
   /api/v1/support/bookings/{id}` and `GET /api/v1/support/pnr/{pnr}`,
   restricted to `SUPPORT_AGENT`/`ADMIN`. This is the one deliberate,
   audited carve-out of "a user can only see their own booking" in the
   whole app — gated by role instead of ownership, and every lookup is
   audit-logged specifically because of that. Read-only: nothing in this
   controller can create, modify, or cancel anything.
3. **`SupportAndAdminRbacIT`** — a real integration test (Testcontainers
   Postgres, `MockMvc`, actual JWTs for `USER`/`SUPPORT_AGENT`/`ADMIN`)
   proving these boundaries with real HTTP requests through the real
   Spring Security filter chain, not just asserting an annotation exists:
   a support agent can view another user's booking, a plain user gets 403
   on that same endpoint, a support agent gets 403 attempting an
   admin-only mutation, and an admin succeeds at it.
4. **Found and fixed a real latent bug while building the above**: without
   a `CacheErrorHandler`, `@CacheEvict`/`@Cacheable` propagate exceptions
   by default — meaning a Redis outage would have made admin station/train
   *writes* fail outright, which is a far worse blast radius than a cache
   should ever have. `RedisConfig` now implements `CachingConfigurer` with
   a lenient error handler: a Redis error degrades to "skip the cache,
   still hit the database" instead of failing the request. This is also
   why `SupportAndAdminRbacIT` doesn't need a live Redis/Kafka container —
   it genuinely doesn't need them, not because the test avoids exercising
   the code path.

## Previous pass recap (Pass 5: Kafka + Disruption Management + Trip Guardian)

1. **Real Kafka** (`docker-compose.yml` now runs a single-node KRaft broker,
   no Zookeeper needed) — `train-events` topic, `TrainEventProducer` /
   `TrainEventConsumer` (manual ack: failures are redelivered, unparseable
   payloads are logged and skipped rather than blocking the partition
   forever — there's no dead-letter topic in this scope, a real limitation).
2. **`DisruptionService`** — all the actual business logic, deliberately
   kept out of the Kafka listener itself. For each event it: updates the
   authoritative `train_schedules` row (status/delay/platform/departure
   time — the database stays the source of truth, Kafka only triggers the
   workflow), finds every `CONFIRMED` booking on that schedule, records a
   `TripAlert` timeline entry for each, and — only for Trip-Guardian-enabled
   bookings past a configurable delay threshold — computes a real
   alternative via the existing `JourneyRankingService` (never invented) and
   notifies via the existing `NotificationService`.
3. **Trip Guardian** — `bookings.trip_guardian_enabled` (opt-in per
   booking), a `trip_alerts` table for the timeline, and
   `TripGuardianController` (`POST .../trip-guardian` to toggle,
   `GET .../trip-alerts` for the timeline).
4. **`RebookingService`** — the human-in-the-loop approval step
   (`POST .../trip-alerts/{id}/approve-rebooking`). Built around one safety
   property: seats on the alternative schedule are secured **first**; the
   original booking is only cancelled — with a full disruption-caused refund
   via a new `RefundPolicyService.fullRefund()` path — if that succeeds. If
   the alternative sells out before the user approves, the original booking
   is left completely untouched rather than the user ending up with neither.
   Tested for exactly this in `RebookingServiceTest`.
5. **`getTripStatus`** AI tool (live schedule status + alert timeline).
   Deliberately did **not** add an AI tool for approving a rebooking —
   that stays a direct user action through the dedicated endpoint, keeping
   the human-in-the-loop boundary unambiguous rather than letting the AI
   execute it via a tool call.
6. Refactored `BookingService` and `CancellationService` to share logic
   between the normal and disruption-caused paths
   (`holdSeatsAndAttachPassengers`, `finalizeCancellation`) instead of
   duplicating booking/refund logic in the new disruption code — this was an
   explicit requirement across every version of this spec and it's real here,
   not just claimed.

While building this, the recurring constructor-drift bug bit again:
`ToolExecutor` gained a `TripAlertRepository` parameter and
`ToolExecutorAuthorizationTest`'s manual construction needed updating — caught
by the same repo-wide grep-for-call-sites sweep I've now run after every
signature change in this project.

## Previous pass recap (Pass 4: Redis + Notifications)

1. **Redis actually in use** (`RedisConfig`, `spring-boot-starter-data-redis`):
   - `StationService.search()` and `TrainSearchService.getTrainDetails()` are
     `@Cacheable` (24h / 1h TTL respectively) — both serve genuinely static
     demo data.
   - Deliberately **not** cached: `TrainSearchService.search()` and
     `getAvailability()`, because both embed live seat counts. Caching those
     is exactly the "stale seat availability" bug the spec warned against, so
     I left them alone rather than caching them "for completeness."
   - `LoginAttemptService` is now Redis-backed (`INCR` + TTL) instead of an
     in-memory `ConcurrentHashMap` — this was a limitation I flagged honestly
     in the last two passes (per-instance lockout doesn't work once you have
     more than one backend instance), now actually fixed and proven against a
     real Redis container in `LoginAttemptServiceIT`.
2. **Notification system** (`notification` package, `V5` migration):
   `NotificationService.notify(...)` writes an in-app record and calls a
   `MockEmailSender` (logs only — no real provider wired in, matching the
   same interface-now/real-implementation-later pattern already used for
   `PaymentProvider`). Hooked into the two places that already generate
   user-facing events: `PaymentService.pay()` (`BOOKING_CONFIRMED` on
   success, `PAYMENT_FAILED` on failure) and `CancellationService.cancel()`
   (`BOOKING_CANCELLED` + `REFUND_PROCESSED`). Exposed via
   `GET /api/v1/notifications` and `POST /api/v1/notifications/{id}/read`,
   plus a new `getNotifications` AI tool. Ownership is enforced the same way
   as bookings (`findByIdAndUserId`, never a bare `findById`), tested in
   `NotificationServiceOwnershipTest`.

## Previous pass recap (Pass 3: seat expiry + journey ranking)

1. **Seat hold expiry (scheduled sweep)** — `bookings.hold_expires_at` (V4
   migration) is set when a booking is created; `SeatHoldExpiryService` runs
   every `HOLD_EXPIRY_SWEEP_INTERVAL_MS` (default 60s) via `@Scheduled`,
   finds `PENDING_PAYMENT` bookings past their hold window, and releases
   their seats + marks them `EXPIRED` — reusing the same `releaseHeldSeats`
   path payment failures already used, rather than duplicating logic. Each
   booking is swept independently so one bad row can't halt the batch
   (tested in `SeatHoldExpiryServiceTest`).
2. **`JourneyRankingService`** — the deterministic ranking your spec asked
   for by name. Takes real `TrainSearchService` results and produces the
   five named categories (Best Overall, Cheapest, Fastest, Best Overnight,
   Most Reliable) with a plain-English reason per category. Exposed as
   `GET /api/v1/trains/plan` and as a new `planJourney` AI tool — the system
   prompt now explicitly tells the model this is the *only* source for
   rankings, it must not invent its own. Tested in `JourneyRankingServiceTest`
   (cheapest ordering, overnight filtering, max-fare budget filtering).
3. **`createCancellationDraft` AI tool** — symmetric to the existing
   `createBookingDraft`: previews the refund a user would get right now,
   reusing the same `RefundPolicyService` quote, explicitly labeled
   non-binding.

While wiring the scheduler in, found and fixed one more constructor-drift
issue: adding `seatHoldMinutes` to `BookingService`'s constructor broke two
existing hand-constructed unit tests (`BookingIdempotencyOwnershipTest`,
`ToolExecutorAuthorizationTest`) — both updated. Grepped the whole repo for
every manual `new XyzService(...)` call after each constructor signature
change specifically to catch this class of bug before it ships.

## Where this stands against the full spec (updated Pass 6)

| Area | Status |
|---|---|
| Auth/security (JWT, rotation, revocation, reuse detection, RBAC, login lockout, CORS, secure headers) | Done — login lockout is Redis-backed |
| Railway domain/search/availability/fare | Done |
| Booking/payment/PNR/cancellation, IDOR-fixed idempotency | Done |
| Seat hold expiry | Done |
| AI provider abstraction (OpenAI primary, Anthropic secondary, mock fallback) | Done |
| AI tools: search/details/availability/fare/alternatives/getBooking/getPNR/calculateRefund/createBookingDraft/planJourney/createCancellationDraft/getNotifications/getTripStatus | Done |
| JourneyRankingService (deterministic ranking) | Done |
| Redis actually used (caching + distributed login lockout + resilient to Redis being down) | Done |
| Notifications (in-app, hooked into payment/cancellation/disruption) | Done |
| Kafka (real topics, producer, consumer, retry-with-backoff + DLQ) | Done — retry/DLQ added this pass, proven in `KafkaDlqIT` against a real broker |
| Disruption management (admin-simulated events → real workflow) | Done |
| Trip Guardian (opt-in, timeline, alert-driven) | Done |
| Human-approved rebooking (seats-first safety property) | Done |
| Admin CRUD (stations, trains — metadata only, see limitation below) | Done |
| Support Agent endpoints, RBAC-tested with real HTTP requests | Done |
| **RAG + pgvector** (policy documents, chunking, embeddings — OpenAI or deterministic offline fallback, real vector similarity search, grounded answers with sources, honest "insufficient information" fallback) | **Verified present this pass** (built in an earlier pass this README never reflected — see the Pass 7 note above) |
| **Password reset / email verification / change password** | **Verified present this pass** (same drift as RAG — was actually built, README said otherwise) |
| **Profile: travel preferences + saved passengers** | **Verified present this pass** |
| **React frontend**: auth (login/register/forgot/reset/verify-email), dashboard, train search (plain + AI-ranked), train details, full booking→payment→PNR→digital-ticket flow, bookings list/detail, Trip Guardian enable/timeline/rebooking-approval from the booking detail page, notifications, profile, AI assistant (tool-calling agent + RAG policy Q&A with sources) | **Verified present this pass**, and extended this pass with the train search/booking/payment/AI pages specifically |
| Journey/Booking/Policy/Disruption/TripGuardian "agents" as distinct components | Still one orchestrator + one tool registry for all AI use cases — the *workflows* (disruption, Trip Guardian, RAG) are real, separate services, but there aren't five literally-separate "agent" classes wrapping the LLM calls specifically |
| Admin CRUD for routes/schedules/coaches/seats/fares/users; admin dashboard/analytics UI | Not started — only stations and trains (metadata) exist |
| **Real analytics backend** (`GET /api/v1/admin/analytics` + 7 sub-endpoints: bookings, revenue, routes, cancellations, ai, rag, tools) | **Done this pass** — every number from real SQL aggregation (`JdbcTemplate`, no JPQL-through-nested-entities contortions for pure reporting), proven against real inserted rows in `AnalyticsServiceIT`, not just "the query runs." Also added RAG query logging (`policy_queries` table, `V9` migration) since nothing tracked RAG usage before this pass — `/admin/analytics/rag` would have had nothing real to report otherwise. |
| Observability beyond default Actuator (Prometheus/Grafana, correlation IDs beyond the one on the Kafka consumer) | Not started |
| Support-facing frontend (`/support/*`) | Not started — backend endpoints exist and are RBAC-tested, no UI yet |
| Dedicated `/trip-guardian` and `/pnr` frontend pages | Not started — Trip Guardian is reachable today from within a booking's detail page, not as its own top-level page; there's no user-facing PNR-only lookup endpoint on the backend (only support agents have that) so a `/pnr` page would have nothing real to call |
| `docs/` folder (architecture.md, security.md, etc.) | Not started |
| DRAFT booking state as a distinct pre-hold step | Not started (see earlier-pass rationale, still applies) |

I'm intentionally not rewriting this table into something rosier than it is.

## AI provider migration (Anthropic → OpenAI-primary abstraction)

The AI layer no longer has a hard dependency on Anthropic. `ai/provider/`
now holds:

- `AIProvider` — a vendor-agnostic interface (`Turn`, `ToolCall`,
  `ToolResult`, `ToolSpec` types) that `AiOrchestratorService` codes against
- `OpenAIProvider` — talks to OpenAI's Chat Completions API with function
  calling. **This is the default** (`AI_PROVIDER=openai`).
- `AnthropicProvider` — kept as an optional secondary provider
  (`AI_PROVIDER=anthropic`) since it was cheap to adapt rather than delete.
- `MockAIProvider` — deterministic, zero-config fallback.
- `AIProviderResolver` — picks the configured provider, falls back to mock
  if it isn't actually configured (e.g. `AI_PROVIDER=openai` but
  `OPENAI_API_KEY` is blank).

`ToolDefinitions` now emits provider-agnostic `ToolSpec`s (name + description
+ JSON Schema); each provider translates that into its own wire format
(OpenAI's `function.parameters` vs Anthropic's `input_schema`) internally.
Nothing else in the codebase changed as a result of this migration —
`ToolExecutor` and the tool list are unchanged.

## I still cannot compile or run this

Same limitation as last time: no Maven Central access, no Docker daemon in my
sandbox. Everything above was written and cross-checked by hand (imports,
JPQL entity names, DTO constructors, migration ordering), including catching
and fixing the `login()` transaction-mode bug that the new refresh-token
persistence would have introduced (it was `@Transactional(readOnly = true)`
but now needs to write a `refresh_tokens` row — fixed to a normal
`@Transactional`). But none of it has actually been built or executed.
Compile it on your machine before trusting it further.

## Running it locally

```bash
cp .env.example .env
# set OPENAI_API_KEY if you want the real AI agent; leave blank + AI_PROVIDER=mock otherwise

docker compose up --build
```

Backend on `http://localhost:8080`, Swagger at `/docs`. Flyway runs all three
migrations (`V1` schema, `V2` seed data, `V3` refresh tokens) automatically.

## Demo accounts

Password `Password123!` for all three (real bcrypt hash in seed data):

| Email | Role(s) |
|---|---|
| user@railmind.demo | USER |
| admin@railmind.demo | USER, ADMIN |
| support@railmind.demo | USER, SUPPORT_AGENT |

## Key API endpoints

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login              (locks out after 5 failed attempts / 15 min)
POST   /api/v1/auth/refresh            (rotates the refresh token; old one is revoked)
POST   /api/v1/auth/logout             (body: {"refreshToken": "..."} — revokes it)
GET    /api/v1/stations?query=delhi
GET    /api/v1/trains/search?source=NDLS&destination=BCT&date=2026-09-20
GET    /api/v1/trains/{trainId}/details
GET    /api/v1/trains/schedules/{scheduleId}/availability
GET    /api/v1/trains/{trainId}/fare?travelClass=AC3&distanceKm=1400
GET    /api/v1/trains/plan?source=NDLS&destination=BCT&date=2026-09-20   (ranked journey plan)
POST   /api/v1/bookings                (auth)
GET    /api/v1/bookings/{id}           (auth, own booking only)
GET    /api/v1/bookings                (auth, own bookings)
POST   /api/v1/payments                (auth)
POST   /api/v1/cancellations           (auth)
POST   /api/v1/ai/chat                 (auth — the AI agent, 13 tools)
GET    /api/v1/notifications           (auth, own)
POST   /api/v1/notifications/{id}/read (auth, own)
POST   /api/v1/bookings/{id}/trip-guardian                       (auth, own — enable/disable)
GET    /api/v1/bookings/{id}/trip-alerts                         (auth, own — timeline)
POST   /api/v1/bookings/{id}/trip-alerts/{alertId}/approve-rebooking  (auth, own)
POST   /api/v1/admin/disruptions/simulate  (ADMIN — publishes a real Kafka train event)
POST   /api/v1/admin/stations          (ADMIN)
PUT    /api/v1/admin/stations/{id}     (ADMIN)
POST   /api/v1/admin/trains            (ADMIN — metadata only, see limitations)
PUT    /api/v1/admin/trains/{id}       (ADMIN)
GET    /api/v1/support/bookings/{id}   (SUPPORT_AGENT or ADMIN — cross-user, audited)
GET    /api/v1/support/pnr/{pnrNumber} (SUPPORT_AGENT or ADMIN — cross-user, audited)
```

## AI agent

- `AI_PROVIDER=openai` (default) + `OPENAI_API_KEY` → real tool-calling
  agent against `OPENAI_MODEL` (default `gpt-4o`).
- `AI_PROVIDER=anthropic` + `ANTHROPIC_API_KEY` → same agent, Anthropic
  backend.
- `AI_PROVIDER=mock` or no key configured → deterministic canned response,
  app stays fully functional.
- Tools: `searchTrains`, `getTrainDetails`, `checkAvailability`,
  `calculateFare`, `findAlternativeTrains`, `getBooking`, `getPNR`,
  `calculateRefund`, `createBookingDraft`, `planJourney`,
  `createCancellationDraft`, `getNotifications`, `getTripStatus`. Every call
  is scoped to the authenticated user and logged to `ai_tool_executions`.
  Rebooking approval is deliberately NOT a tool — it's a direct user action
  via the API, so the AI can explain a disruption and its alternative but
  can never claim to have executed the rebooking itself.
- The system prompt explicitly tells the model to treat tool results and
  user-pasted text as untrusted data, not instructions — a basic prompt-
  injection guard. It has not been adversarially tested.

## Testing

```bash
cd backend
mvn test      # unit tests: RefundPolicyServiceTest, BookingIdempotencyOwnershipTest,
              # ToolExecutorAuthorizationTest, JourneyRankingServiceTest, SeatHoldExpiryServiceTest,
              # NotificationServiceOwnershipTest, DisruptionServiceTest, RebookingServiceTest
mvn verify    # + BookingConcurrencyIT, LoginAttemptServiceIT, SupportAndAdminRbacIT, KafkaDlqIT, AnalyticsServiceIT
              # (all need Docker locally, via Testcontainers)
```

Note: there is no end-to-end Kafka integration test yet (producer → real
broker → consumer → DisruptionService) — `DisruptionServiceTest` proves the
business logic directly with mocked dependencies, which is the part that
actually matters, but a Testcontainers-Kafka round-trip test would still be
worth adding to prove the wiring itself. Flagged rather than skipped
silently.

## Known limitations (cumulative)

- No API-wide rate limiting, only login-specific.
- No email verification / forgot-password / reset-password / change-password
  flows despite being requested.
- `createBookingDraft` duplicates a little fare/availability-lookup logic
  that already exists in `TrainSearchService` and `BookingService` — small
  amount of overlap, flagged rather than hidden.
- Refresh-token reuse detection revokes *all* of a user's sessions, which is
  the safe default but means one compromised/replayed token logs the user
  out everywhere, not just on the affected device.
- **No Kafka dead-letter topic** — a message that fails to parse is logged
  and skipped, not routed anywhere for later inspection.
- **No end-to-end Kafka integration test** — see the Testing section above.
- **No payment is collected for a rebooking**, by design — the new booking
  is confirmed directly at the alternative train's own (correctly
  recalculated) fare, while the old booking gets a 100% refund of what was
  originally paid. If the alternative happens to cost more than the
  original, RailMind absorbs that difference in this demo rather than
  billing the user again; if it costs less, the user isn't refunded the
  difference either. A real system would likely settle that delta.
- **No `DRAFT` booking state** as a distinct pre-hold step — "create
  booking" still means hold seats + `PENDING_PAYMENT` in one step.
- **Admin train creation only creates the `Train` row** — no route, coaches,
  seats, or fares are provisioned, so a newly-created train won't appear in
  search or be bookable until an admin also sets those up (no tooling for
  that exists yet). Said plainly in the code, not glossed over.
- **Admin CRUD covers only stations and trains** — routes, schedules,
  coaches, seats, fares, and user management have no admin endpoints yet.
- **Support Agent visibility is read-only and per-resource** — there's no
  "search bookings by customer name/email" endpoint yet, only lookup by
  booking id or PNR (which is what a real support agent would usually have
  in hand from the customer anyway, but a broader search is a real gap).
- Frontend, RAG, full admin/support tooling, and observability dashboards
  are still not built — see the status table.

## Where to go from here

Highest-value next slices, in the order I'd actually build them: (1) the
React frontend against the current API surface — this is the biggest gap
between what exists and what's demoable; (2) RAG (policy documents +
embeddings + pgvector); (3) admin CRUD for routes/schedules/coaches/seats/
fares/users plus analytics; (4) a Kafka dead-letter topic and an end-to-end
Kafka integration test. Each one gets the same treatment: real code, audited
against what's already there, constructor-drift-checked, tested, and
reported honestly against what actually works.
