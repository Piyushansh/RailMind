# Phase 6 — AI Agent Architecture and Safety — Final Report

Statuses used throughout: **PASS**, **PARTIAL**, **FAIL**, **NOT VERIFIED**.

## 1. Phase objective

Refactor the existing single-orchestrator AI design into 5 clearly separated
agent classes with declared tool permissions, fail-closed routing, and
per-call audit tagging — without removing working AI functionality, without
weakening existing ownership/authorization checks, and without rebuilding
the orchestrator's conversation loop.

## 2. Architecture

```
User
  |
AiOrchestratorService  (unchanged single conversation loop, provider-agnostic)
  |
AgentRouter            (tool name -> owning agent, fail-closed for unknown tools)
  |
Agent                  (JourneyPlanningAgent | BookingAgent | PolicyRagAgent |
                         DisruptionAgent | TripGuardianAgent - permission check)
  |
ToolExecutor           (unchanged - the actual hardened gateway to real services)
  |
Business Service       (BookingService, TrainSearchService, RefundPolicyService, ...)
  |
Database
```

Design decision, stated plainly: the 5 agents are a **permission and audit
boundary layered on top of the existing, already-hardened `ToolExecutor`**,
not a fragmentation of database/service access across 5 classes. Every
agent ultimately delegates real execution to the same `ToolExecutor`
instance that existed before this phase. This was a deliberate choice to
avoid introducing new security bugs while refactoring with no working
compiler available in this environment — fragmenting the execution path
itself would have been the riskier change for the same nominal benefit.

## 3. The five agents and responsibilities

| Agent | Responsibility | Tools owned |
|---|---|---|
| `JourneyPlanningAgent` | Search, compare, and rank trains; read saved preferences | `searchTrains`, `findAlternativeTrains`, `getTrainDetails`, `checkAvailability`, `calculateFare`, `planJourney`, `getUserPreferences` |
| `BookingAgent` | Booking/cancellation drafts, PNR/booking lookup, refund quotes — never a real mutation | `createBookingDraft`, `getBooking`, `getPNR`, `createCancellationDraft`, `calculateRefund` |
| `PolicyRagAgent` | Grounded policy Q&A only, never from model knowledge | `policySearch` |
| `DisruptionAgent` | Relay system-generated disruption notifications | `getNotifications` |
| `TripGuardianAgent` | Live per-trip status/delay/platform/alert explanation | `getTripStatus` |

## 4. Tool ownership table (verified against actual source, not assumed)

Extracted directly from `ToolExecutor`'s `switch` (15 case labels) and each
agent's `ALLOWED_TOOLS` set:

| Tool | Owning agent | Verified |
|---|---|---|
| searchTrains | JourneyPlanningAgent | ✅ |
| findAlternativeTrains | JourneyPlanningAgent | ✅ |
| getTrainDetails | JourneyPlanningAgent | ✅ |
| checkAvailability | JourneyPlanningAgent | ✅ |
| calculateFare | JourneyPlanningAgent | ✅ |
| planJourney | JourneyPlanningAgent | ✅ |
| getUserPreferences | JourneyPlanningAgent | ✅ |
| createBookingDraft | BookingAgent | ✅ |
| getBooking | BookingAgent | ✅ |
| getPNR | BookingAgent | ✅ |
| createCancellationDraft | BookingAgent | ✅ |
| calculateRefund | BookingAgent | ✅ |
| policySearch | PolicyRagAgent | ✅ |
| getNotifications | DisruptionAgent | ✅ |
| getTripStatus | TripGuardianAgent | ✅ |

15/15 tools accounted for, each exactly once, matching `ToolExecutor`'s case
labels one-for-one. No tool is unowned; no tool is claimed twice. Confirmed
by direct source inspection this session (grep against both files), not
carried over from the prior report unverified.

## 5. AgentRouter behavior

- Builds a `Map<String, Agent>` at construction by iterating every agent's
  `allowedTools()`.
- **One-owner-per-tool is enforced at construction**: if two agents ever
  claim the same tool name, the constructor throws `IllegalStateException`
  immediately — this is a startup-time fail-fast, not a runtime
  best-effort. Directly tested (`AgentRouterTest.constructorRejectsTwoAgentsClaimingTheSameTool`).
- A package-visible `AgentRouter(List<Agent>)` constructor exists
  specifically so this duplicate-detection logic is unit-testable without
  needing five real Spring-wired agent instances.

## 6. Fail-closed behavior

`AgentRouter.route(...)`: if `request.toolName()` has no entry in the
tool-to-agent map, it returns `authorized=false` and an error payload
**without ever calling the matched agent or `ToolExecutor`**. Verified by:
- `AgentRouterTest.unregisteredToolName_isRefused_withoutEverCallingToolExecutor`
  — asserts `verifyNoInteractions(toolExecutor)`.
- `AgentAuthorizationTest.everyAgent_refusesAMadeUpAdminSoundingTool` — every
  individual agent also independently refuses a tool outside its own set
  (defense in depth: even if routing were ever bypassed, the agent itself
  still enforces its permission set via `AbstractToolAgent.execute`).

This is what makes "AI cannot execute admin tools as a normal user" true by
construction: there is no admin tool registered to any agent, and an
unregistered/invented tool name (including anything admin-flavored) has no
path to execution at either the router layer or the agent layer.

## 7. Security boundaries — traced against actual source this session

- **No direct SQL / no raw queries**: confirmed — `ToolExecutor` contains
  zero `createQuery`/`createNativeQuery`/`jdbcTemplate` usage.
- **No write operations from the AI path**: confirmed — grep for
  `.save(`/`.delete(`/`.saveAndFlush(` inside `ToolExecutor` returns zero
  matches. Every mutation-shaped tool (`createBookingDraft`,
  `createCancellationDraft`) only ever reads and computes a preview.
- **One disclosed, pre-existing architectural note** (not introduced this
  phase, not changed this phase): `ToolExecutor` reads several
  repositories directly (`StationRepository`, `PnrRepo`,
  `TripAlertRepository`, `TravelPreferenceRepository`) rather than
  exclusively through a service-layer method. All such access is
  read-only (`findBy...`), parameterized (Spring Data derived queries, not
  raw SQL), and pre-dates Phase 6. A stricter reading of "AI cannot bypass
  business services" would flag this as a minor layering inconsistency.
  Refactoring it was judged out of scope for a phase whose brief is
  agent-boundary closure, not a `ToolExecutor` internals rewrite, and is
  noted here rather than silently passed over.
- **Ownership enforcement unchanged and re-verified**: `ToolExecutorAuthorizationTest`
  (a genuinely pre-existing broken test — missing 2 constructor args, fixed
  this phase) proves a PNR belonging to another user comes back as an error,
  never that user's data.
- **Mutation safety directly proven**, not just asserted:
  `AiSafetyPropertiesTest.createBookingDraft_neverCallsAnyBookingServiceWriteMethod`
  and `...createCancellationDraft_neverCallsAnyCancellationOrRefundWriteMethod`
  both assert `verify(bookingService, never()).createBooking(...)` /
  `.cancelConfirmedBooking(...)` respectively, with `verifyNoMoreInteractions`
  — not inferred from reading the code, but from a mock that would fail the
  test if a write path were ever exercised.
- **No fabricated data**: same two tests assert the returned fare
  (`425`) and refund amount (`750`) are the exact values the mocked
  service returned, proving the tool/agent layer passes real numbers
  through rather than computing or rounding independently.
- **RAG insufficient-information fallback**: `AiSafetyPropertiesTest.policySearch_reportsInsufficientInformation_whenNothingClearsTheSimilarityThreshold`
  proves an empty retrieval result produces `sufficientInformation:false`
  and no `results` array — never a fallback to model-invented content.

## 8. Mutation/approval model

Confirmed by both source inspection and the tool-ownership table: **no
agent owns any tool that directly books, charges, cancels, refunds, or
rebooks.** `createBookingDraft`/`createCancellationDraft` produce
`"draft": true` previews only. The real mutating actions
(`POST /api/v1/bookings`, `POST /api/v1/cancellations`,
`POST .../trip-alerts/{id}/approve-rebooking`,
`.../reject-rebooking`) remain real, separate, user-initiated REST calls
outside the AI's tool surface entirely — the AI has no mechanism to invoke
them at all, not merely a permission check that happens to block them.

## 9. Ownership enforcement

Enforced inside `ToolExecutor`/`BookingService` (unchanged by this phase),
re-verified via the fixed `ToolExecutorAuthorizationTest`. The agent layer
does not re-implement or weaken this — it delegates to the same
`authenticatedUserId`-scoped service calls that existed before Phase 6.

## 10. Audit trail

- `ai_tool_executions.agent_name` (VARCHAR(60), nullable) added by `V14`.
- **Verified against real PostgreSQL this session**: full clean re-run of
  V1–V14 from a dropped schema, all applied without error; column
  confirmed present via `\d ai_tool_executions`; a simulated insert/read
  cycle round-tripped `agent_name`/`tool_name`/`success`/`correlation_id`
  exactly as the Java code would write them (including the
  `agent_name='none', success=false` shape for a refused/unregistered
  tool), then cleaned up.
- **Cannot falsely diverge from the router's actual decision**: traced in
  `AiOrchestratorService.runAgentLoop` — `agentResponse.agentName()` (the
  router's own return value) is what gets persisted, with no intermediate
  re-derivation or hardcoding that could disagree with it.
- Nullable is correct/intentional: rows created before `V14` (none exist in
  this fresh schema, but would in a real deployment) simply have `NULL`
  here, which is the honest state for a call that predates agent routing.

## 11. Correlation-ID behavior

**Unchanged and re-verified, not newly built** — Phase 2 already
implemented `CorrelationIdFilter` (HTTP-level, sets `MDC["correlationId"]`
for the request's duration, always cleared in `finally`) and
`AiOrchestratorService.logToolExecution` already read `MDC.get("correlationId")`
into `AiToolExecution.correlationId`. Because MDC is thread-local and the
full chain (`Controller -> AiOrchestratorService -> AgentRouter -> Agent ->
ToolExecutor`) runs synchronously on one thread within one HTTP request,
the correlation ID is automatically visible at every layer without any
new plumbing — traced this session to confirm Phase 6 didn't need to (and
didn't) touch this mechanism. Kafka-side correlation propagation
(`TrainEventMessage.correlationId`, `TrainEventConsumer`,
`DeadLetterConsumer`) is unrelated to the AI path and was also left
untouched.

## 12. Tests executed (source-traced, NOT compiler-executed)

- `AgentRouterTest` (4 cases)
- `AgentAuthorizationTest` (5 cases)
- `AiSafetyPropertiesTest` (3 cases)
- `ToolExecutorAuthorizationTest` (1 case, fixed this phase — was missing 2
  constructor args, a genuine pre-existing compile-breaking bug)

Every one of these 13 test cases was re-read line by line this session
against the actual current source of `ToolExecutor`, `AgentRouter`, each
agent class, and `AiOrchestratorService` to confirm method signatures,
constructor argument order and count, and field names all still match.
This is hand-tracing, explicitly **not** the same as compiler/JUnit
execution, and is reported as such rather than conflated with a real pass.

## 13. Tests blocked

All 13 above, plus the full pre-existing backend suite. Exact command and
error, re-run this session:

```
$ mvn -q compile
[FATAL] Non-resolvable parent POM for com.railmind:railmind-backend:0.1.0:
Could not transfer artifact org.springframework.boot:spring-boot-starter-parent:pom:3.3.4
from/to central (https://repo.maven.apache.org/maven2): authorization failed ...
status: 403 Forbidden
```

Root cause: Maven Central is not on this sandbox's network allow-list, and
no local `.m2` cache exists to route around it. Identical blocker as every
prior phase in this project.

## 14. Database verification

- Full clean re-run, V1 through V14, from a dropped-and-recreated schema:
  **all 14 apply without error**, no duplicate version numbers (file-naming
  inspection — no `flyway_schema_history` exists since Flyway itself
  couldn't run, see below), no migration references a column/table
  introduced by a later-numbered migration (each file read against its
  predecessors).
- `flyway_schema_history` does **not** exist in this database, because
  every migration this project, this session included, was applied via
  direct `psql -f`, never through Flyway's own Maven-resolved runner
  (blocked for the same reason as compilation). This is stated explicitly
  so raw-SQL verification is never confused with Flyway's own execution,
  checksum validation, or history bookkeeping, none of which have been
  exercised.
- `ai_tool_executions.agent_name` column existence, type, nullability, and
  index: confirmed via `\d ai_tool_executions`.
- Insert/read round-trip for realistic agent-tagged rows (including a
  refused/unauthorized shape): confirmed, then cleaned up.

## 15. Known limitations

- Backend compile/test remains unverified by an actual compiler — the
  standing, disclosed risk across every phase of this project.
- Flyway's own execution (as opposed to the equivalent raw SQL) remains
  unexercised.
- `ToolExecutor`'s direct (read-only) repository access alongside its
  service-layer calls is a minor pre-existing layering inconsistency,
  disclosed in §7, not fixed this phase as out of scope for agent-boundary
  closure work.
- The agent split is a permission/audit layer, not a full re-architecture
  of tool execution — by design (§2), but worth restating plainly: a
  compromised or buggy `ToolExecutor` itself would still affect every
  agent equally, since they share one instance.

## 16. Final status

**PARTIAL — implementation complete and verified by every means available
in this environment (source inspection, PostgreSQL execution, hand-traced
test logic, frontend execution from the prior session); backend
compile/test execution remains blocked by Maven Central returning 403.**
