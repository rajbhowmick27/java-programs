# Spring Boot 3 to Spring Boot 4 Micrometer Tracing Migration Guide

## Goals

This migration guide ensures:

- Migration from Spring Boot 3.x → Spring Boot 4.x
- Distributed tracing continues working
- `traceId` and `spanId` appear in ALL logs
- Backward compatibility with B3 headers
- W3C trace propagation support
- JMS listener tracing support
- Async thread trace propagation
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

# 7. logback.xml Configuration

## Recommended logback.xml

```xml
<configuration>

    <property
            name="CONSOLE_LOG_PATTERN"
            value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} traceId=%X{traceId:-} spanId=%X{spanId:-} - %msg%n"/>

    <appender name="CONSOLE"
              class="ch.qos.logback.core.ConsoleAppender">

        <encoder>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
        </encoder>

    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>

</configuration>
```

---

# 8. Expected Logs

Example:

```text
2026-05-24 20:30:11 INFO traceId=4f3ab2f91 spanId=ab1212 Starting JMS processing
```

---

# 9. JMS Listener Tracing

## Problem

JMS listeners often execute on container-managed threads.

Without propagation:

- traceId disappears
- MDC becomes empty
- logs lose correlation

---

# 10. Recommended JMS Setup

## Add TaskDecorator

```java
@Bean
public TaskDecorator tracingTaskDecorator() {

    return runnable ->
            ContextSnapshot.captureAll().wrap(runnable);
}
```

---

# 11. Configure JMS Listener Container Factory

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

# 12. JMS Listener Example

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

# 13. If Using Existing JMS Factory From Shared Library

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

# 14. Async Method Propagation

## Problem

```java
@Async
public void execute() {
    log.info("trace missing");
}
```

---

# 15. Solution

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

# 16. Reactor Propagation

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

# 17. Manual Span Usage (Optional)

Usually unnecessary now.

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

# 18. Recommended Production Strategy

For large enterprise systems:

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

# 19. Final Migration Checklist

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

## Ensure logback.xml contains

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

# 20. Final Result

After migration:

- All REST logs contain traceId
- JMS listeners contain traceId
- Async threads contain traceId
- Reactor chains contain traceId
- MDC correlation works automatically
- No OTLP exporter errors occur
- Legacy B3 systems continue functioning
- W3C tracing works for newer systems
