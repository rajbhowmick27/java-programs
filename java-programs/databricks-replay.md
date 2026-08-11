# Trade Surveillance Replay Architecture on Databricks

## 1. Purpose

This document defines the proposed replay/backtesting architecture for
moving a trade-surveillance platform to Databricks using:

-   Delta Lake tables
-   Lakeflow Declarative Pipelines (formerly Delta Live Tables / DLT)
-   Medallion architecture
-   Historical surveillance features
-   A dedicated replay engine
-   Point-in-time data and rule reconstruction

The design supports three primary use cases:

1.  **What-if / threshold replay**\
    Example: "If the threshold had been 3 instead of 5 during the last
    90 days, how many alerts would have been generated?"

2.  **New-rule historical backtesting**\
    Example: "I added a new surveillance rule today. Run it over the
    previous 180 days and show the alerts it would have generated."

3.  **Production forensic debugging**\
    Example: "Why was an alert not generated for client C123 on June
    17?"

The design also defines how to retain:

-   Raw source data for 7 years in S3
-   Bronze Delta data for a rolling 1 year
-   Silver Delta data for a rolling 1 year
-   Historical surveillance features for the replay window
-   Sufficient metadata to reproduce historical surveillance decisions

------------------------------------------------------------------------

# 2. Terminology

## 2.1 Delta Lake

Delta Lake is the table/storage layer.

It provides:

-   ACID transactions
-   Table versions
-   Schema enforcement/evolution
-   Time travel
-   Transaction history
-   Integration with Spark

## 2.2 Lakeflow Declarative Pipelines

Databricks formerly called this capability **Delta Live Tables (DLT)**.
Databricks now refers to it as **Lakeflow Declarative Pipelines**.

The important architectural distinction is:

``` text
Lakeflow Declarative Pipelines
        |
        | orchestration / dependency management /
        | incremental pipeline processing / quality
        v
      Spark
        |
        | distributed computation
        v
    Delta Lake
        |
        | persistent tables
        v
      S3
```

Lakeflow pipelines declare datasets and their dependencies; the pipeline
runtime analyzes dependencies and orchestrates execution.

For this document, "DLT" may be used informally where useful because the
existing architecture terminology may still refer to DLT.

------------------------------------------------------------------------

# 3. Core Architectural Principle

The most important design decision is:

> **Do not use DLT itself as the replay engine.**

DLT/Lakeflow should be responsible primarily for continuously building
and maintaining reliable Bronze, Silver, and feature datasets.

The replay capability should be a separate execution layer that reads
historical Delta data and invokes the same surveillance rule engine used
by production.

``` text
                         SOURCE SYSTEMS
                              |
                              v
                         Raw S3 Archive
                         7-year retention
                              |
                              v
                    Lakeflow / DLT Pipeline
                              |
                              v
                       Bronze Delta
                       1-year rolling
                              |
                              v
                    Lakeflow / DLT Pipeline
                              |
                              v
                       Silver Delta
                       1-year rolling
                              |
                 +------------+-------------+
                 |                          |
                 v                          v
          Live Processing             Replay Processing
                 |                          |
                 v                          v
           Feature Builder            Replay Planner
                 |                          |
                 v                          v
           Rule Evaluation             Historical Data
                 |                     + Historical
                 v                     Features
        Production Alerts                    |
                                             v
                                      Same Rule Engine
                                             |
                                             v
                                       Replay Results
```

------------------------------------------------------------------------

# 4. Medallion Architecture

## 4.1 Raw S3

Raw source files are the long-term immutable archive.

Example:

``` text
s3://surveillance-raw/
    trades/
    orders/
    positions/
    open-lots/
    market-data/
    news/
    corporate-actions/
```

Retention:

``` text
7 years
```

Raw S3 is the ultimate recovery/reprocessing source.

It should not normally be read for a 90-day or 180-day replay.

------------------------------------------------------------------------

# 5. Bronze Layer

Bronze contains the ingested representation of source events with
minimal transformation.

Example:

``` text
bronze_trades
bronze_orders
bronze_positions
bronze_market_data
bronze_news
```

Typical fields:

``` text
event_id
source_event_id
client_id
security_id
event_timestamp
event_date
ingestion_timestamp
source_system
source_file
record_hash
schema_version
raw_payload / normalized source columns
```

Important timestamps:

### event_timestamp

When the business event actually occurred.

### ingestion_timestamp

When the event reached the surveillance platform.

### availability_timestamp

When the event became available to the surveillance engine, if different
from ingestion time.

These timestamps are critical for point-in-time replay.

------------------------------------------------------------------------

# 6. Silver Layer

Silver contains canonical, validated, normalized business data.

Examples:

``` text
silver_trades
silver_orders
silver_positions
silver_open_lots
silver_market_data
silver_news
silver_corporate_actions
```

Silver answers:

> "What business events/state existed?"

It should not be confused with surveillance features.

------------------------------------------------------------------------

# 7. Normal Silver Data vs Historical Surveillance Features

This distinction is important.

## 7.1 Normal Silver table: trades

Example:

``` text
silver_trades

trade_id
client_id
security_id
side
quantity
price
notional
trade_timestamp
trade_date
exchange
currency
```

Grain:

``` text
One row = one trade
```

It answers:

> "What trades happened?"

Example:

``` text
T101 | C123 | AAPL | BUY  | 100 | 190 | Jun-10 10:00
T102 | C123 | AAPL | BUY  | 200 | 192 | Jun-11 11:00
T103 | C123 | AAPL | SELL | 150 | 195 | Jun-12 14:00
```

------------------------------------------------------------------------

## 7.2 Normal Silver table: open lots

Open lots are derived business state, not necessarily surveillance
features.

Example:

``` text
silver_open_lots

lot_id
client_id
security_id
source_trade_id
open_quantity
open_price
open_timestamp
valid_from
valid_to
record_version
```

Example:

``` text
Lot L1
valid_from   valid_to    quantity
Jun-10       Jun-12      100
Jun-12       Jun-17       50
Jun-17       Jun-20        0
```

This lets replay answer:

> "What open lots existed on June 17?"

rather than only showing today's open lots.

------------------------------------------------------------------------

## 7.3 Historical surveillance features

These are derived analytics used by surveillance rules.

Example:

``` text
historical_surveillance_features

client_id
security_id
feature_timestamp
feature_date
feature_version
avg_volume_30d
avg_volume_90d
volatility_30d
trade_frequency_30d
volume_percentile
open_notional
exposure_percentile
pre_news_trading_score
...
```

Example:

``` text
client | security | date   | avg_vol_30d | volatility | open_notional
C123   | AAPL     | Jun-10 | 120         | 2.1%       | $19K
C123   | AAPL     | Jun-11 | 125         | 2.2%       | $57K
C123   | AAPL     | Jun-12 | 130         | 2.4%       | $29K
```

This answers:

> "What surveillance metrics were calculated for the client/security at
> that point in time?"

------------------------------------------------------------------------

# 8. Why Historical Features Are Useful

A replay can calculate features directly from Silver, but repeatedly
recalculating expensive features can make replay slow.

For example:

``` text
90-day replay
    |
    +-- 30-day average volume
    +-- 90-day percentile
    +-- volatility
    +-- client exposure
    +-- trading frequency
    +-- abnormal volume
    +-- open-lot calculations
```

If all of these are recomputed every time:

``` text
Historical Silver
      |
      v
Window calculations
      |
      v
Joins
      |
      v
Feature calculations
      |
      v
Rules
```

The same expensive computation may be repeated for every replay.

Instead:

``` text
Historical Silver
      |
      v
Feature Builder
      |
      v
Historical Feature Tables
      |
      v
Replay
      |
      v
Rule Engine
```

This provides much faster replay for rules whose required features
already exist.

------------------------------------------------------------------------

# 9. Important distinction: business state vs feature

Do not classify every derived dataset as a "feature".

For example:

``` text
silver_trades
    -> business events

silver_open_lots
    -> derived business state

silver_positions
    -> business state

historical_surveillance_features
    -> derived surveillance analytics
```

A rule may need all three.

Example:

``` text
Trade
+
Historical Open Lots
+
Historical Features
+
Historical Market Data
+
Historical News
+
Rule Version
```

------------------------------------------------------------------------

# 10. Data Retention Architecture

The target retention model is:

``` text
                     7 YEARS
                 Raw S3 Archive
                       |
                       v
                 1 YEAR ROLLING
                  Bronze Delta
                       |
                       v
                 1 YEAR ROLLING
                   Silver Delta
                       |
                       v
               Historical Features
                 Replay window
```

Recommended starting point:

``` text
Raw S3       = 7 years
Bronze       = 365 days
Silver       = 365 days
Features     = at least maximum normal replay window,
               preferably with a safety buffer
```

If the normal maximum replay is 180 days, retaining 365 days of
replay-ready data provides a useful buffer.

------------------------------------------------------------------------

# 11. VACUUM vs Business Data Retention

This is one of the most important architectural distinctions.

## VACUUM does NOT mean:

``` text
Delete rows older than 7 days
```

Instead, VACUUM removes old physical data files that are no longer
referenced by the table and are older than the configured deleted-file
retention threshold.

The default VACUUM deleted-file retention is 7 days.

Therefore:

``` text
VACUUM retention = 7 days
```

does NOT mean:

``` text
Table contains only 7 days of data
```

If the table is append-only and still logically contains one year of
rows, those rows remain available in the current table.

------------------------------------------------------------------------

# 12. Implementing a Rolling 1-Year Retention

Business requirement:

> Bronze and Silver must contain only a rolling 365-day window.

This is a row/data retention requirement.

Conceptually:

``` sql
DELETE FROM bronze_trades
WHERE event_date < current_date() - INTERVAL 365 DAYS;
```

and:

``` sql
DELETE FROM silver_trades
WHERE event_date < current_date() - INTERVAL 365 DAYS;
```

The same principle applies to other retained datasets.

After rows are logically removed, physical cleanup is performed
separately using the appropriate Delta maintenance mechanisms, including
VACUUM and, where required by table features such as deletion vectors,
purge/rewrite operations.

------------------------------------------------------------------------

# 13. Why Not Set VACUUM to 365 Days?

Do not use:

``` sql
VACUUM bronze_trades RETAIN 365 DAYS;
```

as the mechanism for "keeping one year of data."

That means:

> "Keep obsolete physical files available for approximately one year for
> historical table versions."

It does not mean:

> "Keep exactly one year of business data."

Increasing VACUUM retention also increases storage cost.

Therefore:

``` text
Business retention
    =
DELETE / governed row-retention policy

Time-travel retention
    =
Delta file + transaction-log retention

Physical cleanup
    =
VACUUM / purge mechanisms
```

These are separate controls.

------------------------------------------------------------------------

# 14. Rolling Retention Example

Assume today is:

``` text
2026-08-11
```

The Bronze table contains:

``` text
2025-08-11 -> 2026-08-11
```

The retention boundary is:

``` text
2025-08-11
```

On the next day:

``` text
2026-08-12
```

the desired window becomes:

``` text
2025-08-12 -> 2026-08-12
```

The retention process removes data before the new lower boundary.

This is a rolling window.

------------------------------------------------------------------------

# 15. Recommended Retention Workflow

Do not run retention blindly while a DLT/Lakeflow update is modifying
the same table.

Recommended sequence:

``` text
Daily Schedule
      |
      v
Lakeflow ingestion/transformation
      |
      v
Pipeline success / data-quality checks
      |
      v
Retention validation
      |
      v
Delete data older than retention boundary
      |
      v
Purge/rewrite where necessary
      |
      v
VACUUM
      |
      v
Retention metrics + audit
```

Monitor:

``` text
oldest_event_date
newest_event_date
rows_deleted
files_removed
table_size
retention_job_status
replay_availability
```

------------------------------------------------------------------------

# 16. Why Raw S3 Is Still 7 Years

The raw S3 layer is the deep historical recovery layer.

Example:

``` text
Raw S3
2020
2021
2022
2023
2024
2025
2026
```

Bronze/Silver:

``` text
2025-08-11 -> 2026-08-11
```

If someone asks for a replay from 2 years ago:

``` text
Replay request
      |
      v
Silver availability check
      |
      v
Historical data unavailable
      |
      v
Recovery/rebuild pipeline
      |
      v
Raw S3
      |
      v
Temporary historical Bronze/Silver
      |
      v
Replay
```

Thus:

> S3 is the 7-year archive; Bronze/Silver are the fast replay window.

------------------------------------------------------------------------

# 17. Do Not Use Delta Time Travel as the Primary 90/180-Day Replay Archive

Delta time travel is useful for:

-   short-term debugging
-   historical table state
-   rollback
-   investigating table changes
-   reproducing recent table states

But it should not be treated as the long-term regulatory archive.

Databricks documents that table history/time travel has separate log and
data-file retention controls and recommends not using table history as a
long-term backup/archive solution.

Therefore:

``` text
90/180-day replay
    |
    v
Replay-ready historical data
    |
    v
not dependent on old Delta table versions
```

Use Delta time travel as a supporting capability, not as the primary
regulatory replay store.

------------------------------------------------------------------------

# 18. Point-in-Time Correctness

This is one of the most important requirements for trade surveillance.

A replay must reproduce what the surveillance system could have known at
the time.

Consider:

``` text
Trade occurred:
June 17 10:00

News published:
June 17 10:05

News received by surveillance:
June 17 10:07
```

If the trade was evaluated at 10:00, the surveillance engine should not
automatically use the news that only became available at 10:07.

Therefore retain timestamps such as:

``` text
event_time
source_event_time
ingestion_time
availability_time
effective_time
```

where appropriate.

------------------------------------------------------------------------

# 19. Current State vs Historical State

Current state is not sufficient for replay.

Example:

``` text
June 17:
Open lot = 100 shares

June 20:
Lot closed

Today:
Lot does not exist
```

If the database only stores current open lots, replay cannot reconstruct
the June 17 state.

Therefore historical business-state tables such as open lots and
positions should retain temporal information.

Example:

``` text
lot_id
client_id
security_id
open_quantity
open_price
valid_from
valid_to
record_version
```

This enables:

``` text
"What were the open lots at June 17 10:00?"
```

------------------------------------------------------------------------

# 20. Rule Versioning

Historical data alone is not enough.

Suppose production used:

``` text
R001 version 17
volume_multiplier = 5
lookback_days = 30
news_window = 3 days
minimum_trade_value = $1M
```

Today the rule is changed to:

``` text
R001 version 18
volume_multiplier = 3
```

If you replay June 17 using today's configuration, you are not
reproducing production.

Therefore every production alert should record:

``` text
alert_id
rule_id
rule_version
rule_execution_id
```

And the rule repository should retain immutable versions.

Example:

``` text
rule_id | version | effective_from | effective_to | threshold
R001    | 17      | Apr-01         | Jun-30       | 5
R001    | 18      | Jul-01         | current      | 3
```

------------------------------------------------------------------------

# 21. Version the Complete Rule Configuration

Do not store only one threshold.

Example:

``` json
{
  "ruleId": "R001",
  "version": 17,
  "volumeMultiplier": 5,
  "lookbackDays": 30,
  "newsWindowBeforeDays": 3,
  "newsWindowAfterDays": 1,
  "minimumTradeValue": 1000000
}
```

A replay can then use:

``` text
Historical data
+
Historical feature version
+
Historical rule configuration
```

------------------------------------------------------------------------

# 22. The Same Rule Engine Must Serve Live and Replay

Avoid:

``` text
Production Rule Engine
Replay Rule Engine
```

because they can diverge.

Instead:

``` text
                     Rule Engine
                          |
                +---------+---------+
                |                   |
                v                   v
           Production             Replay
                |                   |
            Live data          Historical data
                |                   |
                v                   v
             Alerts             Replay alerts
```

The rule engine should be deterministic for the same
inputs/configuration.

Only the input context changes.

------------------------------------------------------------------------

# 23. Replay Manifest

Every replay should generate a Replay Manifest.

Example:

``` json
{
  "replayId": "REP-20260811-001",

  "period": {
    "from": "2026-05-13",
    "to": "2026-08-11"
  },

  "datasets": {
    "trades": "historical snapshot",
    "openLots": "historical snapshot",
    "marketData": "historical snapshot",
    "news": "historical snapshot",
    "positions": "historical snapshot"
  },

  "rules": {
    "R001": "v17"
  },

  "overrides": {
    "R001.volumeMultiplier": 3
  },

  "featureVersion": "v12",

  "codeVersion": "git-abc123"
}
```

The manifest makes the replay reproducible.

------------------------------------------------------------------------

# 24. Replay Data Model

Recommended tables:

## replay_execution

``` text
replay_id
requested_by
request_time
start_date
end_date
mode
status
created_at
started_at
completed_at
```

## replay_rule

``` text
replay_id
rule_id
rule_version
parameter_override
```

## replay_dataset_snapshot

``` text
replay_id
dataset
snapshot_timestamp
snapshot_reference
data_version
```

## replay_alert

``` text
replay_id
replay_alert_id
client_id
security_id
rule_id
rule_version
alert_time
severity
```

## rule_evaluation

``` text
evaluation_id
replay_id
client_id
trade_id
rule_id
rule_version
result
evaluation_time
```

## rule_evaluation_detail

``` text
evaluation_id
condition_id
condition_name
actual_value
threshold
result
```

------------------------------------------------------------------------

# 25. Decision Trace

For forensic investigation, storing only "NO ALERT" is insufficient.

Example:

``` text
Client C123
Trade T99881
Rule R001 v17

Condition 1:
trade_volume > 5 * avg_volume
actual = 4.8
threshold = 5
result = FALSE

Condition 2:
trade within news window
result = TRUE

Condition 3:
exposure > $1M
actual = $750K
threshold = $1M
result = FALSE

Final result:
NO ALERT
```

This provides an explainable decision trace.

It allows an investigator to distinguish:

``` text
No alert because rule condition failed
```

from:

``` text
No alert because data was missing
```

from:

``` text
No alert because pipeline failed
```

from:

``` text
No alert because rule was not active
```

------------------------------------------------------------------------

# 26. Replay Request Types

There are three major execution patterns.

## 26.1 Forensic Replay

Example:

> Why was an alert not generated for C123 on June 17?

Characteristics:

``` text
1 client
1/few securities
1 day
few rules
small temporal window
```

Execution:

``` text
Replay request
      |
      v
Replay Planner
      |
      v
Prune by client/security/date/rule
      |
      v
Small Spark/serverless execution
      |
      v
Decision trace
```

Objective:

> Fast diagnosis.

Expected scale:

``` text
seconds -> minutes
```

depending on compute startup and rule complexity.

------------------------------------------------------------------------

# 27. Targeted 90-Day Backtest

Example:

> Run R017 for all clients for the last 90 days.

Characteristics:

``` text
90 days
1 or small number of rules
all clients
large dataset
```

Execution:

``` text
Replay request
      |
      v
Replay Planner
      |
      v
Determine rule dependencies
      |
      v
Read required Silver/features
      |
      v
Spark distributed processing
      |
      v
Same rule engine
      |
      v
Replay alert table
```

Typical execution scale:

``` text
minutes -> tens of minutes
```

depending on data volume, joins, window functions, feature requirements
and compute size.

------------------------------------------------------------------------

# 28. Full 180-Day Historical Replay

Example:

> Run all surveillance rules for all clients over the last 180 days.

Characteristics:

``` text
180 days
all rules
all clients
multiple datasets
complex joins
window calculations
```

Execution:

``` text
Replay Planner
      |
      +-- Trades
      +-- Orders
      +-- Open Lots
      +-- Positions
      +-- Market Data
      +-- News
      +-- Corporate Actions
      |
      v
Distributed Spark execution
      |
      v
Rule evaluation
      |
      v
Replay results
```

Expected scale:

``` text
tens of minutes -> hours
```

depending on data volume and rule complexity.

The actual performance must be benchmarked using production-like data.

------------------------------------------------------------------------

# 29. Replay Planner

The Replay Planner is the key component that decides how much data needs
to be read.

Input:

``` text
start_date
end_date
rules
parameter overrides
client/security filters
```

It determines:

``` text
required datasets
required time windows
required features
rule versions
data versions
```

Conceptually:

``` text
Replay Request
      |
      v
Replay Planner
      |
      +----------------+
      |                |
      v                v
Rule dependencies   Time windows
      |                |
      +-------+--------+
              |
              v
        Data access plan
```

------------------------------------------------------------------------

# 30. Rule Dependency Metadata

Each rule should declare its dependencies.

Example:

``` text
R017

Inputs:
- trades
- market_data
- news
- open_lots

Features:
- avg_volume_30d
- volatility_30d
- exposure

Temporal dependency:
- trade window = requested range
- news window = requested range +/- 3 days
- market data = requested range
- feature lookback = requested range + 30 days
```

This allows the Replay Planner to avoid reading unnecessary data.

------------------------------------------------------------------------

# 31. Temporal Window Expansion

Suppose the replay request is:

``` text
May 13 -> Aug 11
```

and a rule requires:

``` text
30-day historical average
```

Then the trade data required for feature computation may actually be:

``` text
Apr 13 -> Aug 11
```

If the rule uses news within +/- 3 days:

``` text
May 10 -> Aug 14
```

The Replay Planner should calculate these automatically.

Example:

``` text
User requested:
May 13 -> Aug 11

Rule:
30-day feature lookback
+/- 3-day news window

Required:

Trades:
Apr 13 -> Aug 11

News:
May 10 -> Aug 14

Market data:
Apr 13 -> Aug 11
```

This is a major performance optimization.

------------------------------------------------------------------------

# 32. Avoid Rebuilding Silver for Every Replay

Do NOT use this design:

``` text
Replay request
    |
    v
Read raw S3
    |
    v
Run DLT transformations
    |
    v
Build Silver
    |
    v
Build features
    |
    v
Run rules
```

That makes every replay an ETL rebuild.

Instead:

``` text
Continuous DLT pipeline
        |
        v
Replay-ready Silver
        |
        v
Replay Planner
        |
        v
Read required historical data
        |
        v
Build only missing/new features
        |
        v
Same rule engine
```

Raw S3 should be the fallback when the required historical Silver data
is outside the normal replay retention window.

------------------------------------------------------------------------

# 33. Three Replay Data Paths

The replay engine should support three paths.

``` text
                    Replay Planner
                         |
          +--------------+--------------+
          |              |              |
          v              v              v
 Existing Silver    Existing Features  New Feature/
                                      Transformation
          |              |              |
          +--------------+--------------+
                         |
                         v
                  Replay Compute
                         |
                         v
                    Rule Engine
```

### Path 1: Existing Silver + existing features

Fastest.

### Path 2: Existing Silver + recompute only required features

Used when a new feature/rule is introduced.

### Path 3: Raw S3 recovery

Used when the requested date is outside the retained Silver window.

------------------------------------------------------------------------

# 34. Example: New Rule Added Today

Suppose R099 is introduced today:

``` text
R099 = Trading before material news
```

User requests:

``` text
Run R099 over last 180 days
```

If Silver and historical business state exist:

``` text
Historical Silver
      |
      +-- Trades
      +-- News
      +-- Market data
      +-- Open lots
      |
      v
R099-specific feature computation
      |
      v
R099
      |
      v
Replay alerts
```

No Bronze rebuild is necessary.

------------------------------------------------------------------------

# 35. Example: Threshold What-If

Production:

``` text
volume_multiplier = 5
```

User asks:

``` text
What if volume_multiplier = 3?
```

Replay:

``` text
Historical Silver
        |
Historical features
        |
R001 v17
        |
override:
volume_multiplier = 3
        |
Rule Engine
        |
Replay alerts
```

Example result:

``` text
Original production alerts: 1,245
Replay alerts:               1,873
Additional alerts:             628
```

The replay results remain separate from production alerts.

------------------------------------------------------------------------

# 36. Example: Production Forensic Debugging

User:

> Why wasn't C123 alerted on June 17?

Replay:

``` text
Client = C123
Trade date = June 17
Rules = production-active rules
Rule versions = production versions
```

Replay planner performs targeted pruning:

``` text
client_id = C123
security_id = relevant securities
event window = required rule window
```

Then:

``` text
Rule Evaluation
      |
      +-- condition 1 = PASS
      +-- condition 2 = PASS
      +-- condition 3 = FAIL
      |
      v
NO ALERT
```

The investigator sees the exact reason.

------------------------------------------------------------------------

# 37. Optimize Silver for Replay

The primary replay access pattern is typically:

``` sql
WHERE event_date BETWEEN start_date AND end_date
```

and often:

``` sql
AND client_id = ...
AND security_id = ...
```

Therefore Silver physical layout must support these filters.

For modern Databricks Delta tables, evaluate liquid clustering based on
actual access patterns.

Possible clustering dimensions:

``` text
event_date
client_id
security_id
```

Do not automatically cluster on every column.

The right columns depend on actual replay and production query patterns.

------------------------------------------------------------------------

# 38. Data Skipping and File Pruning

The objective is:

``` text
Total Silver data:
25 TB

Relevant replay data:
2 TB
```

The physical layout should allow Spark/Databricks to avoid reading
irrelevant files.

Conceptually:

``` text
25 TB Silver
      |
      v
Date/file pruning
      |
      v
2 TB relevant
      |
      v
Client/security pruning
      |
      v
200 GB
      |
      v
Rule evaluation
```

The actual reduction depends on table layout, statistics, predicates and
data distribution.

------------------------------------------------------------------------

# 39. Example of Dataset Pruning

Suppose:

``` text
25 TB total historical data
```

A replay requests:

``` text
90 days
Rule R017
```

The Replay Planner determines:

``` text
Trades:
90 days + 30-day lookback

News:
90 days + 3 days on each side

Market data:
90 days + 30-day lookback

Open lots:
relevant security/client/date range
```

It should not automatically scan all 25 TB.

------------------------------------------------------------------------

# 40. Feature Reuse Strategy

Maintain historical features where they are expensive to calculate
repeatedly.

Examples:

``` text
30-day average volume
90-day average volume
volatility
trade frequency
volume percentile
client exposure
abnormal trading score
pre-news trading score
```

Store:

``` text
client_id
security_id
feature_timestamp
feature_version
feature_value
```

During replay:

``` text
Feature exists?
      |
     YES
      |
      v
Reuse historical feature
```

If:

``` text
Feature does not exist
```

then:

``` text
Compute feature from historical Silver
```

------------------------------------------------------------------------

# 41. Feature Versioning

Historical features should be versioned.

Example:

``` text
feature_version = v12
```

Suppose the definition of:

``` text
abnormal_volume_score
```

changes.

Then:

``` text
v12 = old formula
v13 = new formula
```

A replay can explicitly choose:

``` text
feature_version = v12
```

or:

``` text
feature_version = v13
```

This prevents historical results from changing silently.

------------------------------------------------------------------------

# 42. Replay Reproducibility

A replay should be reproducible from:

``` text
Historical data
+
Historical business state
+
Historical features
+
Rule version
+
Feature version
+
Parameter overrides
+
Code version
```

Conceptually:

``` text
Reproducible Result
=
Data Snapshot
+
Feature Snapshot
+
Rule Snapshot
+
Code Snapshot
+
Execution Parameters
```

------------------------------------------------------------------------

# 43. Replay Result Isolation

Never write replay alerts directly into production alert tables.

Use:

``` text
replay_alerts
```

or:

``` text
replay_results
```

Example:

``` text
replay_id
replay_alert_id
client_id
security_id
rule_id
rule_version
alert_timestamp
severity
```

Production remains:

``` text
production_alerts
```

This prevents a backtest from accidentally becoming a real surveillance
alert.

------------------------------------------------------------------------

# 44. Replay Modes

Recommended modes:

``` text
FORENSIC
BACKTEST
WHAT_IF
FULL_REPLAY
```

### FORENSIC

Small, targeted, explainable.

### BACKTEST

Run a new rule against history.

### WHAT_IF

Override parameters and compare with production.

### FULL_REPLAY

Re-run a large set of rules over a broad historical window.

------------------------------------------------------------------------

# 45. Compare Replay vs Production

For a what-if replay, produce a comparison:

``` text
Production:
1,245 alerts

Replay:
1,873 alerts

New alerts:
628

Removed alerts:
0

Changed severity:
42
```

You can then identify:

``` text
Production Alert
Replay Alert
New Replay Alert
Missing Replay Alert
```

This is useful for rule tuning and validation.

------------------------------------------------------------------------

# 46. Production Debugging Should Be Different from Full Replay

Do not launch a 180-day Spark replay for:

> "Why didn't C123 generate an alert yesterday?"

Instead:

``` text
User
 |
 v
Forensic Replay API
 |
 v
Replay Planner
 |
 +-- client
 +-- security
 +-- date/time
 +-- affected rules
 |
 v
Small targeted compute
 |
 v
Decision trace
```

This keeps investigator queries cheap and fast.

------------------------------------------------------------------------

# 47. Recommended Logical Architecture

``` text
                              RAW DATA
                                |
                                v
                     +----------------------+
                     |      Raw S3          |
                     |      7 years         |
                     +----------+-----------+
                                |
                         Lakeflow/DLT
                                |
                                v
                     +----------------------+
                     |      Bronze          |
                     |    Rolling 1 year    |
                     +----------+-----------+
                                |
                         Lakeflow/DLT
                                |
                                v
                     +----------------------+
                     |       Silver         |
                     |    Rolling 1 year    |
                     |                      |
                     | Trades                |
                     | Orders                |
                     | Positions             |
                     | Open Lots             |
                     | Market Data           |
                     | News                  |
                     +----------+-----------+
                                |
                 +--------------+---------------+
                 |                              |
                 v                              v
        Historical Business State       Historical Features
                 |                              |
                 +--------------+---------------+
                                |
                                v
                         Replay Planner
                                |
              +-----------------+------------------+
              |                 |                  |
              v                 v                  v
          Forensic           90-day             180-day
           Replay           Backtest            Replay
              |                 |                  |
              +-----------------+------------------+
                                |
                                v
                          Same Rule Engine
                                |
                    +-----------+-----------+
                    |                       |
                    v                       v
             Replay Results          Decision Trace
```

------------------------------------------------------------------------

# 48. Recommended Component Responsibilities

## Lakeflow/DLT

Responsible for:

-   ingestion
-   Bronze processing
-   Silver transformations
-   data quality
-   incremental processing
-   pipeline dependency management
-   historical feature generation where appropriate

Not responsible for:

-   investigator replay API
-   replay orchestration policy
-   what-if parameter management
-   production/replay result comparison

------------------------------------------------------------------------

## Delta Lake

Responsible for:

-   persistent table storage
-   ACID transactions
-   table versions
-   time travel
-   schema management
-   efficient historical reads

Not responsible for:

-   deciding which rules to replay
-   replay scheduling
-   investigation workflows

------------------------------------------------------------------------

## Replay Controller

Responsible for:

-   accepting replay requests
-   authorization
-   validating date ranges
-   selecting replay mode
-   generating replay ID
-   submitting replay execution

------------------------------------------------------------------------

## Replay Planner

Responsible for:

-   determining required datasets
-   calculating temporal lookback
-   selecting rule versions
-   selecting feature versions
-   determining data pruning
-   choosing execution strategy
-   estimating workload

------------------------------------------------------------------------

## Replay Compute

Responsible for:

-   reading historical Delta tables
-   computing missing features
-   joining required datasets
-   invoking rule engine
-   writing replay results

------------------------------------------------------------------------

## Rule Engine

Responsible for:

-   deterministic rule evaluation
-   condition evaluation
-   parameter handling
-   decision trace generation

The same implementation should be used for live and replay.

------------------------------------------------------------------------

# 49. Data Retention Decision

Recommended:

``` text
Raw S3:
7 years

Bronze:
365-day rolling window

Silver:
365-day rolling window

Historical surveillance features:
365 days initially, or at least the maximum normal replay window plus buffer

Delta time travel:
short-term operational/debugging history, not the regulatory archive
```

If the maximum normal replay requirement is 180 days, keeping 365 days
in Silver provides:

``` text
0-180 days:
normal replay

180-365 days:
extended investigation/replay

>365 days:
rebuild from 7-year raw S3
```

------------------------------------------------------------------------

# 50. Why 365 Days in Silver Is Better Than Exactly 180 Days

If the business requirement is:

``` text
maximum replay = 180 days
```

retaining exactly 180 days creates operational risk.

For example:

``` text
Replay requested:
180 days

Feature lookback:
30 days

News window:
3 days
```

The actual source data required may extend beyond the requested replay
period.

Therefore:

``` text
Silver retention = 365 days
```

provides a safe buffer.

------------------------------------------------------------------------

# 51. Important Replay Boundary Example

User requests:

``` text
May 13 -> Aug 11
```

Rule requires:

``` text
30-day feature lookback
+/- 3-day news window
```

Replay Planner should calculate:

``` text
Trades:
Apr 13 -> Aug 11

Market:
Apr 13 -> Aug 11

News:
May 10 -> Aug 14
```

Therefore the replay does not simply query the user's requested date
range for every dataset.

------------------------------------------------------------------------

# 52. If Historical Silver Is Missing

If the request is:

``` text
2 years ago
```

but Silver retains only one year:

``` text
Silver:
last 365 days

Raw S3:
last 7 years
```

then:

``` text
Replay Planner
      |
      v
Historical Silver unavailable
      |
      v
Recovery Pipeline
      |
      v
Raw S3
      |
      v
Temporary historical canonical data
      |
      v
Replay
```

This should be an exceptional path.

------------------------------------------------------------------------

# 53. Do Not Copy All Historical Data for Replay

Avoid:

``` text
S3
 |
 v
Copy 10 TB
 |
 v
Replay cluster
```

Instead:

``` text
Replay
 |
 v
Spark reads required Delta files directly
 |
 v
Filter/prune
 |
 v
Compute
```

The goal is to keep data in place and minimize data movement.

------------------------------------------------------------------------

# 54. Performance Strategy

Replay performance is primarily improved by:

1.  Keeping replay-ready Silver data available.
2.  Avoiding Bronze reconstruction.
3.  Data skipping/file pruning.
4.  Appropriate liquid clustering.
5.  Predicate pushdown.
6.  Rule dependency metadata.
7.  Temporal window calculation.
8.  Reusing historical features.
9.  Running only required rules.
10. Separating forensic replay from large backtests.
11. Using distributed Spark compute for large replays.
12. Avoiding unnecessary materialization/copying.

------------------------------------------------------------------------

# 55. Expected Execution Behavior

These are architecture-level estimates, not guarantees.

## Forensic replay

``` text
Scope:
1 client
1/few securities
1 day
few rules

Expected:
seconds -> minutes
```

## 90-day targeted backtest

``` text
Scope:
90 days
1/few rules
all clients

Expected:
minutes -> tens of minutes
```

## 180-day full replay

``` text
Scope:
180 days
many/all rules
all clients
multiple datasets

Expected:
tens of minutes -> hours
```

Actual performance must be benchmarked using:

-   daily data volume
-   number of files
-   average file size
-   joins
-   window functions
-   rule count
-   client count
-   security count
-   feature complexity
-   cluster/serverless configuration

------------------------------------------------------------------------

# 56. Recommended Replay Execution Strategy

Use a single Replay Framework with multiple execution strategies.

``` text
                    Replay Framework
                           |
            +--------------+--------------+
            |              |              |
            v              v              v
        Forensic       Targeted         Full
         Engine         Engine          Engine
            |              |              |
        small scope    medium scope    large scope
        low compute    Spark compute   large Spark
```

This avoids building three completely separate replay systems.

------------------------------------------------------------------------

# 57. Recommended End-to-End Example

User asks:

> "Run R001 from May 13 to Aug 11 with threshold 3 instead of 5."

### Step 1

Replay API creates:

``` text
REP-20260811-001
```

### Step 2

Replay Planner reads rule metadata:

``` text
R001 v17
```

Dependencies:

``` text
trades
market_data
news
open_lots
avg_volume_30d
```

### Step 3

Planner calculates required ranges:

``` text
Trades:
Apr 13 -> Aug 11

Market:
Apr 13 -> Aug 11

News:
May 10 -> Aug 14

Open lots:
Apr 13 -> Aug 11
```

### Step 4

Planner checks historical availability.

``` text
Silver contains:
Aug 2025 -> Aug 2026

Required:
Apr 2026 -> Aug 2026

Available = YES
```

### Step 5

Replay reads historical Silver/features.

### Step 6

Override:

``` text
volume_multiplier = 3
```

### Step 7

Same rule engine evaluates.

### Step 8

Results written to:

``` text
replay_alerts
```

### Step 9

Comparison:

``` text
Production alerts = 1,245
Replay alerts     = 1,873
Additional        =   628
```

------------------------------------------------------------------------

# 58. Production Debug Example

User asks:

> "Why didn't C123 get an alert on June 17?"

Replay request:

``` text
mode = FORENSIC
client = C123
date = June 17
```

Planner:

``` text
Identify candidate rules
Identify required datasets
Identify required temporal windows
```

Execution:

``` text
Historical trade
+
Historical open lots
+
Historical market data
+
Historical news
+
Historical features
+
Production rule version
```

Decision trace:

``` text
R001 v17

Volume:
4.8x
Threshold:
5x
Result:
FAIL

News condition:
PASS

Exposure:
$750K
Required:
$1M
Result:
FAIL

Final:
NO ALERT
```

This gives the investigator an actionable explanation.

------------------------------------------------------------------------

# 59. Architectural Decisions Summary

  -----------------------------------------------------------------------
  Decision                            Recommendation
  ----------------------------------- -----------------------------------
  Raw retention                       7 years in S3

  Bronze retention                    Rolling 365 days

  Silver retention                    Rolling 365 days

  VACUUM purpose                      Physical cleanup, not business
                                      retention

  Delta time travel                   Short-term state
                                      reconstruction/debugging

  Long-term replay archive            Raw S3 + replay-ready canonical
                                      data

  Normal Silver                       Canonical business events/state

  Open lots                           Historical business state

  Historical features                 Derived surveillance analytics

  Replay engine                       Separate from DLT

  Rule engine                         Same implementation for live/replay

  Rule configuration                  Immutable versioned

  Feature definitions                 Versioned

  Replay output                       Separate from production alerts

  Replay metadata                     Replay Manifest

  Forensic replay                     Highly targeted

  90-day backtest                     Distributed targeted Spark
                                      execution

  180-day replay                      Large distributed Spark execution

  Historical data unavailable         Rebuild from raw S3

  Replay optimization                 Pruning + feature reuse + temporal
                                      planning

  Silver layout                       Optimize for actual replay
                                      predicates; evaluate liquid
                                      clustering

  Data correctness                    Point-in-time/availability-aware
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 60. Final Recommended Architecture

``` text
                                  7-YEAR ARCHIVE
                                +----------------+
                                |   RAW S3       |
                                | Immutable      |
                                +-------+--------+
                                        |
                                        v
                             +----------------------+
                             | Lakeflow / DLT       |
                             | Ingestion Pipeline   |
                             +----------+-----------+
                                        |
                                        v
                             +----------------------+
                             | Bronze Delta         |
                             | Rolling 365 days     |
                             +----------+-----------+
                                        |
                                        v
                             +----------------------+
                             | Lakeflow / DLT       |
                             | Transformation       |
                             +----------+-----------+
                                        |
                                        v
                             +----------------------+
                             | Silver Delta         |
                             | Rolling 365 days     |
                             |                      |
                             | Trades               |
                             | Orders               |
                             | Positions            |
                             | Open Lots            |
                             | Market Data          |
                             | News                 |
                             +----------+-----------+
                                        |
                    +-------------------+-------------------+
                    |                                       |
                    v                                       v
        +-------------------------+             +-------------------------+
        | Historical Business     |             | Historical Surveillance |
        | State                   |             | Features                |
        |                         |             |                         |
        | Open lots               |             | Avg volume              |
        | Positions               |             | Volatility              |
        | Other derived state     |             | Exposure                |
        +------------+------------+             | Abnormal trading       |
                     |                          +------------+------------+
                     |                                       |
                     +-------------------+-------------------+
                                         |
                                         v
                              +-----------------------+
                              |    Replay Planner     |
                              |                       |
                              | Rule dependencies     |
                              | Date ranges           |
                              | Lookback windows      |
                              | Data availability     |
                              | Feature availability  |
                              | Rule versions         |
                              +-----------+-----------+
                                          |
                       +------------------+------------------+
                       |                  |                  |
                       v                  v                  v
                 FORENSIC             TARGETED             FULL
                  REPLAY              BACKTEST            REPLAY
                       |                  |                  |
                       +------------------+------------------+
                                          |
                                          v
                                +--------------------+
                                | Same Rule Engine   |
                                | as Production      |
                                +---------+----------+
                                          |
                         +----------------+----------------+
                         |                                 |
                         v                                 v
                 Replay Alerts                      Decision Trace
                         |
                         v
                  Replay Results
```

------------------------------------------------------------------------

# 61. Final Architectural Principle

The overall design should follow this hierarchy:

``` text
Raw S3
    |
    | 7-year immutable archive
    v
Bronze
    |
    | 1-year rolling ingestion history
    v
Silver
    |
    | 1-year replay-ready canonical history
    v
Historical Business State
    |
    | point-in-time state
    v
Historical Features
    |
    | versioned surveillance metrics
    v
Replay Planner
    |
    | determines exact data/rules/features
    v
Same Rule Engine
    |
    +--> Production
    |
    +--> Replay
```

The central idea is:

> **Do not rebuild the data pipeline for every replay. Continuously
> maintain a replay-ready historical data layer, keep the raw 7-year S3
> archive as the deep recovery path, version
> rules/features/configuration, and use a Replay Planner to read only
> the historical data actually required by the requested rules and time
> window.**

This architecture supports fast investigator debugging, controlled
90/180-day backtesting, what-if threshold analysis, reproducible
production investigations, and large-scale historical replays without
turning every replay into a full ETL job.

------------------------------------------------------------------------

# 62. Databricks Documentation References

The design uses the following current Databricks concepts/documentation:

-   **What happened to Delta Live Tables (DLT)?** --- Databricks
    documentation on the transition to Lakeflow pipelines.
-   **What are Lakeflow pipelines?** --- declarative pipeline model,
    dependency analysis and orchestration.
-   **Remove unused data files with VACUUM** --- deleted-file retention,
    physical cleanup and time-travel implications.
-   **Work with table history** --- Delta versions, time travel and
    history retention.
-   **CLUSTER BY clause / Liquid clustering** --- physical data layout
    optimization for Delta tables.
-   **Purge metadata-only deletes** --- relevant when deletion vectors
    are used and physical purging is required.

Important current defaults to verify against the Databricks
Runtime/workspace configuration before implementation:

-   VACUUM deleted-file retention defaults to 7 days.
-   Delta table history/log retention is a separate setting from
    deleted-file retention.
-   Databricks does not recommend table history as a long-term
    backup/archive mechanism.
-   Current Databricks terminology is Lakeflow Declarative Pipelines,
    although DLT remains common legacy terminology.
