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

# 10. Retry & Failure Handling

The migration framework has been designed to provide partition-level fault tolerance.

Instead of restarting an entire migration, only the failed partition is retried.

Each partition maintains its execution state independently inside DynamoDB.

---

# 10.1 Partition State Machine

Each partition progresses through the following lifecycle.

```text

                 +-------------+
                 |   PENDING   |
                 +------+------+ 
                        |
                        |
                        v
                 +-------------+
                 |  RUNNING    |
                 +------+------+ 
                        |
        +---------------+----------------+
        |                                |
        |                                |
        v                                v
+---------------+               +----------------+
|   SUCCESS     |               |    FAILED      |
+---------------+               +-------+--------+
                                        |
                              Retry Count < Limit ?
                                        |
                     +------------------+----------------+
                     |                                   |
                     | Yes                               | No
                     v                                   v

                 PENDING                            DEAD

```

---

# 10.2 Failure Scenarios

The framework considers failures independently for every component.

| Component | Possible Failure |
|------------|-----------------|
| Lambda | Invocation failure |
| Glue | ETL failure |
| Database | JDBC connectivity |
| S3 | Upload failure |
| Schema | Invalid schema |
| Data | Corrupt records |
| DynamoDB | Metadata update failure |

Each failure is isolated to a single partition.

---

# 10.3 Lambda Failure Handling

Possible failures include:

- Unable to load configuration
- Unable to generate partitions
- Unable to invoke Glue
- Unable to acquire processing lock

If Lambda fails before invoking Glue:

```
RUNNING

↓

PENDING
```

The partition becomes available during the next execution.

No manual intervention is required.

---

# 10.4 Glue Failure Handling

Possible failures

- JDBC timeout
- Out of memory
- Invalid schema
- S3 write failure
- Spark executor failure

Glue itself retries failed Spark tasks.

If the Glue Job ultimately fails:

1. Glue emits a FAILED event.
2. EventBridge captures the event.
3. Status Lambda updates DynamoDB.
4. Retry Count increments.
5. Partition returns to PENDING (if retry threshold not exceeded).

---

# 10.5 Retry Strategy

Retry policy is configurable.

Recommended values:

| Retry | Delay |
|--------|-------|
| Retry 1 | Immediate |
| Retry 2 | 5 Minutes |
| Retry 3 | 15 Minutes |
| Retry 4 | 30 Minutes |

Maximum retries

```
4
```

After exceeding the retry threshold

```
FAILED

↓

DEAD
```

The partition is excluded from automatic execution.

---

# 10.6 Manual Reprocessing

Operations teams may manually restart failed partitions.

The workflow is:

```text

Operator

      |

Reset Status

      |

Retry Count = 0

      |

Status = PENDING

      |

Next Lambda Execution

      |

Glue Job

```

Because output paths are deterministic, the Glue job safely overwrites the same S3 partition.

No duplicate data is generated.

---

# 10.7 Glue Completion Workflow

Instead of polling Glue from Lambda, the framework uses an event-driven completion mechanism.

```text

AWS Glue

      |

Job Completed

      |

Glue State Change Event

      |

Amazon EventBridge

      |

Status Lambda

      |

Update DynamoDB

```

Benefits:

- No polling
- Lower Lambda execution time
- Better scalability
- Automatic event delivery
- Cleaner separation of responsibilities

---

# 11. Reconciliation Strategy

Data reconciliation validates that migrated data matches the source.

Validation occurs at the partition level.

---

# 11.1 Validation Checks

Each completed partition performs the following validations.

| Validation | Source | Target |
|------------|--------|--------|
| Row Count | Database | Parquet |
| File Count | Glue | S3 |
| Partition Count | DynamoDB | S3 |
| Schema Validation | Avro | Parquet |
| Checksum (Optional) | Database | Parquet |

---

# 11.2 Row Count Validation

Example

Database

```
1,245,321 rows
```

Parquet

```
1,245,321 rows
```

If counts match

```
SUCCESS
```

Otherwise

```
RECONCILIATION_FAILED
```

---

# 11.3 Checksum Validation (Optional)

For critical tables an additional checksum may be generated.

Example

```
MD5

SHA-256

CRC32
```

The checksum is calculated independently for:

- Source dataset
- Generated Parquet dataset

Matching checksums provide higher confidence in migration integrity.

---

# 11.4 Reconciliation Metadata

Additional attributes stored in DynamoDB.

| Attribute | Description |
|------------|-------------|
| Source Row Count | Records extracted |
| Target Row Count | Records written |
| Validation Status | PASS / FAIL |
| Checksum | Optional |
| Validation Timestamp | Audit information |

---

# 12. Monitoring & Alerting

Operational visibility is provided using CloudWatch.

---

## Metrics

The following metrics should be published.

| Metric | Description |
|---------|-------------|
| Partitions Created | Number of generated partitions |
| Partitions Running | Active Glue jobs |
| Successful Partitions | Completed partitions |
| Failed Partitions | Failed partitions |
| Rows Archived | Total migrated records |
| Data Archived | Total archived size |
| Glue Runtime | Execution duration |
| Retry Count | Retry attempts |

---

## Dashboards

Recommended CloudWatch dashboards:

- Migration Progress
- Active Glue Jobs
- Success Rate
- Failure Rate
- Average Glue Runtime
- Archived Data Volume
- Retry Distribution

---

## Alerts

Alerts should be generated for:

- Glue Job Failure
- Excessive Retry Count
- Partition Dead State
- DynamoDB Errors
- JDBC Connectivity Failure
- S3 Write Failure

Alerts may be integrated with:

- Amazon SNS
- Email
- Slack
- PagerDuty

---

# 13. Security Considerations

The framework follows AWS security best practices.

---

## Authentication

- IAM Roles for Lambda
- IAM Roles for Glue

---

## Secrets

Database credentials are stored in AWS Secrets Manager.

No credentials are stored inside code or configuration files.

---

## Network

Glue connects to databases through private networking.

Traffic remains encrypted in transit.

---

## Encryption

- SSE-KMS for Amazon S3
- DynamoDB Encryption
- TLS for JDBC Connections

---

# 14. Operational Runbook

## One-Time Oracle Migration

1. Deploy configuration.
2. Trigger Lambda manually.
3. Monitor partition creation.
4. Monitor Glue executions.
5. Validate reconciliation.
6. Enable Glacier lifecycle.

---

## Daily Aurora Archival

1. EventBridge triggers Lambda.
2. Eligible partitions are generated.
3. Glue archives completed tickets.
4. Validation completes.
5. Lifecycle transitions archived data to Glacier.

---

# 15. Future Enhancements

The framework has been designed to support future enhancements without architectural changes.

Potential enhancements include:

- AWS Step Functions orchestration
- Additional database connectors
- Iceberg or Delta Lake support
- Glue Auto Scaling
- Automatic schema evolution
- Data Quality Framework integration
- AWS Glue Data Catalog integration
- Multi-account archival
- Cross-region archival

---

# 16. Conclusion

The proposed framework provides a generic, scalable, and resilient solution for both historical data migration and continuous archival.

By separating orchestration from ETL processing and using timestamp-based partitioning as the unit of work, the solution achieves:

- Scalable parallel execution
- Configuration-driven onboarding
- Partition-level retries
- Fine-grained failure isolation
- End-to-end reconciliation
- Operational observability
- Long-term archival using Amazon S3 and Glacier

This design enables future onboarding of additional source systems while reusing the same orchestration and ETL framework, minimizing development effort and operational complexity.
