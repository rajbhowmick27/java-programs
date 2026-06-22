# Trade Surveillance Investigation Platform - Final Architecture Design

## 1. Objective

Build a daily investigation pipeline that enriches trade surveillance alerts with multiple evidence sources, performs AI-assisted analysis, prioritizes relevant evidence, generates investigation summaries, and stores results for analyst consumption.

The solution must be:

* Highly auditable
* Cost optimized
* LLM optimized
* Scalable from 1,000 alerts/day to 100,000 alerts/day
* Cloud native on AWS
* Easy to maintain
* Deterministic and regulator friendly

---

# 2. Alert Processing Flow

Daily alert files are generated and stored as Parquet files.

Each alert contains:

* Alert Id
* Client Id
* Security Id
* Company Name
* Trade Date
* Alert Type
* Risk Attributes

The investigation engine enriches the alert through multiple evidence lenses.

```text
Parquet Alert File
        │
        ▼
Alert Context Builder
        │
        ▼
Parallel Evidence Collection
        │
 ┌──────┼──────────┬──────────┬───────────┐
 ▼      ▼          ▼          ▼
News   Market    Trading    Social
Lens    Lens      Lens       Lens
 │       │          │          │
 └───────┴──────────┴──────────┘
                │
                ▼
       Evidence Aggregator
                │
                ▼
         LLM Analysis
                │
                ▼
      Investigation Summary
                │
                ▼
            DynamoDB
```

---

# 3. Technology Stack

## Runtime

* Python 3.13

## Container

* Docker
* ECS Fargate

## Scheduling

* EventBridge Scheduler

## Storage

### Primary

* DynamoDB

### Audit Storage

* S3

## Data Processing

* Polars

## Async Processing

* AsyncIO

## Retry Framework

* Tenacity

## AWS SDK

* aioboto3

## Observability

* OpenTelemetry
* CloudWatch
* AWS X-Ray

## LLM

* OpenAI
* AWS Bedrock

## Vector Search

Possible choices:

* OpenSearch Vector
* pgvector
* Chroma
* Pinecone

---

# 4. Why Not Spring Batch Equivalent Frameworks?

The workload is not CPU-heavy ETL.

The workload is primarily:

* Network I/O
* Vector Search
* Market Data Retrieval
* Social Data Retrieval
* Trading Data Retrieval
* LLM Calls

The bottleneck is external systems.

Therefore:

* Airflow unnecessary
* Luigi unnecessary
* Spark unnecessary
* EMR unnecessary

for the current scale.

A custom Async Investigation Engine is the best fit.

---

# 5. ECS Deployment Model

## Current Volume

```text
1000 alerts/day
```

Deployment:

```text
EventBridge
      │
      ▼
ECS Fargate Task
      │
      ▼
Python Investigation Engine
```

Container command:

```bash
python investigation_batch.py
```

The container starts, processes alerts, exits successfully.

No web server required.

---

# 6. Alert Processing Strategy

## Read Alerts

Use Polars.

```python
alerts = pl.read_parquet(...)
```

Expected daily volume:

```text
1000 alerts
```

Memory footprint is small.

---

# 7. Investigation Context

Each alert receives a unique investigation id.

```python
investigation_id
```

Example:

```text
INV-20260622-12345
```

Context object:

```python
InvestigationContext
{
    investigation_id
    alert_id
    trace_id
}
```

Passed through all layers.

---

# 8. Lens Architecture

Every datasource is implemented as an independent lens.

Interface:

```python
class InvestigationLens:
    async def collect(ctx):
        pass
```

Implementations:

```text
NewsLens
MarketLens
TradingLens
SocialLens
```

Future:

```text
RelationshipLens
PositionLens
CommunicationLens
InsiderLens
```

No orchestration changes required.

---

# 9. Parallel Evidence Collection

All lenses execute concurrently.

```python
await asyncio.gather(
    news_lens.collect(),
    market_lens.collect(),
    trading_lens.collect(),
    social_lens.collect()
)
```

Benefits:

* Reduced latency
* Better ECS utilization
* Better scalability

---

# 10. News Retrieval Strategy

Inputs:

* Company
* Security
* Trade Date

Vector Search retrieves:

```text
50 Articles
```

before LLM processing.

---

# 11. Reranking Layer

Do NOT send 50 articles to LLM.

Use:

```text
Cross Encoder
BGE Reranker
Cohere Rerank
```

Reduce:

```text
50 Articles
      ↓
10 Articles
```

This significantly reduces token costs.

---

# 12. Market Lens

Retrieve:

* Price movement
* Volatility
* Volume spikes
* Significant market events

Time window:

```text
Trade Date ± configurable period
```

Output:

```json
MarketSummary
```

---

# 13. Trading Lens

Retrieve:

* Historical trading activity
* Client positions
* Previous alerts
* Pattern indicators

Output:

```json
TradingSummary
```

---

# 14. Social Lens

Retrieve:

* Relationship network
* Connected entities
* Associated individuals
* Relevant social intelligence

Output:

```json
SocialSummary
```

---

# 15. LLM Strategy

Avoid:

```text
4 LLM Calls per alert
```

Preferred:

```text
1 Final LLM Call
```

Inputs:

* Top News
* Market Summary
* Trading Summary
* Social Summary

Prompt:

```text
Prioritize evidence
Determine relevance
Explain findings
Assign risk score
Generate summary
```

Output:

```json
{
  risk_score,
  findings,
  prioritized_evidence,
  summary
}
```

---

# 16. Token Optimization

Never send raw evidence.

Compress before LLM.

Example:

```text
50 Articles
     ↓
10 Articles
     ↓
10 Summaries
     ↓
LLM
```

Benefits:

* Lower cost
* Lower latency
* Better quality

---

# 17. Retry Strategy

Use Tenacity.

Retry:

* Vector Search
* DynamoDB
* Internal APIs
* LLM Calls

Policy:

```text
Max Attempts: 3
Exponential Backoff
```

---

# 18. Concurrency Control

Limit active investigations.

Example:

```python
Semaphore(50)
```

Allows:

```text
50 concurrent investigations
```

Protects downstream systems.

---

# 19. Persistence Layer

Store investigation result.

DynamoDB:

```json
{
  investigationId,
  alertId,
  riskScore,
  summary,
  evidenceReferences,
  status
}
```

---

# 20. Audit Architecture

Audit must never block processing.

Principle:

```text
Processing Path Fast
Audit Path Async
```

---

# 21. Audit Queue

In-memory async queue.

```python
asyncio.Queue()
```

All components publish events.

Example:

```text
NEWS_RETRIEVED
MARKET_RETRIEVED
LLM_INVOKED
SUMMARY_GENERATED
```

---

# 22. Audit Writer

Separate background task.

Responsibilities:

* DynamoDB Audit Writes
* S3 Snapshots
* CloudWatch Events

Batch size:

```text
100 Events
```

Benefits:

* Low latency
* Minimal overhead

---

# 23. Investigation Event Timeline

Every investigation produces events.

Example:

```text
10:00 Alert Received
10:01 News Retrieved
10:01 Market Retrieved
10:02 Trading Retrieved
10:02 Social Retrieved
10:03 LLM Invoked
10:04 Summary Generated
10:04 Persisted
```

Used for regulatory audits.

---

# 24. Structured Logging

Use JSON logging.

Fields:

```json
{
  timestamp,
  level,
  traceId,
  investigationId,
  alertId,
  event
}
```

Store in CloudWatch.

---

# 25. Distributed Tracing

Use OpenTelemetry.

Root Span:

```text
process_alert
```

Child Spans:

```text
news_retrieval
market_retrieval
trading_retrieval
social_retrieval
reranking
llm_call
dynamodb_save
```

Export:

```text
AWS X-Ray
```

---

# 26. LLM Audit Store

For every model invocation store:

```json
{
  promptVersion,
  model,
  inputTokens,
  outputTokens,
  latency,
  timestamp
}
```

Prompt and response archived to S3.

---

# 27. Evidence Snapshot Storage

Store exactly what the model received.

Examples:

```json
{
  topNews,
  marketSummary,
  tradingSummary,
  socialSummary
}
```

Purpose:

* Reproducibility
* Compliance
* Model Review

---

# 28. S3 Audit Layout

```text
s3://investigation-audit

year=2026/
  month=06/
    day=22/
      investigation=INV-123/
        evidence.json
        prompt.json
        response.json
```

---

# 29. Metrics

Capture:

```text
Alerts Processed
Failed Alerts
LLM Latency
Token Usage
News Retrieval Latency
Market Retrieval Latency
Trading Retrieval Latency
Social Retrieval Latency
```

Export via OpenTelemetry.

---

# 30. Future Scale (100K Alerts/Day)

Current:

```text
EventBridge
     ↓
Single ECS Task
```

Future:

```text
Parquet Reader
      ↓
SQS
      ↓
Multiple ECS Workers
      ↓
Investigation Engine
```

Lens implementation remains unchanged.

Only orchestration changes.

---

# 31. Final Recommendation

Recommended Production Architecture:

```text
EventBridge
      │
      ▼
ECS Fargate
      │
      ▼
Python Async Investigation Engine
      │
      ├── Polars
      ├── AsyncIO
      ├── aioboto3
      ├── Tenacity
      ├── OpenTelemetry
      │
      ├── News Lens
      ├── Market Lens
      ├── Trading Lens
      ├── Social Lens
      │
      ▼
Evidence Aggregator
      │
      ▼
LLM Analysis
      │
      ▼
DynamoDB
      │
      ├── Async Audit Queue
      ├── CloudWatch
      ├── AWS X-Ray
      └── S3 Audit Archive
```

This architecture provides:

* Deterministic processing
* Regulatory-grade auditability
* LLM cost optimization
* Low operational complexity
* Future scalability to 100K alerts/day
* Minimal audit overhead (<1%)
* Cloud-native AWS deployment
