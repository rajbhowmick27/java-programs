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
