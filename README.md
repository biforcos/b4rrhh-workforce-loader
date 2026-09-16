# B4RRHH — workforce loader

**B4RRHH is a personnel administration system and a configurable payroll engine.**
Employment history is temporal by construction — the domain itself refuses overlaps and
gaps instead of hoping the database will catch them — and payroll is computed from a
dependency graph that is configuration rather than code, so any amount on a payslip can be
opened all the way down to the step that produced it.

This repository is an external CLI that fills an empty instance: it hires a whole
workforce, terminates part of it, rehires some of those, and moves people between work
centres, contracts and cost centres along the way — all of it through the same public API
a person would use. Everything else — the other repositories and the documents they share
— starts at **`b4rrhh/workspace`**, which is [`../README.md`](../README.md) once it is laid
out beside this one.

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

## Where this run is described

`FABRICAR-SEMILLA.md`, in the `b4rrhh/deploy` repository, is the procedure that fills an
empty instance end to end — blank database, migrations, this loader, a full month
calculated — and it states the row counts that have to come out. Its loader step already
carries the flag.
