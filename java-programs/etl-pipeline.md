# AWS Data Migration & Archival Pipeline (Oracle + Aurora → S3 → Glacier)

## Overview

This document describes a **production-grade, generic pipeline** to:

* Migrate **12TB Oracle data (one-time)**
* Archive **Aurora PostgreSQL data (rolling 180-day rule)**
* Convert to **Avro → Parquet**
* Store in **S3 → Glacier**
* Track progress using **DynamoDB**

---

## Architecture

```
Spring Boot Lambda (Orchestrator)
        ↓
DynamoDB (Partition Tracking)
        ↓
AWS Glue (Spark - Java)
        ↓
S3 (Raw Avro → Curated Parquet)
        ↓
Lifecycle → Glacier
```

---

## 1. DynamoDB Table Design

### Table: `migration_status`

| Attribute       | Type   | Description                          |
| --------------- | ------ | ------------------------------------ |
| pk              | String | TABLE#<table_name>                   |
| sk              | String | PARTITION#<start_ts>                 |
| source          | String | oracle / aurora                      |
| query_type      | String | FULL / ROLLING_180                   |
| start_ts        | String | partition start                      |
| end_ts          | String | partition end                        |
| status          | String | PENDING / RUNNING / SUCCESS / FAILED |
| row_count       | Number | processed rows                       |
| s3_raw_path     | String | raw location                         |
| s3_parquet_path | String | parquet location                     |
| last_updated    | String | timestamp                            |

---

## 2. Spring Boot Lambda (Orchestrator)

### Dependencies (Maven)

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>dynamodb</artifactId>
    </dependency>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>glue</artifactId>
    </dependency>
</dependencies>
```

---

### Partition Generator

```java
public List<Partition> generateMonthlyPartitions(LocalDate start, LocalDate end) {
    List<Partition> partitions = new ArrayList<>();

    LocalDate current = start;
    while (current.isBefore(end)) {
        LocalDate next = current.plusMonths(1);

        partitions.add(new Partition(
                current.toString(),
                next.toString()
        ));

        current = next;
    }
    return partitions;
}
```

---

### Save to DynamoDB

```java
public void savePartition(Partition p) {
    Map<String, AttributeValue> item = new HashMap<>();

    item.put("pk", AttributeValue.builder().s("TABLE#orders").build());
    item.put("sk", AttributeValue.builder().s("PARTITION#" + p.getStart()).build());
    item.put("status", AttributeValue.builder().s("PENDING").build());

    dynamoDbClient.putItem(r -> r.tableName("migration_status").item(item));
}
```

---

### Trigger Glue Job

```java
public void triggerGlueJob(String table, String start, String end, String source) {
    glueClient.startJobRun(r -> r
        .jobName("migration-job")
        .arguments(Map.of(
            "--TABLE", table,
            "--START_TS", start,
            "--END_TS", end,
            "--SOURCE", source
        ))
    );
}
```

---

## 3. Glue Job (Apache Spark - Java)

### Maven Dependencies

```xml
<dependency>
    <groupId>org.apache.spark</groupId>
    <artifactId>spark-sql_2.12</artifactId>
</dependency>
```

---

## Main Spark Job

```java
SparkSession spark = SparkSession.builder().appName("MigrationJob").getOrCreate();

String source = argsMap.get("SOURCE");
String table = argsMap.get("TABLE");
String startTs = argsMap.get("START_TS");
String endTs = argsMap.get("END_TS");

String query = buildQuery(source, table, startTs, endTs);

Dataset<Row> df = spark.read()
        .format("jdbc")
        .option("url", getJdbcUrl(source))
        .option("dbtable", query)
        .option("user", "user")
        .option("password", "password")
        .option("fetchsize", "10000")
        .load();
```

---

## Query Builder

```java
private String buildQuery(String source, String table, String start, String end) {

    if ("oracle".equals(source)) {
        return String.format(
            "(SELECT * FROM %s WHERE updated_timestamp >= TIMESTAMP '%s' AND updated_timestamp < TIMESTAMP '%s') tmp",
            table, start, end
        );
    }

    return String.format(
        "(SELECT * FROM %s WHERE updated_timestamp < NOW() - INTERVAL '180 days' AND updated_timestamp >= TIMESTAMP '%s' AND updated_timestamp < TIMESTAMP '%s') tmp",
        table, start, end
    );
}
```

---

## Write Avro (Raw)

```java
String rawPath = String.format("s3://bucket/raw/%s/%s/", source, table);

 df.write()
   .format("avro")
   .mode(SaveMode.Overwrite)
   .save(rawPath);
```

---

## Convert to Parquet

```java
String parquetPath = String.format("s3://bucket/curated/%s/%s/", source, table);

 df.write()
   .format("parquet")
   .option("compression", "snappy")
   .mode(SaveMode.Overwrite)
   .save(parquetPath);
```

---

## Update DynamoDB from Glue

```java
DynamoDbClient dynamo = DynamoDbClient.create();

Map<String, AttributeValue> key = Map.of(
    "pk", AttributeValue.builder().s("TABLE#orders").build(),
    "sk", AttributeValue.builder().s("PARTITION#" + startTs).build()
);

Map<String, AttributeValueUpdate> updates = Map.of(
    "status", AttributeValueUpdate.builder()
        .value(AttributeValue.builder().s("SUCCESS").build())
        .action(AttributeAction.PUT)
        .build()
);

dynamo.updateItem(r -> r.tableName("migration_status").key(key).attributeUpdates(updates));
```

---

## 4. S3 Structure

```
s3://bucket/
    raw/<source>/<table>/year=YYYY/month=MM/
    curated/<source>/<table>/year=YYYY/month=MM/
```

---

## 5. Lifecycle Policy (Glacier)

```json
{
  "Rules": [
    {
      "Status": "Enabled",
      "Transitions": [
        {
          "Days": 30,
          "StorageClass": "GLACIER"
        }
      ]
    }
  ]
}
```

---

## 6. Execution Strategy

* Oracle: One-time batch (parallel partitions)
* Aurora: Daily scheduled Lambda
* Parallel Glue Jobs: 5–10 max

---

## 7. Validation

### Oracle

```sql
SELECT COUNT(*) FROM table;
```

### Aurora

```sql
SELECT COUNT(*)
FROM table
WHERE updated_timestamp < NOW() - INTERVAL '180 days';
```

Compare with DynamoDB aggregated counts.

---

## 8. Failure Handling

* FAILED partitions retried
* Idempotent writes (overwrite)
* DynamoDB = source of truth

---

## 9. Key Optimizations

* Partition size: 5–20 GB
* File size: 128–512 MB
* Use pushdown queries
* Avoid small files

---

## Final Flow

```
Lambda → DynamoDB → Glue → S3 (Avro)
                          ↓
                       Parquet
                          ↓
                       Glacier
```

---

## Summary

* Single generic pipeline
* Supports multi-source ingestion
* Fully restartable
* Scales to 10+ TB
* Clean separation of concerns

---

## Next Enhancements (optional)

* Step Functions for orchestration
* Schema registry for Avro
* Data quality checks
* Metrics + CloudWatch dashboards
