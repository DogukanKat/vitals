# Design: Reusable analysis facade + JSON reporter

Date: 2026-05-15
Status: Approved (brainstorming)
Scope: Delivery-spine sub-project 0 (facade) + A (JSON reporter)

## Problem

The full analysis flow — rule registry, analyze loop, scoring — is hardcoded
inside `VitalsCli.call()`. There is no programmatic entry point. Every delivery
channel (Gradle plugin, Maven plugin, JSON/HTML output) would otherwise
duplicate this logic. The MVP end-state in `CLAUDE.md` also requires a
schema-validated JSON output, which does not exist yet (only `ConsoleReporter`).

This sub-project delivers the shared foundation the rest of the delivery spine
depends on: a reusable analysis facade plus the machine-readable JSON contract
that plugins and CI will consume.

## Goals

- Extract a reusable `VitalsEngine` facade so CLI and (future) plugins share one
  analysis code path.
- Add a custom, versioned, schema-validated JSON report.
- Keep the CLI artifact dependency-lean (no new runtime JSON dependency).

## Non-goals (YAGNI / separate sub-projects)

SARIF output, HTML reporter, Gradle/Maven plugins, Maven mirror build, rule
filtering, config file. Each is its own spec → plan → implementation cycle.

## Architecture

New module **`vitals-engine`** — depends on `vitals-core`,
`vitals-static-engine`, and all `vitals-rules-*`.

- `VitalsEngine` — facade. The rule registry (`List.of(new Jpa001…(), …)`) and
  analyze loop currently in `VitalsCli.call()` move here.
  API: `AnalysisResult analyze(Path projectRoot)`.
- `Reporter` abstraction + `ConsoleReporter` (moved from `vitals-cli`) +
  `JsonReporter` (new). Reporters live in `vitals-engine` so plugins can reuse
  them. A dedicated `vitals-report` module is deferred until a third consumer
  justifies it (YAGNI).

`vitals-core` gains one record:

- `AnalysisResult` — `Path projectRoot, int filesAnalyzed, Duration duration,
  Score score, List<Diagnostic> diagnostics`. Pure domain, no forbidden
  dependencies, immutable.

`vitals-cli` slims to: argument parsing, a `--format console|json` option,
directory validation, delegation to `VitalsEngine` + the chosen reporter, and
exit-code derivation. It depends on `vitals-engine` instead of importing each
rule class directly.

Build changes:

- `settings.gradle.kts`: add `vitals-engine`.
- JaCoCo coverage verification: include `vitals-engine` (currently scoped to
  `vitals-core` + `vitals-rules-*`); minimum 80% line coverage.

## JSON contract

JSON Schema draft 2020-12, versioned, committed as a public API surface at
`docs/schema/vitals-report-v1.schema.json` (not merely a test fixture).

Example instance:

```json
{
  "schemaVersion": "1.0",
  "tool":     { "name": "vitals", "version": "0.1.0" },
  "project":  { "root": "/abs/path", "filesAnalyzed": 247 },
  "analysis": { "durationMillis": 1823 },
  "score":    { "value": 67, "grade": "NEEDS_WORK", "errors": 3, "warnings": 8, "infos": 2 },
  "diagnostics": [
    {
      "ruleId": "JPA-001",
      "severity": "ERROR",
      "category": "JPA",
      "location": { "file": "src/main/java/com/example/Order.java", "line": 23, "column": 12 },
      "message": "FetchType.EAGER on @ManyToOne 'customer'",
      "helpUrl": "https://vitals.dev/rules/JPA-001"
    }
  ]
}
```

Decisions:

- `location.file` is **relative** to the project root — portable across
  machines/CI.
- `score.grade` uses enum names (`GREAT` / `NEEDS_WORK` / `CRITICAL`);
  `severity` is `ERROR` / `WARN` / `INFO`.
- `schemaVersion` enables forward evolution; v1 is frozen.

### Delivery

- `--format` defaults to `console` (current behavior unchanged when the flag is
  omitted).
- `vitals . --format json` → JSON to **stdout** (console output suppressed so
  the stream stays valid JSON; human-facing notes go to stderr). Pipeable to
  `jq`.
- A file is **always** written to `build/reports/vitals/report.json` so the
  future HTML reporter and plugins reuse the same artifact.
- Exit-code contract is unchanged: `errors > 0 → 1`, otherwise `0`.

### Serialization

A small hand-rolled `JsonWriter` (~40 lines), no new runtime dependency — keeps
the JBang-distributed CLI lean and follows the `CLAUDE.md` "don't add a
dependency to avoid 30 lines" rule.

Trade-off (stated explicitly): correct JSON string escaping (quotes,
backslashes, control characters, Windows-style paths, unicode) is the risk of
hand-rolling. Mitigated by exhaustive escaping tests plus schema validation of
every produced document in tests. The alternative — Jackson — gives escaping for
free but adds a runtime dependency and bloats the CLI; rejected for MVP.
Confidence in hand-rolled choice: ~70%.

## Testing

- `VitalsEngineTest`: run `analyze()` against an existing fixture project;
  assert `AnalysisResult` (score value, diagnostic count/content). Reuse
  existing fixtures.
- `JsonReporterTest`: positive (diagnostics present), negative (clean project →
  empty `diagnostics` array), edge (`"`, `\`, control char, Windows-style path
  in message/location). Plus a schema-validation test: every produced document
  validates against `docs/schema/vitals-report-v1.schema.json` using test-scope
  `com.networknt:json-schema-validator`.
- `JsonWriterTest`: escaping-focused — all escape cases + unicode.
- `VitalsCliIntegrationTest`: add a `--format json` case — parse stdout as JSON,
  assert structure, assert `build/reports/vitals/report.json` was written.
- Coverage: `vitals-engine` included in JaCoCo verification (≥ 80%).

## Error handling

- Path is not a directory → preserve current behavior: CLI exits with code 2
  (the check stays in the CLI, before calling the engine).
- `report.json` cannot be written (e.g. read-only `build/`) → SLF4J `warn`, the
  run does **not** crash; stdout JSON is still emitted; exit code still derived
  from the score.
- JSON mode never prints the console banner to stdout (it would corrupt the
  JSON). Exit-code contract unchanged.

## Affected files (anticipated)

- New module `vitals-engine/` (`VitalsEngine`, `Reporter`, `JsonReporter`,
  `JsonWriter`; `ConsoleReporter` moved from `vitals-cli`).
- `vitals-core`: add `AnalysisResult`.
- `vitals-cli`: `VitalsCli` slimmed, `--format` option, depend on
  `vitals-engine`; remove per-rule imports; `ConsoleReporter` moved out.
- `settings.gradle.kts`, root `build.gradle.kts` (JaCoCo scope).
- New `docs/schema/vitals-report-v1.schema.json`.
