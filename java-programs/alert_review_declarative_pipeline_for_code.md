# PIT Declarative Review Compiler --- Problem Statement and Recommended Architecture

## 1. Executive summary

The objective is to build a reusable platform for generating Potential
Insider Trading (PIT) review/alert pipelines from declarative review
definitions rather than hand-written PySpark for every review.

A PIT review can involve:

-   Silver-layer source datasets
-   Multiple joins
-   Filters and derived columns
-   Aggregations
-   Time/lookback logic
-   Window functions
-   Ranking
-   Threshold evaluation
-   Alert/evidence generation

The desired flow is:

``` text
Declarative PIT Review
        |
        v
Parser + Validation
        |
        v
Surveillance Review IR
        |
        v
IR Validation / Optimization
        |
        v
Deterministic PySpark Generator
        |
        v
Spark Declarative Pipeline
        |
        v
Spark Catalyst / AQE
        |
        v
PIT Alerts
```

The key architectural principle is:

> **The custom compiler owns surveillance semantics; Spark owns
> execution optimization.**

This avoids rebuilding Spark's execution optimizer while still allowing
every new review to be defined declaratively and generated consistently.

------------------------------------------------------------------------

## 2. Problem statement

Today, each PIT review could be implemented as a separate PySpark job.
That creates repeated implementation patterns and increases:

-   development effort
-   defect risk
-   inconsistent implementation
-   maintenance effort
-   testing effort
-   audit complexity
-   difficulty of versioning review logic independently from execution
    code

The desired platform should allow a new review to be expressed as a
versioned declarative specification such as:

``` yaml
review:
  id: PIT_001
  name: Employee Trading Before Material Event
  version: 1.0

sources:
  trades: silver.trades
  employees: silver.employee_accounts
  events: silver.material_events

joins:
  - left: trades.account_id
    right: employees.account_id
    type: inner

  - left: trades.instrument_id
    right: events.instrument_id
    type: inner

filters:
  - employees.employee_flag = true
  - trades.trade_timestamp >= events.event_timestamp - 2 days
  - trades.trade_timestamp < events.event_timestamp

aggregations:
  group_by:
    - account_id
    - instrument_id
    - event_id

  metrics:
    total_notional:
      expression: sum(trades.quantity * trades.price)

ranking:
  function: row_number
  partition_by:
    - event_id
  order_by:
    - total_notional desc

thresholds:
  - metric: total_notional
    operator: ">="
    value: 1000000

  - metric: rank
    operator: "<="
    value: 10

output:
  type: pit_alert
```

The platform should validate this definition and deterministically
generate production-quality PySpark and SDP artifacts.

------------------------------------------------------------------------

# 3. Recommended architecture

``` text
                         +--------------------------+
                         | PIT Review Definition    |
                         | YAML / JSON / UI         |
                         +------------+-------------+
                                      |
                                      v
                         +--------------------------+
                         | Parser + Schema          |
                         | Validation               |
                         +------------+-------------+
                                      |
                                      v
                         +--------------------------+
                         | Semantic Validation      |
                         | Schema / Type / Rules    |
                         +------------+-------------+
                                      |
                                      v
                         +--------------------------+
                         | Surveillance Review IR   |
                         +------------+-------------+
                                      |
                         +------------+-------------+
                         |                          |
                         v                          v
               +------------------+       +------------------+
               | IR Validation    |       | Common Subplan   |
               | & Simplification |       | Detection        |
               +--------+---------+       +--------+---------+
                        |                          |
                        +------------+-------------+
                                     |
                                     v
                         +--------------------------+
                         | Deterministic PySpark    |
                         | Code Generator           |
                         +------------+-------------+
                                      |
                         +------------+-------------+
                         |                          |
                         v                          v
                Generated PySpark          Generated Tests
                         |
                         v
                +-----------------------+
                | Spark Declarative     |
                | Pipeline              |
                +-----------+-----------+
                            |
                            v
                +-----------------------+
                | Spark Catalyst / AQE  |
                | Execution Optimization|
                +-----------+-----------+
                            |
                            v
                       PIT Alerts
```

------------------------------------------------------------------------

# 4. Why Spark Declarative Pipelines should be the execution layer

Apache Spark Declarative Pipelines (SDP) is designed for declarative,
reliable, maintainable, and testable Spark pipelines. Pipeline
definitions can be written in Python or SQL, and SDP analyzes
dependencies between pipeline objects and orchestrates execution.

Official documentation:

-   https://spark.apache.org/docs/latest/declarative-pipelines-programming-guide.html
-   https://github.com/apache/spark/blob/master/docs/declarative-pipelines-programming-guide.md

For this architecture, SDP should **not** be the surveillance DSL.

Instead:

``` text
PIT DSL
   |
   v
Compiler
   |
   v
Generated PySpark
   |
   v
SDP
```

Example generated pipeline:

``` python
from pyspark import pipelines as dp
from pyspark.sql import functions as F
from pyspark.sql.window import Window


@dp.materialized_view(name="pit_001_alerts")
def pit_001_alerts():

    trades = spark.read.table("silver.trades")
    events = spark.read.table("silver.material_events")

    joined = (
        trades
        .join(
            events,
            trades.instrument_id == events.instrument_id,
            "inner"
        )
    )

    aggregated = (
        joined
        .groupBy(
            "account_id",
            "instrument_id",
            "event_id"
        )
        .agg(
            F.sum(
                F.col("quantity") * F.col("price")
            ).alias("total_notional")
        )
    )

    window_spec = (
        Window
        .partitionBy("event_id")
        .orderBy(F.col("total_notional").desc())
    )

    ranked = (
        aggregated
        .withColumn(
            "rank",
            F.row_number().over(window_spec)
        )
    )

    return (
        ranked
        .filter(F.col("total_notional") >= F.lit(1_000_000))
        .filter(F.col("rank") <= F.lit(10))
    )
```

------------------------------------------------------------------------

# 5. The most important design decision: an intermediate representation

Do **not** build:

``` text
DSL -> string templates -> PySpark
```

Instead:

``` text
DSL
 |
 v
Parser
 |
 v
Review IR
 |
 v
Validation
 |
 v
IR Optimization
 |
 v
PySpark Generator
```

The Review IR should be independent of PySpark.

Example:

``` python
from dataclasses import dataclass
from typing import Any


@dataclass
class JoinNode:
    left: str
    right: str
    join_type: str
    condition: Any


@dataclass
class AggregateNode:
    group_by: list[str]
    metrics: dict[str, Any]


@dataclass
class WindowNode:
    partition_by: list[str]
    order_by: list[str]
    function: str


@dataclass
class ThresholdNode:
    metric: str
    operator: str
    value: Any


@dataclass
class ReviewPlan:
    sources: list[str]
    joins: list[JoinNode]
    aggregates: list[AggregateNode]
    windows: list[WindowNode]
    thresholds: list[ThresholdNode]
```

This allows multiple generators later:

``` text
Review IR
   |
   +--> PySpark generator
   +--> Spark SQL generator
   +--> Test generator
   +--> Documentation generator
```

------------------------------------------------------------------------

# 6. Validation

Validation should occur before code generation.

## Structural validation

Use Pydantic or JSON Schema.

Example:

``` python
from pydantic import BaseModel
from typing import Literal


class Threshold(BaseModel):
    metric: str
    operator: Literal[">", ">=", "<", "<=", "=", "!="]
    value: float


class Ranking(BaseModel):
    function: Literal[
        "row_number",
        "rank",
        "dense_rank"
    ]
    partition_by: list[str]
    order_by: list[str]


class ReviewDefinition(BaseModel):
    id: str
    name: str
    version: str
    sources: dict[str, str]
    thresholds: list[Threshold]
    ranking: Ranking | None = None
```

## Semantic validation

The compiler should also verify:

``` text
- source exists
- referenced column exists
- join key exists on both sides
- types are compatible
- aggregation references valid expressions
- ranking references generated columns
- threshold metric is numeric where required
- window ordering is deterministic
- aliases are unambiguous
```

Example:

``` python
def validate_threshold(metric, schema):
    metric_type = schema.get(metric)

    if metric_type is None:
        raise ValueError(f"Unknown metric: {metric}")

    if metric_type not in {"int", "long", "double", "decimal"}:
        raise TypeError(f"Threshold metric must be numeric: {metric}")
```

This validation layer is a major mechanism for reducing generated-code
errors.

------------------------------------------------------------------------

# 7. Deterministic PySpark generation

Do not allow arbitrary Python expressions in the DSL.

Instead define controlled mappings.

Example:

``` python
FILTER_OPERATORS = {
    ">": lambda c, v: f'F.col("{c}") > F.lit({v})',
    ">=": lambda c, v: f'F.col("{c}") >= F.lit({v})',
    "<": lambda c, v: f'F.col("{c}") < F.lit({v})',
    "<=": lambda c, v: f'F.col("{c}") <= F.lit({v})',
    "=": lambda c, v: f'F.col("{c}") == F.lit({v})',
}
```

Then:

``` python
generate_filter("total_notional", ">=", 1_000_000)
```

produces:

``` python
F.col("total_notional") >= F.lit(1_000_000)
```

The compiler controls the possible output.

------------------------------------------------------------------------

# 8. Window/ranking generation

DSL:

``` yaml
ranking:
  function: row_number
  partition_by:
    - event_id
  order_by:
    - total_notional desc
```

Generated PySpark:

``` python
window_spec = (
    Window
    .partitionBy("event_id")
    .orderBy(F.col("total_notional").desc())
)

ranked = (
    df.withColumn(
        "rank",
        F.row_number().over(window_spec)
    )
)
```

Controlled function registry:

``` python
RANK_FUNCTIONS = {
    "row_number": "F.row_number()",
    "rank": "F.rank()",
    "dense_rank": "F.dense_rank()",
}
```

------------------------------------------------------------------------

# 9. Aggregation generation

DSL:

``` yaml
aggregations:
  group_by:
    - account_id
    - instrument_id

  metrics:
    total_notional:
      function: sum
      expression: quantity * price

    trade_count:
      function: count
      expression: trade_id
```

Generated PySpark:

``` python
aggregations = [
    (
        F.sum(
            F.col("quantity") * F.col("price")
        )
        .alias("total_notional")
    ),
    F.count("trade_id").alias("trade_count"),
]

result = (
    df
    .groupBy(
        "account_id",
        "instrument_id"
    )
    .agg(*aggregations)
)
```

Controlled function registry:

``` python
AGGREGATIONS = {
    "sum": F.sum,
    "count": F.count,
    "avg": F.avg,
    "min": F.min,
    "max": F.max,
}
```

------------------------------------------------------------------------

# 10. Where optimization should happen

There are two distinct optimization layers.

## Compiler-level optimization

The PIT compiler can optimize the logical review:

-   Remove unused columns
-   Remove redundant filters
-   Merge compatible predicates
-   Reuse common expressions
-   Detect common subplans
-   Avoid duplicate aggregations
-   Validate dependency ordering

## Spark-level optimization

Spark should handle:

-   physical join strategy
-   shuffle planning
-   broadcast decisions
-   physical execution
-   adaptive execution
-   runtime statistics

Do not rebuild these in the PIT compiler.

Spark DataFrames feed into Spark's query planning and optimization
infrastructure.

During testing, inspect generated plans using:

``` python
df.explain("extended")
```

Reference:

https://spark.apache.org/docs/latest/api/python/reference/pyspark.sql/api/pyspark.sql.DataFrame.explain.html

------------------------------------------------------------------------

# 11. Apache Calcite: where it fits

Apache Calcite is an excellent relational algebra and optimizer
framework.

It represents queries as relational operator trees and applies planner
rules and cost models to transform them into semantically equivalent
lower-cost plans.

References:

-   https://calcite.apache.org/docs/algebra.html
-   https://calcite.apache.org/javadocAggregate/org/apache/calcite/plan/RelOptPlanner.html

However, Calcite does not directly provide:

``` text
Custom PIT DSL -> PySpark
```

You would still need:

``` text
PIT DSL
   |
   v
Calcite relational model
   |
   v
Calcite optimizer
   |
   v
PySpark generator
```

For a Spark-native platform, this introduces another optimizer and plan
representation while Spark already owns execution optimization.

Therefore:

**Use Calcite as architectural inspiration or a future option, not as
the first implementation.**

Calcite becomes more attractive if the platform later needs its own
sophisticated cross-engine optimizer.

------------------------------------------------------------------------

# 12. SQLGlot: useful supporting technology

SQLGlot provides an AST, SQL parser/transpiler, and logical optimizer.

Official resources:

-   https://sqlglot.com/sqlglot
-   https://github.com/tobymao/sqlglot
-   https://github.com/tobymao/sqlglot/blob/main/sqlglot/optimizer/optimizer.py

It supports optimization transformations such as predicate pushdown,
projection pushdown, join optimization, subquery elimination, CTE
elimination, simplification, and type annotation.

Example:

``` python
import sqlglot
from sqlglot.optimizer import optimize

expression = sqlglot.parse_one(
    """
    SELECT account_id, SUM(notional)
    FROM trades
    WHERE employee_flag = true
    GROUP BY account_id
    """,
    dialect="spark",
)

optimized = optimize(
    expression,
    dialect="spark",
    schema={
        "trades": {
            "account_id": "STRING",
            "notional": "DECIMAL",
            "employee_flag": "BOOLEAN",
        }
    },
)

print(optimized.sql(dialect="spark"))
```

SQLGlot is useful if Spark SQL is used as an intermediate
representation.

It should not be treated as the complete PIT-review compiler.

------------------------------------------------------------------------

# 13. Substrait: future-proofing option

Substrait is an open specification for cross-language relational algebra
plans.

Official resources:

-   https://substrait.io/
-   https://substrait.io/tutorial/examples/
-   https://github.com/substrait-io/substrait-python

Substrait is interesting if the platform eventually needs:

``` text
PIT DSL
    |
    v
Surveillance IR
    |
    +----> Spark
    +----> DuckDB
    +----> Trino
    +----> Other engines
```

Substrait examples also demonstrate plans being created and consumed by
Spark.

For V1, however, a custom surveillance-specific IR is simpler and gives
more control over domain semantics.

------------------------------------------------------------------------

# 14. Common feature/subplan reuse

For a large surveillance platform, this may become one of the most
valuable optimizations.

Suppose:

``` text
PIT001 -> 7-day aggregate notional
PIT002 -> 7-day aggregate notional
PIT003 -> 7-day aggregate notional
PIT004 -> 7-day trade count
```

Do not independently compute the same 7-day aggregate three times.

Instead:

``` text
                  Silver Trades
                       |
                       v
               Common Features
              /                 \
     7D Notional              7D Count
       /   |   \                  |
      /    |    \                 |
 PIT001 PIT002 PIT003           PIT004
```

A canonical logical-plan representation can be hashed:

``` python
import hashlib


def canonical_subplan_hash(canonical_plan: str) -> str:
    return hashlib.sha256(
        canonical_plan.encode("utf-8")
    ).hexdigest()
```

Equivalent subplans can then be reused or materialized once.

This is potentially more valuable than micro-optimizing generated Python
because it avoids duplicated Spark work across many reviews.

------------------------------------------------------------------------

# 15. Generated project structure

A generated review could produce:

``` text
generated/
└── pit_001/
    ├── review.yaml
    ├── pipeline/
    │   ├── pit_001.py
    │   └── spark-pipeline.yml
    ├── tests/
    │   ├── test_pit_001.py
    │   └── fixtures/
    └── metadata/
        ├── logical_plan.json
        └── compiler_manifest.json
```

Example compiler manifest:

``` json
{
  "review_id": "PIT_001",
  "review_version": "1.0",
  "compiler_version": "2.3.0",
  "source_schema_version": "silver-v5",
  "generator": "pyspark"
}
```

The declarative review should be the source of truth. Generated code is
a build artifact.

------------------------------------------------------------------------

# 16. Testing strategy

Testing should operate at multiple levels.

### Level 1 --- DSL validation

``` text
Is the definition structurally valid?
```

### Level 2 --- semantic validation

``` text
Do tables, columns, functions and types make sense?
```

### Level 3 --- generated-code validation

``` text
Does generated PySpark compile?
Does SDP dry-run?
```

### Level 4 --- golden-data testing

Example:

``` text
Input:

trade_id | employee | notional
---------|----------|---------
T1       | true     | 900K
T2       | true     | 1.2M
T3       | false    | 5.0M

Threshold = 1M

Expected alert:
T2
```

The generated review must produce the expected result.

------------------------------------------------------------------------

# 17. CI/CD

Recommended flow:

``` text
Review DSL
    |
    v
Pull Request
    |
    v
Compiler
    |
    +--> Schema validation
    +--> Semantic validation
    +--> Logical-plan validation
    +--> Generate PySpark
    +--> Generate tests
    +--> SDP dry-run
    +--> Golden-data tests
    |
    v
Generated artifacts
    |
    v
Deployment
    |
    v
Spark Declarative Pipeline
```

This makes a new review primarily a **configuration/declarative
change**, while the compiler remains a centrally governed piece of
engineering software.

------------------------------------------------------------------------

# 18. Recommended technology stack

  Requirement                 Recommendation
  --------------------------- ---------------------------------
  Review definition           Custom YAML/JSON DSL
  Schema validation           Pydantic / JSON Schema
  Internal representation     Custom Surveillance Review IR
  Parser                      YAML/JSON parser
  Logical optimization        Custom IR rules
  SQL AST/tooling             SQLGlot, where useful
  Portable relational IR      Substrait, optional
  Full relational optimizer   Calcite, optional/future
  Code generation             Deterministic PySpark generator
  Execution optimizer         Spark Catalyst
  Runtime optimization        Spark AQE
  Pipeline orchestration      Spark Declarative Pipelines
  Version control             Git
  Testing                     Pytest + golden datasets
  CI/CD                       Enterprise CI/CD platform

------------------------------------------------------------------------

# 19. Recommended V1 DSL primitives

Do not attempt to expose all Spark functionality initially.

Start with:

``` text
SOURCE
JOIN
FILTER
SELECT
DERIVE
GROUP BY
SUM
COUNT
AVG
MIN
MAX
ROW_NUMBER
RANK
DENSE_RANK
LAG
LEAD
TIME WINDOW
THRESHOLD
AND
OR
NOT
ALERT
```

Add primitives only when real PIT reviews require them.

This keeps the compiler deterministic and makes validation substantially
easier.

------------------------------------------------------------------------

# 20. Final architecture decision

### Recommended

``` text
Custom PIT DSL
      |
      v
Custom Surveillance Review IR
      |
      v
Semantic Validation
      |
      v
Logical Optimization / Common-Subplan Detection
      |
      v
Deterministic PySpark Generator
      |
      v
Spark Declarative Pipeline
      |
      v
Spark Catalyst / AQE
      |
      v
PIT Alerts
```

### Supporting OSS

-   **Spark SDP** --- pipeline definition and orchestration
-   **Spark Catalyst/AQE** --- execution optimization
-   **SQLGlot** --- optional SQL AST and logical SQL optimization
-   **Substrait** --- optional future portable IR
-   **Apache Calcite** --- optional future independent relational
    optimizer

### Not recommended

``` text
Custom DSL
   |
   v
Calcite
   |
   v
Scala
   |
   v
PySpark
```

This creates unnecessary translation layers for a Spark-native solution.

------------------------------------------------------------------------

# 21. Key architectural principle

> **The compiler owns surveillance semantics. Spark owns execution
> optimization.**

The compiler answers:

-   What data is required?
-   What joins are required?
-   What calculations are required?
-   What aggregation is required?
-   What time window is required?
-   What ranking is required?
-   What threshold is required?
-   What constitutes an alert?

Spark answers:

-   How should the join execute?
-   What physical plan is best?
-   How should shuffles execute?
-   Should a join be broadcast?
-   How should execution adapt to runtime statistics?
-   How should the generated computation be distributed?

This separation keeps the solution generic, deterministic, maintainable,
and scalable.

------------------------------------------------------------------------

# 22. Bottom line

The goal should not be to build a generic "PySpark code generator."

The better goal is to build a **PIT Review Compiler**:

``` text
              PIT REVIEW COMPILER
                     |
        +------------+------------+
        |                         |
   Review DSL                Review IR
        |                         |
        +------------+------------+
                     |
                Validation
                     |
                Optimization
                     |
              Code Generation
                     |
          +----------+----------+
          |                     |
       PySpark                Tests
          |
          v
       SDP Pipeline
          |
          v
        Spark
          |
          v
       PIT Alerts
```

The core intellectual property should be the **Surveillance Review IR,
semantic validator, optimization rules, and deterministic PySpark
generator**. Spark, SDP, SQLGlot, Substrait, and Calcite should be
supporting infrastructure rather than the core surveillance compiler.
