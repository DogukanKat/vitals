# KAFKA-001 — `enable.auto.commit=true` with a `@KafkaListener`

| Field    | Value                  |
| -------- | ---------------------- |
| Severity | Error                  |
| Category | Kafka                  |
| Since    | 0.1.0                  |
| Module   | `vitals-rules-kafka`   |

## What it checks

The rule fires only when **both** signals are present:

1. A config file explicitly sets one of `spring.kafka.consumer.enable-auto-commit`, `spring.kafka.properties.enable.auto.commit`, or the raw `enable.auto.commit` to `true`, **and**
2. The project contains at least one `@KafkaListener` (the manual record-processing pattern).

It fires only on an *explicit* `true`. Spring Kafka's listener container defaults `enable.auto.commit` to `false` and drives commits itself, so an unset value is already safe — this rule does not flag omission (contrast with JPA-003, where Spring Boot's default is the dangerous one).

The diagnostic attaches to the config line that sets the property.

## Why it matters

`enable.auto.commit=true` makes the Kafka client commit the *last polled* offsets on a timer (`auto.commit.interval.ms`, default 5s) — independently of whether your listener has finished, or even started, processing those records.

The failure sequence:

1. `poll()` returns records 100–199. The client schedules their offsets for the next auto-commit.
2. The auto-commit timer fires and commits offset 200 — your listener has processed up to record 150.
3. The pod is killed (deploy, OOM, node drain) before records 151–199 are processed.
4. On restart the consumer resumes from the committed offset 200. **Records 151–199 are never processed and never redelivered.**

This is silent. There is no exception, no dead-letter, no metric that obviously screams "data loss" — just a gap in downstream state that surfaces weeks later as "some orders never got an invoice". It is the single most common cause of mysterious missing-message incidents in Spring Kafka applications, precisely because the code *looks* like at-least-once processing (there is a listener method with business logic) while the offset management is fire-and-forget.

`@KafkaListener` exists to give you at-least-once semantics. Pairing it with auto-commit throws those semantics away.

## How to fix

Disable auto-commit and acknowledge after the work is durably done:

```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false
    listener:
      ack-mode: MANUAL
```

```java
@KafkaListener(topics = "orders", groupId = "orders")
public void onOrder(String payload, Acknowledgment ack) {
    process(payload);   // throws on failure -> no ack -> record redelivered
    ack.acknowledge();
}
```

If you do not need fine-grained control, the default container ack mode (`BATCH`, with `enable-auto-commit: false`) already commits only after the listener returns successfully for the polled batch — that alone fixes the loss. The key change is **`enable-auto-commit: false`**; manual `Acknowledgment` is the explicit, reviewable form.

For idempotent consumers that genuinely tolerate reprocessing and want maximum throughput, document that decision next to the property and suppress the rule — but verify the consumer is actually idempotent first.

## When to suppress

- The consumer is provably idempotent *and* loss-tolerant (metrics/telemetry pipelines where a dropped point is acceptable), and the team has signed off. Document it at the property.
- The listener is a no-op probe / health check that does no durable work.

For any consumer that mutates state downstream, there is no defensible reason to suppress.

## References

- Spring Kafka — [Committing Offsets](https://docs.spring.io/spring-kafka/reference/kafka/committing-offsets.html)
- Confluent — [Consumer offset management and `enable.auto.commit`](https://docs.confluent.io/platform/current/clients/consumer.html#offset-management)
- Spring Kafka — [`@KafkaListener` and acknowledgment modes](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listener-container.html)
