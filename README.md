# reconciliation-service

Part of [`card-billing-modernization`](https://github.com/leon-lourenco/card-billing-modernization) —
the modernized counterpart to [`card-billing-legacy`](https://github.com/leon-lourenco/card-billing-legacy).
Full architecture, contracts, and cross-cutting decisions live in that repo's
[`ARCHITECTURE.md`](https://github.com/leon-lourenco/card-billing-modernization/blob/master/ARCHITECTURE.md) —
this README covers what's specific to this one service.

Owns external statement ingest and matching — the piece that held the legacy's O(lines ×
invoices) nested loop, because there was never a shared database key between an external bank
statement and this system's own invoices to index on.

## API

| Endpoint | Purpose |
|---|---|
| `POST /statements/ingest` (multipart CSV) | Same shape as the legacy: `external_reference,document_number,amount_cents,statement_date` |
| `POST /statements/match` | For each unmatched line, matches against open invoices |

## The fix, concretely

Matching a statement line no longer means loading every open invoice into memory and scanning.
Each line calls `billing-service`'s `GET /invoices/search?documentNumber=&amountCents=&aroundDate=`
first (an indexed database lookup, not a scan), and — if that finds nothing — the same endpoint
again without `amountCents`, to tell a wrong-amount invoice apart from no invoice at all. This
service never holds more than one statement line and one query result in memory at a time. A match calls
`POST /invoices/{id}/payments` on `billing-service` to record it. O(lines × invoices) becomes
O(lines) indexed lookups.

`ExternalStatementLine`, `ReconciliationRun`, and `ReconciliationMatch` stay owned here, same
shape as the legacy — this service's own bookkeeping of what it ingested and what it found.

Errors are `application/problem+json` via this service's own domain exceptions —
`MalformedStatementLineException`, `ReconciliationRunAlreadyInProgressException`.

## A real contract bug, caught by a real integration run

This service's `billing-service` client was written — WireMock-tested, all green — before
`billing-service` existed, against an assumed response shape documented in its own Javadoc. The
first time the two services actually ran against each other, `/statements/match` failed every
line with "billing-service could not be reached," which looked like a network problem and wasn't
one. `billing-service` was answering `200` the whole time.

Two real mismatches, found by comparing the client's expectations to the built controller instead
of to the assumption written a phase earlier: the client expected a `{"invoices": [...]}` wrapper
object; `billing-service`'s `GET /invoices/search` returns a bare JSON array. The client expected
a field called `amountCents`; the real field is `amountOwedCents`. A third, related gap:
`billing-service` required `amountCents` on every call, but this service's own divergence-check
design — telling "this customer owes something else in the window" apart from "this customer owes
nothing at all" — depends on being able to omit it. Fixed on both sides: this service's
`InvoiceSearchResponse` now matches the real shape, and `billing-service`'s `amountCents` became
optional.

Re-run after the fix, against real seeded invoices: 3 statement lines ingested, `/statements/match`
returns `{"matchedCount":3,"unmatchedCount":0,"divergentCount":0}`, and `billing-service`'s
`payments` table shows all three, `source: EXTERNAL_RECONCILIATION`, each keyed by the statement
line's own external reference.

## Engineering practices

Hexagonal package structure (`domain` / `application` / `infrastructure`), enforced by ArchUnit
in every test run — see `ARCHITECTURE.md` in the hub repo for the exact rules. Tests written
alongside implementation, not after.

## Stack

| Category | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 4.1.0 |
| Build | Gradle (Kotlin DSL) | 9.7.1 |
| API docs | springdoc-openapi-starter-webmvc-ui | 3.1.0 |
| Auth | Keycloak (client + resource server) | 26.7 |
| Database | PostgreSQL | 16 |
| Architecture tests | ArchUnit | — |

## Running it

```bash
docker compose up -d          # Postgres + Keycloak
./gradlew bootRun
```

Needs `billing-service` reachable for a real match run — see the hub repo's full-platform
`docker-compose.yml` to bring up all four services together. Swagger UI at
`http://localhost:8084/swagger-ui.html`.

```bash
./gradlew test
```

Outbound calls to `billing-service` are stubbed with WireMock in this service's own tests,
matching the contract documented in the hub repo's `ARCHITECTURE.md`.
