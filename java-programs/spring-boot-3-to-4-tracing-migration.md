# Spring Boot 3 to Spring Boot 4 Micrometer Tracing Migration Guide

## Goals

This migration guide ensures:

- Migration from Spring Boot 3.x → Spring Boot 4.x
- Distributed tracing continues working
- `traceId` and `spanId` appear in ALL logs
- Backward compatibility with B3 headers
- W3C trace propagation support
- JMS listener tracing support
- JMS publisher tracing propagation
- Async thread trace propagation
- Safe ThreadLocal/MDC cleanup
- No OTLP exporter errors
- No dependency on external tracing backend

---

# 1. Key Migration Changes

| Area | Spring Boot 3 | Spring Boot 4 |
|---|---|---|
| Tracing Engine | Micrometer Tracing | Micrometer Observation + Tracing |
| Preferred Backend | Brave or OTel | OpenTelemetry-first |
| Default Trace Headers | W3C preferred | W3C dominant |
| Legacy Support | B3 supported | B3 compatibility mode |
| Async Propagation | Manual decorators common | Context propagation standardized |
| MDC Correlation | TraceContext bridge | Observation bridge |
| JMS Propagation | Partial/manual | Better but still needs executor propagation |

---

# 2. Remove Old Dependencies

Remove old Sleuth dependency if present:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
```

Remove Zipkin exporter if not using it:

```xml
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

Remove OTLP exporter if you do not want exporter errors:

```xml
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

---

# 3. Recommended Dependencies for Spring Boot 4

## pom.xml

Use ONLY these tracing dependencies:

```xml
<dependencies>

    <!-- Spring Boot Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Micrometer Tracing -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>

    <!-- Context Propagation -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>context-propagation</artifactId>
    </dependency>

</dependencies>
```

---

# 4. Disable OTLP Exporter Completely

Since no exporter is required, disable all exporting.

## application.yml

```yaml
management:
  tracing:
    enabled: true

    sampling:
      probability: 1.0

    propagation:
      consume: [b3, w3c]
      produce: [b3, w3c]

  otlp:
    tracing:
      enabled: false
```

---

# 5. Why This Setup Works

This configuration:

- Keeps tracing fully local
- Generates traceId/spanId
- Injects MDC automatically
- Supports distributed propagation
- Avoids exporter connection failures
- Avoids OTLP timeout logs
- Maintains backward compatibility

---

# 6. Trace Header Compatibility

## Incoming Headers Supported

Application accepts BOTH:

### Legacy B3

```http
X-B3-TraceId
X-B3-SpanId
```

### W3C

```http
traceparent
tracestate
```

---

## Outgoing Headers Produced

Application produces BOTH:

```yaml
produce: [b3, w3c]
```

This helps mixed environments during migration.

---

# 7. Logging Configuration

## Why logback.xml Gets Renamed

Spring Boot rewrite recipes commonly rename:

```text
logback.xml
```

to:

```text
logback-spring.xml
```

This is expected and recommended.

---

# 8. Difference Between logback.xml vs logback-spring.xml

| File | Spring-Aware | Recommended |
|---|---|---|
| logback.xml | No | Older/basic |
| logback-spring.xml | Yes | Recommended |

`logback-spring.xml` supports:
- Spring profiles
- Environment properties
- `<springProperty>`
- Dynamic configuration

---

# 9. Recommended Logging File

Use:

```text
src/main/resources/logback-spring.xml
```

---

# 10. Recommended logback-spring.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>

<configuration>

    <springProperty
            scope="context"
            name="APP_NAME"
            source="spring.application.name"/>

    <property
            name="LOG_PATTERN"
            value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level ${APP_NAME} %logger{36} traceId=%X{traceId:-} spanId=%X{spanId:-} - %msg%n"/>

    <appender name="CONSOLE"
              class="ch.qos.logback.core.ConsoleAppender">

        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>

    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>

</configuration>
```

---

# 11. Why This Logging Pattern Matters

This pattern provides:

- traceId
- spanId
- thread name
- service name
- logger name

Example:

```text
2026-05-24 22:10:11.123 [DefaultMessageListenerContainer-1]
INFO payment-service
traceId=abc123
spanId=xyz789
com.test.PaymentListener
- Processing JMS message
```

---

# 12. Important JMS Logging Clarification

NO special JMS-specific logback.xml changes are required.

JMS tracing works through:
- MDC propagation
- tracing scope restoration
- listener executor propagation

NOT through custom logback appenders.

As long as this exists:

```xml
%X{traceId:-}
%X{spanId:-}
```

JMS logs automatically print traceId.

---

# 13. Expected Logs

Example:

```text
2026-05-24 20:30:11 INFO traceId=4f3ab2f91 spanId=ab1212 Starting JMS processing
```

---

# 14. JMS Publisher → Listener Trace Propagation

## Scenario

```text
Service A
   ↓
Publishes JMS Message to Topic
   ↓
Broker Fanout
   ↓
Queue Subscription
   ↓
Service B JMS Listener
   ↓
Calls REST API
```

---

# 15. What Happens Automatically

Publisher side:

```text
Current Span
    ↓
JMS Header Injection
```

Listener side:

```text
JMS Header Extraction
    ↓
Create Consumer Span
    ↓
Put traceId in MDC
    ↓
Listener executes
```

REST propagation:

```text
Current Trace
    ↓
HTTP Header Injection
    ↓
Downstream service continues same trace
```

---

# 16. JMS Trace Headers

Micrometer/OpenTelemetry automatically injects:

## W3C

```text
traceparent
tracestate
baggage
```

## B3 Compatibility

```text
X-B3-TraceId
X-B3-SpanId
```

---

# 17. Thread Reuse Problem

JMS listener threads are reused:

```text
Thread-7
  Message-A
  Message-B
  Message-C
```

If MDC is NOT cleared:
- Message-B may accidentally use Message-A traceId
- tracing corruption occurs

---

# 18. How Micrometer Prevents Leakage

Micrometer/OpenTelemetry internally uses tracing scopes:

```java
try (Scope scope = ...) {
}
```

Meaning:
- MDC populated at start
- MDC cleared at end
- ThreadLocal cleared automatically

Thus:

```text
Message-A traceId != Message-B traceId
```

even on same thread.

---

# 19. JMS Listener Tracing

## Problem

JMS listeners often execute on container-managed threads.

Without propagation:
- traceId disappears
- MDC becomes empty
- logs lose correlation

---

# 20. Add TaskDecorator

```java
@Bean
public TaskDecorator tracingTaskDecorator() {

    return runnable ->
            ContextSnapshot.captureAll().wrap(runnable);
}
```

---

# 21. Configure JMS Listener Container Factory

```java
@Bean
public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
        ConnectionFactory connectionFactory,
        TaskDecorator taskDecorator) {

    DefaultJmsListenerContainerFactory factory =
            new DefaultJmsListenerContainerFactory();

    factory.setConnectionFactory(connectionFactory);

    ThreadPoolTaskExecutor executor =
            new ThreadPoolTaskExecutor();

    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(20);

    executor.setTaskDecorator(taskDecorator);

    executor.initialize();

    factory.setTaskExecutor(executor);

    return factory;
}
```

---

# 22. JMS Listener Example

```java
@Component
public class PaymentListener {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentListener.class);

    @JmsListener(destination = "PAYMENT.QUEUE")
    public void receive(String message) {

        log.info("Received JMS message");

    }
}
```

Now logs automatically include:

```text
traceId=xxxx
spanId=xxxx
```

---

# 23. If Using Existing JMS Factory From Shared Library

If factory comes from dependency JAR and cannot be modified directly:

Use BeanPostProcessor.

## BeanPostProcessor Example

```java
@Bean
public BeanPostProcessor tracingJmsPostProcessor(
        TaskDecorator taskDecorator) {

    return new BeanPostProcessor() {

        @Override
        public Object postProcessAfterInitialization(
                Object bean,
                String beanName) {

            if (bean instanceof DefaultJmsListenerContainerFactory factory) {

                ThreadPoolTaskExecutor executor =
                        new ThreadPoolTaskExecutor();

                executor.setCorePoolSize(10);

                executor.setTaskDecorator(taskDecorator);

                executor.initialize();

                factory.setTaskExecutor(executor);
            }

            return bean;
        }
    };
}
```

---

# 24. What If JMS Trace Headers Are Missing

This commonly happens with:
- legacy publishers
- IBM MQ producers
- non-Java producers
- COBOL systems
- old ActiveMQ integrations

In that case:
- listener receives no trace context
- new trace should be created

---

# 25. Recommended Fallback Pattern

DO NOT only use:

```java
MDC.put(...)
```

Instead create a proper tracing scope.

```java
@Component
public class PaymentListener {

    private final Tracer tracer;

    public PaymentListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @JmsListener(destination = "PAYMENT.QUEUE")
    public void receive(String message) {

        Span newSpan =
                tracer.nextSpan()
                      .name("jms-message-processing");

        try (Tracer.SpanInScope ws =
                     tracer.withSpan(newSpan.start())) {

            log.info("processing message");

            // downstream REST calls automatically use same traceId

        } finally {

            newSpan.end();

        }
    }
}
```

---

# 26. Why Manual MDC Is Bad

Avoid:

```java
MDC.put("traceId", UUID.randomUUID().toString());
```

because:
- no actual tracing context exists
- REST propagation fails
- async propagation fails
- child spans fail
- MDC leakage risk exists

---

# 27. Async Method Propagation

## Problem

```java
@Async
public void execute() {
    log.info("trace missing");
}
```

---

# 28. Solution

## Async Executor

```java
@Bean
public Executor asyncExecutor(
        TaskDecorator taskDecorator) {

    ThreadPoolTaskExecutor executor =
            new ThreadPoolTaskExecutor();

    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(20);

    executor.setTaskDecorator(taskDecorator);

    executor.initialize();

    return executor;
}
```

---

# 29. Reactor Propagation

For WebFlux/Reactor systems:

```java
@PostConstruct
public void init() {
    Hooks.enableAutomaticContextPropagation();
}
```

This preserves:
- traceId
- spanId
- MDC

across Reactor chains.

---

# 30. Manual Span Usage (Optional)

Still supported:

```java
@Autowired
Tracer tracer;

public void execute() {

    Span span = tracer.nextSpan().name("custom");

    try (Tracer.SpanInScope ws =
                 tracer.withSpan(span.start())) {

        log.info("inside custom span");

    } finally {
        span.end();
    }
}
```

---

# 31. How to Verify JMS Propagation Works

Log JMS headers:

```java
Enumeration<?> names = message.getPropertyNames();

while (names.hasMoreElements()) {

    String key = names.nextElement().toString();

    log.info("{}={}", key,
             message.getObjectProperty(key));
}
```

You should see:

```text
traceparent
```

or:

```text
X-B3-TraceId
```

---

# 32. Recommended Production Strategy

Recommended:
- Micrometer Observation
- OpenTelemetry bridge
- W3C propagation
- B3 compatibility during migration

Avoid:
- Spring Cloud Sleuth
- Brave-only APIs
- manual MDC manipulation

---

# 33. Final Migration Checklist

## Remove

- Sleuth
- Zipkin exporter
- OTLP exporter

---

## Add

```xml
micrometer-tracing-bridge-otel
context-propagation
```

---

## Ensure

```yaml
management.tracing.enabled=true
```

---

## Enable Compatibility

```yaml
consume: [b3, w3c]
produce: [b3, w3c]
```

---

## Use

```text
logback-spring.xml
```

---

## Ensure Logging Pattern Contains

```xml
%X{traceId:-}
%X{spanId:-}
```

---

## Configure

- JMS task executor propagation
- Async task propagation
- Reactor propagation

---

# 34. Final Result

After migration:

- REST logs contain traceId
- JMS publisher propagates traceId
- JMS listener logs contain traceId
- Downstream REST calls continue same trace
- Async threads contain traceId
- Reactor chains contain traceId
- MDC cleanup works automatically
- Thread reuse remains safe
- No OTLP exporter errors occur
- Legacy B3 systems continue functioning
- W3C tracing works for newer systems
