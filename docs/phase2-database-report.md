# Phase 2 — Database and Seed Data — Final Report

Statuses used throughout: **PASS**, **PARTIAL**, **FAIL**, **NOT_VERIFIED**.

## 1. Phase 2 objective

Ensure the database schema supports every documented functional area
(stations, trains, routes, schedules, coaches, seats, fares, users/roles,
bookings, passengers, payments, refunds, notifications, disruptions,
rebooking, AI conversations/messages/tool calls, RAG documents/chunks/query
logs, audit logs, Kafka event tracking, correlation IDs, analytics), add
migrations only where genuinely missing, and seed realistic demo data —
without rebuilding, redesigning, or destroying any existing working
functionality (auth, security, booking, disruption/rebooking).

## 2. Files created

- `backend/src/main/resources/db/migration/V13__kafka_event_log_correlation_id.sql`
- `backend/src/main/java/com/railmind/common/CorrelationIdFilter.java`
- `backend/src/test/java/com/railmind/common/CorrelationIdFilterTest.java`
- `backend/src/test/java/com/railmind/disruption/TrainEventConsumerTest.java`
- `backend/src/test/java/com/railmind/disruption/DeadLetterConsumerTest.java`
- `backend/src/test/java/com/railmind/audit/AuditServiceTest.java`
- `docs/sql/phase2-verification.sql`
- `docs/phase2-database-report.md` (this file)

(V10, V11, and the `KafkaEventLog` entity/repository, and the earlier
`TrainEventConsumer`/`DeadLetterConsumer` wiring were created in the
previous Phase 2 session and are inspected, not re-created, here.)

## 3. Files modified

- `backend/src/main/resources/db/migration/V11__demo_bookings_disruptions_notifications.sql`
  — added `WHERE NOT EXISTS` guards to the two standalone notification
  `INSERT` statements (real idempotency gap found by testing, see §11).
- `backend/src/main/java/com/railmind/disruption/TrainEventMessage.java`
  — added `correlationId` field, captured from MDC inside `newEvent(...)`
  (no call-site changes needed).
- `backend/src/main/java/com/railmind/disruption/TrainEventConsumer.java`
  — uses the event's own `correlationId` (not `eventId`) for MDC/log
  correlation and persists it on `KafkaEventLog`.
- `backend/src/main/java/com/railmind/disruption/DeadLetterConsumer.java`
  — recovers `eventId`/`correlationId` by re-parsing the dead-lettered
  payload when possible, falling back to synthetic identifiers only when
  genuinely unparseable; wraps handling in MDC set/clear.
- `backend/src/main/java/com/railmind/disruption/KafkaEventLog.java`
  — added `correlationId` field.
- `backend/src/main/java/com/railmind/ai/AiEntities.java`
  — added `correlationId` field to `AiToolExecution` (column existed since
  V10, nothing wrote to it before this).
- `backend/src/main/java/com/railmind/ai/AiOrchestratorService.java`
  — `logToolExecution` now populates `correlationId` from MDC.
- `docs/v14-complete-audit.md`, `docs/v15-roadmap.md` — updated; see those
  files' Phase 2 sections directly.

No authentication, security, booking, or existing database functionality
was modified. `roles`/`user_roles` normalization was considered and
explicitly **not** done — see §16.

## 4. Migration names

In order, V1 through V13 (V12 was added during the intervening Phase 3
session in this same codebase and is included here only because "confirm
migrations apply in order" requires testing the full current chain, not
because Phase 2 authored it):

`V1__init_schema.sql`, `V2__seed_data.sql`, `V3__refresh_tokens.sql`,
`V4__seat_hold_expiry.sql`, `V5__notifications.sql`,
`V6__trip_guardian_disruptions.sql`,
`V7__preferences_saved_passengers_credentials.sql`, `V8__policy_rag.sql`,
`V9__analytics.sql`, `V10__kafka_event_tracking_and_correlation_ids.sql`,
`V11__demo_bookings_disruptions_notifications.sql`,
`V12__active_deactivation_flags.sql` (Phase 3, unmodified this pass),
`V13__kafka_event_log_correlation_id.sql` (new this pass).

No duplicate version numbers exist. No migration references a table or
column introduced by a later-numbered migration (verified by reading each
file's dependencies against the preceding ones during the clean re-run in
§10).

## 5. Tables added

- `kafka_event_log` (V10)

No other new tables this phase. V11 and V13 only insert data / add columns
to existing tables.

## 6. Columns added

- `audit_logs.correlation_id` (V10)
- `ai_tool_executions.correlation_id` (V10)
- `kafka_event_log.correlation_id` (V13 — the table itself came from V10
  without this column; added once the distinction between `event_id` and
  `correlation_id` became clear during this pass, see §12)

## 7. Seed records added

From `V11__demo_bookings_disruptions_notifications.sql`, all tied to the
pre-existing demo user (`user@railmind.demo`):
- 3 bookings (1 plain confirmed / SL, 1 confirmed with Trip Guardian
  enabled / AC3, 1 cancelled+refunded / AC2)
- 4 passengers, 4 booking_passengers, 3 seats marked `BOOKED` (the 4th
  passenger shares booking 2 with a 2nd booked seat), 1 seat correctly left
  `AVAILABLE` (the cancelled booking's)
- 2 PNRs, 2 payments (`SUCCESS`)
- 1 cancellation, 1 refund (`PROCESSED`)
- 1 trip alert (`DELAY_DETECTED`, unresolved)
- 3 notifications (`BOOKING_CONFIRMED`, `TRIP_DISRUPTION`, `BOOKING_CANCELLED`)

## 8. Kafka event tracking changes

- `kafka_event_log` table (V10): `topic`, `event_id` (unique), `event_type`,
  `payload_summary`, `correlation_id` (V13), `status`
  (`RECEIVED`/`PROCESSED`/`FAILED`), `error_message`, `received_at`,
  `processed_at`.
- `TrainEventConsumer` upserts (find-by-`event_id`-or-create) rather than
  always inserting, so a Kafka redelivery of the same event updates the
  existing row instead of violating the unique index — verified by a
  dedicated test, not just asserted (see §12).
- `DeadLetterConsumer` writes a `FAILED` row for every dead-lettered
  message, recovering the original `event_id`/`correlation_id` from the
  payload when parseable.

## 9. Correlation-ID changes

- New `CorrelationIdFilter`: a plain servlet filter (registered outside
  Spring Security's filter chain, `@Order(HIGHEST_PRECEDENCE)`) that
  accepts an incoming `X-Correlation-Id` header or generates a UUID,
  exposes it via MDC (`correlationId` key) for the request's duration,
  echoes it in the response header, and always clears MDC in a `finally`
  block.
- `AuditService.log(...)` already picked up `MDC.get("correlationId")`
  automatically (done in the earlier Phase 2 session) — now actually
  populated for HTTP requests, since `CorrelationIdFilter` sets it.
- `AiOrchestratorService.logToolExecution` now does the same for
  `ai_tool_executions.correlation_id` (previously the column existed but
  nothing wrote to it — a real gap, fixed this pass).
- `TrainEventMessage` carries its own `correlationId` (distinct from
  `eventId`), captured from the ambient MDC value at publish time (usually
  the admin's HTTP request that triggered a simulation) — so an HTTP
  request that causes a Kafka event can be traced through to that event
  and back to a resulting `KafkaEventLog`/notification.
- `TrainEventConsumer`/`DeadLetterConsumer` both set/restore MDC around
  their processing and always clear it afterward, mirroring
  `CorrelationIdFilter`'s discipline.

## 10. SQL verification commands

All run via `psql` directly against a local PostgreSQL 16 + pgvector
instance installed in this sandbox (`apt-get install postgresql
postgresql-16-pgvector`), not through Flyway/Maven (blocked — see §15).

```
DROP SCHEMA public CASCADE; CREATE SCHEMA public;
psql ... -v ON_ERROR_STOP=1 -f V1__init_schema.sql
psql ... -v ON_ERROR_STOP=1 -f V2__seed_data.sql
... (through V13, each applied individually)
psql ... -v ON_ERROR_STOP=1 -t -A -F' = ' -f docs/sql/phase2-verification.sql
```

Additionally, to directly test the "re-running a migration does not
create invalid duplicates" requirement rather than assume it:
```
psql ... -f V11__demo_bookings_disruptions_notifications.sql   -- applied a 2nd time, deliberately
```

## 11. SQL verification results

All 13 migrations (V1–V13) applied cleanly, in order, on a freshly reset
schema. Confirmed twice (once before, once after the V11 idempotency fix
below).

Forcing a 2nd application of V11 (bypassing Flyway's own execute-once
tracking, which would prevent this in real operation) surfaced a real bug:
the three booking inserts correctly failed with
`duplicate key value violates unique constraint "bookings_idempotency_key_key"`
(safe failure, no corruption), but the two standalone notification
`INSERT ... SELECT` statements were not protected by any constraint and
silently created 2 duplicate notification rows. Fixed with `WHERE NOT
EXISTS` guards; re-tested the same forced-duplicate scenario afterward and
confirmed zero duplicates result now, with the same safe constraint
failures on the booking inserts.

`docs/sql/phase2-verification.sql` (28 checks) — all 28 passed:

```
demo_user_exists = 1
bookings_missing_relations = 0
booking_passengers_missing_relations = 0
duplicate_pnrs = 0
confirmed_bookings_without_success_payment = 0
cancelled_seed_booking_has_cancellation_and_refund = 1
cancelled_booking_seat_not_available = 0
confirmed_booking_seat_not_booked = 0
trip_guardian_seed_valid = 1
notifications_missing_relations = 0
duplicate_seed_bookings = 0
duplicate_train_numbers = 0
duplicate_station_codes = 0
duplicate_event_ids = 0
orphan_passengers = 0
orphan_payments = 0
orphan_refunds = 0
orphan_notifications = 0
orphan_trip_alerts = 0
invalid_seat_status = 0
refunded_booking_without_cancellation = 0
cancelled_booking_with_booked_seats = 0
null_required_fields_bookings = 0
station_count = 15
train_count = 10
route_count = 10
schedule_count = 300
coach_count = 60
fare_count = 40
```

One bug was found and fixed while writing the verification script itself:
an early draft referenced `passengers.saved`, which doesn't exist — the
real column is `is_saved`. Caught by actually running the query against
the live schema (`\d passengers`), not by assumption.

## 12. Tests executed

Backend (new this pass): `CorrelationIdFilterTest` (5 cases),
`TrainEventConsumerTest` (3 cases), `DeadLetterConsumerTest` (2 cases),
`AuditServiceTest` (2 cases) — 12 test cases written, covering: HTTP
correlation ID accepted from header, generated when absent, generated when
header is blank, MDC cleared after success, MDC cleared after an exception;
new Kafka event recorded RECEIVED→PROCESSED with correlation ID persisted,
retried delivery of the same `event_id` upserts rather than duplicating,
a failing event is recorded FAILED and still rethrows (so retry/DLQ isn't
short-circuited); dead-letter recovers original correlation ID when the
payload is parseable, falls back to a synthetic ID when it isn't;
`AuditService` persists correlation ID when present in MDC, and does not
fail when absent.

None of these could actually be run — see §15 for why, and what was done
instead.

## 13. Tests passed

NOT_VERIFIED. No backend test could be executed (blocked, §15). Every test
above was instead traced by hand against the actual method it exercises,
line by line, cross-checking mock setups against real method signatures
(e.g. `KafkaEventLogRepository.findByEventId`, `ConsumerRecord`'s 5-arg
constructor, `RefundPolicyService.RefundQuote`'s field order in earlier
phases) — the same discipline used in Phases 1–3, which has previously
caught real mistakes before they were even run (e.g. a `parseSubject`
method that didn't exist, found in Phase 3).

## 14. Tests failed

None — because none were executed. Do not read "0 failed" as "all passed."

## 15. Commands that could not run

| Command | Exact error | Why | Alternative performed |
|---|---|---|---|
| `mvn -q compile` | `Non-resolvable parent POM for com.railmind:railmind-backend:0.1.0: Could not transfer artifact org.springframework.boot:spring-boot-starter-parent:pom:3.3.4 from/to central (https://repo.maven.apache.org/maven2): authorization failed ... status: 403 Forbidden` | Maven Central is not on this sandbox's network allow-list; confirmed directly by re-running the exact command this session, not assumed from a prior phase | Manual line-by-line review of every new/changed Java file against real entity/repository/DTO declarations; cross-referenced field names, method signatures, and constructor arities |
| `mvn test` (any of it) | Same root cause — the build can't even resolve its parent POM, so no test can run | Same | Real PostgreSQL-level verification of every migration and every SQL relationship (§10–11); hand-tracing of new test logic against real method bodies |
| Flyway migrate (via Maven plugin) | Same root cause | Flyway itself is a Maven-resolved dependency in this project | Direct `psql -f` application of every migration file in order, which exercises the exact same SQL Flyway would run — the difference is Flyway's own execute-once bookkeeping (`flyway_schema_history`) wasn't exercised, only the SQL itself |

No local `.m2` cache pre-existed in this sandbox, and no alternate Maven
mirror is on the network allow-list, so there was no way to route around
this.

## 16. Known limitations

- Backend compile/test remains unverified by an actual compiler, for the
  fourth phase running. This is the single largest standing risk in the
  project and should be the first thing done in any environment with
  Maven Central access or a pre-warmed `.m2` cache.
- `roles`/`user_roles` normalization was not done, by deliberate choice
  (see §3 and the updated `docs/v15-roadmap.md` Phase 2 section) — the
  existing comma-separated column works and is tested; normalizing it
  touches `SecurityConfig`/`CustomUserDetailsService` with no compiler
  available to catch a mistake, so the risk didn't clear the bar for this
  pass.
- Flyway's own execution was not exercised, only the equivalent raw SQL
  via `psql`. Flyway's checksum validation, `flyway_schema_history`
  bookkeeping, and baseline/repair behavior are a different (smaller) risk
  surface than the SQL itself, but they are genuinely untested here.
- The forced-duplicate-V11 test is a legitimate stress test of the SQL's
  own safety, but it does not exercise the real protection mechanism
  (Flyway refusing to re-run an already-applied version at all) — that
  remains NOT_VERIFIED for the same Maven-access reason.
- Test execution (as opposed to test authorship) for all 12 new
  correlation-ID/Kafka-logging test cases is NOT_VERIFIED.

## 17. Final Phase 2 status

**PARTIAL**

Rationale: every database-level requirement was genuinely implemented and
verified against a real running Postgres instance — migrations apply
cleanly and in order, seed data is fully relationally consistent, a
forced-duplicate test caught and led to fixing a real idempotency bug, and
correlation-ID propagation now genuinely flows from HTTP request through
to Kafka event, dead-letter record, audit log, and AI tool execution
(verified by tracing the code, not assumed). It is not PASS because (a)
`roles`/`user_roles` normalization, part of this phase's original scope,
was deliberately not done, and (b) no backend test could actually be
executed in this environment — both are disclosed, not silent, gaps.
