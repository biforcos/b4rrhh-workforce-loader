# B4RRHH — workforce loader

**B4RRHH is a personnel administration system and a configurable payroll engine.**
Employment history is temporal by construction — the domain itself refuses overlaps and
gaps instead of hoping the database will catch them — and payroll is computed from a
dependency graph that is configuration rather than code, so any amount on a payslip can be
opened all the way down to the step that produced it.

This repository is an external CLI that fills an empty instance: it hires a whole
workforce, terminates part of it, rehires some of those, and moves people between work
centres, contracts and cost centres along the way — and then **runs the payroll cycle over
that workforce**, month by month: calculate, bulk close, and the corrections to already
delivered months that arrive afterwards. All of it through the same public API a person
would use. Everything else — the other repositories and the documents they share
— starts at the workspace repository, which is
[`../README.md`](../README.md) once it is laid out beside this one. **That repository is
not mirrored to GitHub**, so if you arrived from
[github.com/biforcos](https://github.com/biforcos) this page is the way in, and the
siblings to lay out beside it are `b4rrhh-backend`, `b4rrhh-frontend`, `b4rrhh-designer`
and `b4rrhh-workforce-loader`, cloned into `b4rrhh_backend`, `b4rrhh_frontend`,
`b4rrhh_designer` and `b4rrhh_workforce_loader`.

It **invents** the people it hires. It does not read a file, and there is no way to import
a real workforce yet; that gap is written down in `PRODUCTO.md` §2 in the workspace root.

---

## Running it

**You need** a JDK 21 or newer, and the backend running with a database to write to.

```bash
mvn spring-boot:run
```

The token is not in this repository and never will be: get one from the backend on the
`local` profile and export it.

```
POST http://localhost:8080/api/dev/auth/token   {"subject":"someone"}
```

```powershell
$env:LOADER_AUTH_TOKEN = "eyJ..."
```

## Dry run is the default: without a flag, it writes nothing

A plain `mvn spring-boot:run` walks the whole simulation, asks the backend for its
catalogues and prints its report — and does not send a single hire.

Writing for real has to be said out loud, and better on the command line than in
`application.yml`, so it does not depend on the state the last run left the file in:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--loader.run.dry-run=false
```

The default used to be the other way round. What settled the change was not symmetry with
the documentation but **the cost of being wrong in each direction**: with `false`, a
mistake costs a written database and an hour of rebuilding it; with `true`, it costs a flag
you have to put back. *When the two errors do not cost the same, the default goes on the
cheap side.*

And the guard described below does not cover this case, which is what made it expensive: it
checks **where** it writes, not **whether** it writes. With the expectations correctly set,
a run someone believed was dry wrote a thousand employees into the right database and the
report came out perfect.

**A perfect report does not prove it wrote.** The row count does:

```bash
docker exec b4rrhh-postgres psql -U b4rrhh -d <the database> -tAc \
  'select count(*) from employee.employee'
```

## Which database it writes to

Before generating anything, the loader asks the backend which database it is connected to
and compares that with what the run declares. If they differ it does not start, and it says
so naming both.

There is no default value, because there is no sensible one: only the run knows what the
right answer was.

```yaml
loader:
  backend:
    expected:
      database: localhost:5432/b4rrhh_wl7   # host:port/name
      employees: 0                          # how many must already be there
      schema-version: 120                   # optional
```

Or from the environment, without touching the file:

```powershell
$env:LOADER_BACKEND_EXPECTED_DATABASE  = "localhost:5432/b4rrhh_wl7"
$env:LOADER_BACKEND_EXPECTED_EMPLOYEES = "0"
```

What to put there is a question for the running backend, with an `ADMIN` token:

```
GET http://localhost:8080/api/system/target
{ "database": "localhost:5432/b4rrhh_wl7", "schemaVersion": "120", "employees": 0 }
```

The database name carries host and port on purpose: the demo's database and the development
one are both called `b4rrhh`, so the bare name does not tell apart the case that does the
most damage.

The check repeats every `loader.backend.recheck-every-writes` writes. Checking only at
startup is not enough: the backend on the other side can be replaced mid-run.

## It does not just seed: it runs the cycle

Seeding gives a photograph — a thousand employees and one payslip each. What it does not
give is **time**, and without time there is nothing to show of retroactivity: the
regulatory base reads a previous month that does not exist, delegated sick pay needs leaves
that cross months, and an arrears line needs a **delivered** month to go back to.

So the loader runs the cycle described in `CICLO.md`. For each month from
`loader.cycle.from-period` up to `loader.period`:

1. calculate the whole month through the API;
2. if it is not the last one, **bulk close** it, as a company would on payday;
3. and **after closing**, write the corrections that arrive in real operation: overtime for
   the month just closed, a forgotten absence for the month before that, and a handful
   deeper than the run's retro limit — so the payslip of the open month carries the warning
   that says a known correction is not being paid.

The last month stays **open**, calculated, with its arrears inside. That is what the demo
shows.

**Who gets each correction does not come out of a `Random`**: it comes from the employee's
identity and the month. With nine chained months that stopped being a convenience — a
shifted random sequence changes who has arrears in *every* later month, and then two runs
cannot be compared with anything.

Turning it off (`loader.cycle.enabled: false`) leaves the loader as it was: it seeds and
calculates nothing.

## Where this run is described

`FABRICAR-SEMILLA.md`, in the `b4rrhh/deploy` repository, is the procedure that fills an
empty instance end to end — blank database, migrations, this loader, a full month
calculated — and it states the row counts that have to come out. Its loader step already
carries the flag.

## Where the backlog is

The threads that produced these decisions live in a **private Gitea** and are not
mirrored: the Issues tab here is empty, and a `(#93)` or a `b4rrhh/backend#91` in a commit
message points at something you cannot open from GitHub. It is a known limitation, and it
leaves in reach the half that is worth more anyway — **the why is written inside the
commit**, not behind the link.
