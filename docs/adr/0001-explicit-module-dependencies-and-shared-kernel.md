# 0001: Explicit module dependencies and an open shared kernel

Status: Accepted (2026-10-26)

## Context

The modules and their allowed dependencies are defined in the Architecture doc (§3, §4). Spring Modulith
by default only stops a module from using another module's internal packages. It does not stop
catalog from calling tenant, for example, even though the design forbids it.

Some small types (Money, TenantId, PhoneNumber, the event base type, the tenant context) are needed by
almost every module.

## Decision

- Every module has a `package-info.java` with `@ApplicationModule(allowedDependencies = ...)` listing
  exactly the modules it may use. `ModularityTests` fails the build on any other dependency, on access to
  another module's internals, and on cycles.
- The shared kernel is a module named `shared`:
  - `type = OPEN`, so its types may be organised into sub-packages and still be visible everywhere.
  - `allowedDependencies = {}`, so it can never depend on a business module.
  - Declared in `@Modulithic(sharedModules = "shared")`, so module integration tests always load it.
- Every business module lists `shared` and `audit` explicitly.

## Consequences

- The dependency table in the Architecture doc is now checked by the build, not only by review.
- Adding a dependency is a visible, reviewable change to a `package-info.java`.
- Anything added to `shared` is visible to every module, so it must stay small and logic-free. If a type
  in `shared` starts making business decisions, move it into the module that owns that decision.
