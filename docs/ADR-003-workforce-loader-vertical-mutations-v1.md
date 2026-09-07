# ADR-003 — Vertical Mutations V1 in b4rrhh-workforce-loader

## Status
Proposed

## Context

`b4rrhh-workforce-loader` already supports lifecycle simulation through:
- HIRE
- TERMINATE
- REHIRE

This is useful to create realistic employee presence history, but it is still insufficient for generating rich employee history.

The long-term goal of the loader is to simulate not only lifecycle state changes, but also realistic changes in employee verticals over time, such as:
- work center changes
- contract changes
- labor classification changes
- cost center distribution changes

These changes must be executed through canonical public APIs, not through database access.

At this stage, the next natural milestone is to add a first set of vertical mutation events that provide high functional value while keeping the simulation manageable.

## Decision

The loader will introduce a first set of vertical mutation events focused on the most valuable employee verticals:

- CHANGE_WORK_CENTER
- REPLACE_CONTRACT
- REPLACE_LABOR_CLASSIFICATION
- REPLACE_COST_CENTER

## Scope

### Included in V1
- Generate optional vertical mutation events in employee scenarios
- Execute them chronologically through public APIs
- Keep mutation generation deterministic through seed
- Reuse preloaded catalog pools when possible
- Ensure mutations happen only while the employee is active

### Not included yet
- contacts
- identifiers
- addresses
- administrative corrections
- delete operations on child verticals
- concurrency
- retries
- generic event engine

## Design Principles

### 1. Mutations are scenario events
Vertical changes are modeled as explicit timeline events, not as ad-hoc extra calls.

### 2. Chronological coherence
A mutation may only happen during an active employee cycle.
It must never be scheduled:
- before HIRE
- after TERMINATE unless followed by REHIRE and active again
- outside active lifecycle windows

### 3. Respect vertical semantics
Each vertical mutation must use its canonical public operation:
- work center change via canonical work center endpoint
- contract change via replace-from-date
- labor classification change via replace-from-date
- cost center change via replace-from-date

> **Nota (workforce-loader#7).** Las tres referencias a `replace-from-date` de este
> principio ya no valen. El ADR-057 del backend retiro `replace-from-date` como
> modelo: un cambio de vertical no es una sustitucion, es un **alta con su fecha de
> inicio cuya unica consecuencia automatica es cerrar la ocurrencia anterior**. El
> loader era el ultimo cliente de los cuatro endpoints deprecados y ya no llama a
> ninguno; la operacion canonica de cada vertical es hoy su `POST` de alta. El
> principio en si —cada mutacion por su operacion canonica— sigue en pie: lo que
> cambio es cual es.

### 4. No generic framework yet
The loader should remain explicit and readable.
It must not introduce a metadata-driven mutation engine at this stage.

### 5. Employee-level consistency
Within a scenario, the loader may vary values over time, but every event must remain valid against catalog relations and active lifecycle state.

## Proposed Event Types

- HIRE
- TERMINATE
- REHIRE
- CHANGE_WORK_CENTER
- REPLACE_CONTRACT
- REPLACE_LABOR_CLASSIFICATION
- REPLACE_COST_CENTER

## Scenario Rules

For each employee:
- HIRE always exists
- TERMINATE may exist
- REHIRE may exist after TERMINATE
- mutation events may appear only inside active windows
- mutation events must be ordered chronologically

## Execution Rules

- If HIRE fails, abort the scenario
- If TERMINATE fails, abort remaining events
- If REHIRE fails, abort remaining events
- If a vertical mutation fails, record the failure and abort remaining events for that employee in V1

## Reporting

Reports must include counts by mutation type, in addition to lifecycle event counts.

## Summary

The loader evolves from lifecycle-only simulation into richer employee history simulation by adding a first explicit set of vertical mutation events with deterministic generation and canonical public API execution.