# Vitals

> Check your Spring Boot's vitals before production does.

Vitals is a static health scanner for Spring Boot / Java backends. One command, a 0–100 score, actionable diagnostics, zero config.

```bash
jbang dev.vitals:vitals-cli:0.1.0 .
```

Supports **Spring Boot 3.2+** on **Java 21+** only. Spring Boot 2.x is intentionally not supported.

## Status

MVP: all 10 Tier 1 rules shipped end-to-end (rule + tests + CLI + docs). See the table below.

## Rule catalog (Tier 1)

| ID        | Module                | Status    | Title                                                              |
|-----------|-----------------------|-----------|--------------------------------------------------------------------|
| JPA-001   | `vitals-rules-jpa`    | shipped   | `FetchType.EAGER` on `@ManyToOne`/`@OneToOne`/`@ManyToMany`        |
| JPA-002   | `vitals-rules-jpa`    | shipped   | N+1 query — association getter inside a loop                       |
| JPA-003   | `vitals-rules-jpa`    | shipped   | `spring.jpa.open-in-view=true`                                     |
| TX-001    | `vitals-rules-spring` | shipped   | Blocking I/O inside `@Transactional`                               |
| TX-002    | `vitals-rules-spring` | shipped   | `@Transactional` on private/protected method                       |
| DI-001    | `vitals-rules-spring` | shipped   | Field injection (`@Autowired` on field)                            |
| SEC-001   | `vitals-rules-spring` | shipped   | Actuator endpoints exposed without authentication                  |
| CFG-001   | `vitals-rules-spring` | shipped   | Hardcoded secrets without `${...}` placeholder                     |
| JVM-001   | `vitals-rules-jvm`    | shipped   | Container heap not configured                                      |
| KAFKA-001 | `vitals-rules-kafka`  | shipped   | `enable.auto.commit=true` with manual processing                   |

See [`docs/rules/`](docs/rules) for rule documentation.

## Build

```bash
./gradlew check        # spotless + checkstyle + errorprone/nullaway + tests + coverage
./gradlew :vitals-cli:run --args="path/to/spring-boot/project"
```

## License

[Apache 2.0](LICENSE).
