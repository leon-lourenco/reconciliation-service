-- This service's own bookkeeping: what it ingested, when it ran, and what each line turned out to
-- be. No invoice or payment table - those belong to billing-service and are reached over its API.

create table external_statement_lines (
    id                       bigint        generated always as identity primary key,
    external_reference       varchar(128)  not null unique,
    customer_document_number varchar(32)   not null,
    amount_cents             bigint        not null,
    statement_date           date          not null,
    raw_line                 varchar(1024) not null,
    ingested_at              timestamp(6)  not null,
    matched                  boolean       not null default false
);

-- A reconciliation run walks the unmatched lines in id order, one bounded page at a time. Matched
-- lines are the majority once a file has been through a run, and they are never read again, so
-- this index covers only the rows a run actually looks at.
create index idx_statement_lines_unmatched
    on external_statement_lines (id)
    where matched = false;

create table reconciliation_runs (
    id              bigint       generated always as identity primary key,
    started_at      timestamp(6) not null,
    finished_at     timestamp(6),
    status          varchar(16)  not null,
    total_lines     integer      not null default 0,
    matched_count   integer      not null default 0,
    unmatched_count integer      not null default 0,
    divergent_count integer      not null default 0
);

-- Starting a run first asks whether one is already RUNNING; there is normally at most one such row.
create index idx_reconciliation_runs_in_progress
    on reconciliation_runs (id)
    where status = 'RUNNING';

create table reconciliation_matches (
    id                bigint       generated always as identity primary key,
    run_id            bigint       not null references reconciliation_runs (id),
    statement_line_id bigint       not null references external_statement_lines (id),
    -- billing-service's invoice id, null unless the result is MATCHED. Deliberately no foreign
    -- key: that row lives in another service's database.
    invoice_id        bigint,
    result            varchar(32)  not null,
    matched_at        timestamp(6) not null
);

create index idx_reconciliation_matches_run on reconciliation_matches (run_id);
