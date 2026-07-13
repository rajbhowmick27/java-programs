# Generic Data Migration & Archival Framework

**Version:** 1.0

**Author:** Raj Bhowmick

**Status:** Draft

---

# Table of Contents

1. Introduction
2. Problem Statement
3. Functional Requirements
4. Non Functional Requirements
5. Proposed Solution
6. High Level Architecture
7. Component Responsibilities
8. End-to-End Processing Flow

---

# 1. Introduction

This document describes the architecture for a generic data migration and archival framework capable of migrating historical relational data from multiple source databases into Amazon S3 while maintaining an optimized archival format.

The framework is designed to support both:

- One-time historical migrations
- Continuous rolling archival

using the same architecture and processing pipeline.

The design focuses on scalability, resiliency, configurability and operational simplicity.

---

# 2. Problem Statement

The organization has multiple operational databases that contain historical transactional data.

Current requirements include:

## Oracle

Migrate approximately **12 TB** of historical data from an on-premise Oracle database into AWS.

Characteristics:

- One-time migration
- Source data is immutable
- No Change Data Capture (CDC)
- Data stored in relational format
- Final storage should be Parquet with predefined Avro schema

---

## Aurora PostgreSQL

Archive completed ticket records after they become older than **180 days**.

Characteristics:

- Daily archival process
- Only completed records
- Incremental archival
- Data should follow the same processing pipeline as Oracle

---

Instead of building separate solutions for each source, a common configurable framework is required.

---

# 3. Functional Requirements

The solution shall support:

- One-time Oracle migration
- Rolling Aurora archival
- Timestamp based partitioning
- Generic source configuration
- Schema driven conversion
- Avro generation
- Parquet generation
- S3 archival
- Glacier lifecycle transition
- Partition level checkpointing
- Retry and restart
- Manual reprocessing
- Reconciliation reports

---

# 4. Non Functional Requirements

## Scalability

- Support datasets larger than 10 TB
- Support billions of rows
- Parallel processing
- Horizontal scaling

---

## Reliability

- No duplicate processing
- Restart from failed partition
- Idempotent execution

---

## Performance

- Parallel extraction
- Optimized Parquet files
- Configurable concurrency

---

## Security

- IAM Roles
- Secrets Manager
- Encryption at Rest
- Encryption in Transit

---

## Extensibility

The framework should support future onboarding of databases without requiring Glue code changes.

Examples:

- SQL Server
- MySQL
- PostgreSQL
- DB2

---

# 5. Proposed Solution

The proposed solution follows a metadata-driven architecture.

The migration logic is completely configuration driven.

Every migration consists of independent timestamp partitions.

Each partition becomes an independent execution unit.

Each execution unit is tracked in DynamoDB.

A Spring Boot Lambda orchestrates partition generation and Glue invocation.

AWS Glue performs all heavy ETL processing.

The final Parquet files are stored in Amazon S3.

Lifecycle policies automatically archive data into Glacier.

---

# 6. High Level Architecture

```text

                     +--------------------------------+
                     |       EventBridge              |
                     |                                |
                     |  • One-Time Trigger            |
                     |  • Daily Scheduler             |
                     +---------------+----------------+
                                     |
                                     |
                                     v

                     +--------------------------------+
                     | Spring Boot Lambda             |
                     | Migration Orchestrator         |
                     +---------------+----------------+
                                     |
             +-----------------------+-----------------------+
             |                                               |
             |                                               |
             v                                               v

     Migration Configuration                     DynamoDB
       (Source Metadata)                   Partition Status Store

             |                                               |
             +-----------------------+-----------------------+
                                     |
                                     |
                                     v

                      Generate Timestamp Partitions

                                     |
                                     |
                                     v

                      Invoke AWS Glue Job (Parallel)

                                     |
                                     |
                                     v

                  +----------------------------------------+
                  |      AWS Glue (Apache Spark)           |
                  |                                        |
                  | • Read Source Database                 |
                  | • Apply Avro Schema                    |
                  | • Data Validation                      |
                  | • Write Raw Avro                       |
                  | • Convert to Parquet                   |
                  | • Update Partition Status              |
                  +------------------+---------------------+
                                     |
                                     |
                                     v

                           Amazon S3 Archive

                    Raw Layer          Curated Layer

                                     |
                                     |
                                     v

                           Glacier Lifecycle

```

---

# 7. Component Responsibilities

| Component | Responsibility |
|------------|---------------|
| EventBridge | Triggers one-time or scheduled execution |
| Spring Boot Lambda | Orchestrates partition generation and Glue invocation |
| Configuration Store | Stores migration metadata for every table |
| DynamoDB | Tracks execution status of every partition |
| AWS Glue | Performs ETL, schema conversion and archival |
| Amazon S3 | Stores raw Avro and curated Parquet |
| Glacier Lifecycle | Archives historical Parquet files |

---

# 8. End-to-End Processing Flow

The overall migration flow consists of the following stages.

## Stage 1

Migration request is initiated.

Trigger options:

- Manual invocation
- EventBridge Scheduler

---

## Stage 2

Migration configuration is loaded.

The configuration determines:

- Source Database
- Source Table
- Timestamp Column
- Partition Strategy
- Query Type
- Destination Path
- Avro Schema

---

## Stage 3

Timestamp partitions are generated.

Examples:

Oracle

```
2020-01
2020-02
2020-03
...
```

Aurora

```
2026-01-01
2026-01-02
2026-01-03
...
```

Each partition represents one Glue execution.

---

## Stage 4

Partitions are registered in DynamoDB.

Each partition initially enters the **PENDING** state.

---

## Stage 5

Lambda picks eligible partitions and invokes Glue.

Each Glue job processes exactly one partition.

This allows:

- Independent retries
- Parallel execution
- Easy restart
- Failure isolation

---

## Stage 6

Glue reads the partition data from the source database.

The extracted data is:

- Validated
- Converted into Avro
- Converted into Parquet
- Written into Amazon S3

---

## Stage 7

After successful processing:

- DynamoDB status is updated
- Metrics are captured
- Partition marked SUCCESS

If processing fails:

- Partition marked FAILED
- Retry initiated according to retry policy

---

## High Level Design Summary

The proposed architecture separates orchestration from data processing.

The Lambda service remains lightweight and stateless, while all heavy ETL processing is delegated to Apache Spark running in AWS Glue.

Each timestamp partition becomes an independent execution unit, allowing the framework to scale horizontally while providing fine-grained recovery, retry, and reconciliation capabilities.

# 9. Low Level Design (LLD)

This section describes the internal workflow of each component participating in the migration framework.

The framework has been designed such that orchestration and data processing are completely decoupled.

The Lambda service is responsible only for orchestration, while AWS Glue performs all ETL activities.

---

# 9.1 Migration Configuration

The framework is metadata driven.

Instead of hardcoding tables or SQL inside the Glue job, every source table is onboarded through configuration.

Each configuration contains:

| Property | Description |
|-----------|-------------|
| Source Type | Oracle / Aurora PostgreSQL |
| Source Connection | JDBC Connection Name |
| Table Name | Source table |
| Timestamp Column | Column used for partitioning |
| Partition Type | Monthly / Daily |
| Query Type | FULL / ROLLING_180 |
| Additional Filter | Optional SQL filter |
| Avro Schema Path | S3 location of .avsc |
| Destination Bucket | S3 Archive Bucket |
| Destination Prefix | S3 Folder Prefix |
| Parallel Partitions | Maximum concurrent Glue jobs |

This approach enables onboarding of additional tables without requiring any changes to the Glue application.

---

# 9.2 Spring Boot Lambda Orchestrator

The Lambda acts as the orchestration engine for the migration framework.

It is intentionally lightweight and does not perform any data transformation.

Its responsibilities are limited to:

- Reading migration configuration
- Generating timestamp partitions
- Managing execution state
- Invoking Glue jobs
- Managing retries
- Supporting reprocessing

---

## Trigger Mechanisms

### One-Time Oracle Migration

The migration Lambda can be triggered by:

- Manual AWS Console execution
- API Gateway endpoint
- AWS CLI
- Step Functions (future enhancement)

This trigger is executed only once for historical migration.

---

### Rolling Aurora Migration

The rolling archival Lambda is triggered by EventBridge.

Example schedule:

```
Every day at 02:00 UTC
```

Each execution archives newly eligible records older than 180 days.

---

# 9.3 Lambda Processing Flow

The Lambda executes the following workflow.

## Step 1 — Load Migration Configuration

The Lambda loads all active migration configurations.

Example:

```
Oracle Orders
Oracle Transactions
Aurora Ticket
Aurora Audit
```

Each configuration is processed independently.

---

## Step 2 — Determine Processing Window

The processing window depends on the query type.

### FULL Migration

The Lambda determines the complete historical range.

Example

```
Start Date

2018-01-01

End Date

2025-12-31
```

---

### ROLLING_180

The Lambda calculates:

```
Eligible Date = Current Date - 180 Days
```

Only records older than this date become eligible.

---

## Step 3 — Generate Timestamp Partitions

The processing window is divided into independent partitions.

Oracle example

Monthly

```
2018-01

2018-02

2018-03

...

2025-12
```

Aurora example

Daily

```
2026-01-01

2026-01-02

2026-01-03
```

Partition size remains configurable.

Large tables may use weekly partitions while smaller tables may use monthly partitions.

---

## Step 4 — Register Partitions

Every generated partition is inserted into DynamoDB.

Each partition receives the status

```
PENDING
```

Duplicate partitions are ignored.

This makes the process idempotent.

---

## Step 5 — Select Work Item

The Lambda queries DynamoDB for the next executable partition.

Selection criteria:

```
Status = PENDING
```

or

```
Status = FAILED

Retry Count < Max Retry
```

---

## Step 6 — Acquire Processing Lock

Before invoking Glue, Lambda performs a conditional update.

```
PENDING

↓

RUNNING
```

Only one Lambda execution can acquire the lock.

This prevents duplicate Glue execution.

---

## Step 7 — Invoke Glue Job

The Lambda starts a Glue Job for the selected partition.

The following runtime parameters are passed:

| Parameter | Description |
|-----------|-------------|
| SOURCE | oracle / aurora |
| TABLE | Source table |
| START_TS | Partition start timestamp |
| END_TS | Partition end timestamp |
| QUERY_TYPE | FULL / ROLLING_180 |
| DESTINATION_BUCKET | Archive bucket |
| DESTINATION_PREFIX | S3 folder |
| AVRO_SCHEMA | S3 schema path |
| EXECUTION_ID | Unique execution identifier |

One Glue Job always processes exactly one partition.

This ensures:

- Fine-grained retry
- Better parallelism
- Failure isolation

---

## Step 8 — Persist Execution Metadata

Immediately after Glue invocation the Lambda updates DynamoDB.

Additional attributes stored:

- Glue Job Run ID
- Execution Time
- Invocation Timestamp
- Execution Status

The Lambda does not wait for Glue completion.

It exits immediately after successful invocation.

---

# 9.4 Glue Job Design

The Glue application is implemented once and reused for every migration.

No source-specific logic is hardcoded.

The behavior is driven entirely by runtime parameters.

---

## Glue Processing Workflow

```
Read Runtime Parameters

↓

Load Migration Configuration

↓

Construct SQL Predicate

↓

Read Source Database

↓

Apply Avro Schema

↓

Validate Dataset

↓

Write Raw Avro

↓

Convert to Parquet

↓

Publish Metrics

↓

Update DynamoDB

↓

Exit
```

---

## SQL Generation

The Glue job dynamically constructs SQL based on the migration type.

### Oracle

```
SELECT *
FROM ORDERS
WHERE UPDATED_TIMESTAMP >= :START_TS
AND UPDATED_TIMESTAMP < :END_TS
```

---

### Aurora

```
SELECT *
FROM TICKET

WHERE STATUS='COMPLETED'

AND UPDATED_TIMESTAMP < CURRENT_DATE - INTERVAL '180 days'

AND UPDATED_TIMESTAMP >= :START_TS

AND UPDATED_TIMESTAMP < :END_TS
```

Glue never contains hardcoded SQL.

---

## Data Transformation

After reading the source data, Glue performs:

1. Schema validation
2. Data type conversion
3. Avro serialization
4. Parquet conversion
5. Compression
6. Partitioned S3 write

---

## Output Structure

```
S3

raw/

    oracle/

        orders/

            year=2024/

                month=06/

curated/

    oracle/

        orders/

            year=2024/

                month=06/
```

The Raw layer is retained temporarily for reconciliation.

The Curated layer is retained permanently.

---

# 9.5 DynamoDB Design

DynamoDB serves as the execution metadata repository.

It does not store business data.

It only stores partition execution information.

---

## Primary Key

| Attribute | Description |
|-----------|-------------|
| PK | TABLE#<TableName> |
| SK | PARTITION#<PartitionStartTimestamp> |

---

## Attributes

| Attribute | Description |
|-----------|-------------|
| Source | Oracle / Aurora |
| Query Type | FULL / ROLLING_180 |
| Start Timestamp | Partition Start |
| End Timestamp | Partition End |
| Status | PENDING / RUNNING / SUCCESS / FAILED |
| Retry Count | Retry Attempts |
| Glue Job Run ID | Glue execution identifier |
| Row Count | Extracted records |
| Raw Path | Raw Avro location |
| Curated Path | Parquet location |
| Execution Start | Processing start time |
| Execution End | Processing completion time |
| Duration | Processing duration |
| Error Message | Failure reason |
| Checksum | Optional reconciliation checksum |

---

# 9.6 Processing Sequence

```
EventBridge / Manual Trigger

        │

        ▼

Spring Boot Lambda

        │

        ├── Read Configuration

        │

        ├── Generate Partitions

        │

        ├── Insert DynamoDB Records

        │

        ├── Acquire Processing Lock

        │

        ├── Invoke Glue

        │

        ▼

AWS Glue

        │

        ├── Read Database

        ├── Apply Schema

        ├── Validate

        ├── Write Avro

        ├── Write Parquet

        ├── Update DynamoDB

        ▼

Amazon S3
```

---

# 9.7 Component Interaction Summary

| Component | Responsibility |
|------------|---------------|
| EventBridge | Schedule rolling archival |
| Manual/API | Trigger historical migration |
| Spring Boot Lambda | Orchestrate execution and invoke Glue |
| DynamoDB | Maintain execution state and checkpointing |
| AWS Glue | Extract, transform and archive data |
| S3 | Persist archived data |
| Glacier Lifecycle | Long-term archival |

---

## Low Level Design Summary

The orchestration layer is intentionally lightweight and stateless.

All execution state is persisted in DynamoDB, allowing any Lambda instance to resume processing safely.

AWS Glue performs one partition per execution, providing fine-grained scalability, simplified retries, and improved failure isolation.

This design enables both historical migration and continuous archival to reuse the same processing pipeline while remaining fully configuration driven.
