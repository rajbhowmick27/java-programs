# Databricks DLT/Lakeflow Data Quality Framework

## 1. Purpose

This document defines a maintainable repository pattern for a Databricks data platform where:

- DLT / Lakeflow Declarative Pipelines perform transformations.
- Data-quality (DQ) rules are maintained as configuration rather than hard-coded in pipeline code.
- DQ rules are grouped by business/technical purpose.
- Common rules are reusable across regions/entities.
- Region/entity-specific rules can be added without changing the generic DQ framework.
- Databricks Asset Bundles (DABs) deploy the pipelines.
- CI validates both application code and DQ configuration before deployment.

The central principle is:

> **Python defines how data is transformed and how the DQ framework operates; configuration defines what constitutes valid data.**

---

# 2. Recommended repository structure

```text
databricks-data-platform/
│
├── databricks.yml
│
├── resources/
│   ├── accounts_pipeline.yml
│   ├── trades_pipeline.yml
│   └── lots_pipeline.yml
│
├── configs/
│   └── dq/
│       │
│       ├── common/
│       │   ├── accounts_expectations.json
│       │   ├── trades_expectations.json
│       │   └── lots_expectations.json
│       │
│       ├── namr/
│       │   ├── accounts_expectations.json
│       │   ├── trades_expectations.json
│       │   └── lots_expectations.json
│       │
│       └── ipb/
│           ├── accounts_expectations.json
│           ├── trades_expectations.json
│           └── lots_expectations.json
│
├── src/
│   │
│   ├── transformation/
│   │   ├── accounts/
│   │   │   ├── accounts_transformation.py
│   │   │   └── accounts_pipeline.py
│   │   │
│   │   ├── trades/
│   │   │   ├── trades_transformation.py
│   │   │   └── trades_pipeline.py
│   │   │
│   │   └── lots/
│   │       ├── lots_transformation.py
│   │       └── lots_pipeline.py
│   │
│   └── common/
│       ├── dq/
│       │   ├── rules_loader.py
│       │   ├── rules_processor.py
│       │   ├── expectations.py
│       │   └── dlt_dq_checker.py
│       │
│       ├── utils/
│       └── constants/
│
├── schemas/
│   └── dq/
│       └── dq_rules.schema.json
│
├── tests/
│   ├── unit/
│   │   ├── dq/
│   │   │   ├── test_rules_loader.py
│   │   │   └── test_rules_processor.py
│   │   └── transformation/
│   │
│   └── integration/
│       └── dq/
│
└── README.md
```

## 3. Responsibility of each area

| Area | Responsibility |
|---|---|
| `configs/dq` | DQ definitions and business rules |
| `configs/dq/common` | Rules shared across regions/entities |
| `configs/dq/<region>` | Region-specific overrides/additional rules |
| `src/transformation` | Entity-specific transformation logic |
| `src/common/dq` | Generic DQ framework |
| `schemas/dq` | Contract for valid DQ configuration |
| `resources` | DAB pipeline/job definitions |
| `tests` | Unit and integration validation |
| `databricks.yml` | Bundle-level deployment configuration |

Keep transformation code and DQ configuration separate.

---

# 4. DQ rule model

A DQ rule should carry enough metadata to be understandable, auditable, and executable.

Recommended structure:

```json
{
  "rule_id": "TRD-001",
  "name": "trade_id_not_null",
  "description": "Trade ID must be populated",
  "group": "completeness",
  "expression": "trade_id IS NOT NULL",
  "action": "fail",
  "severity": "critical",
  "enabled": true
}
```

Recommended fields:

| Field | Purpose |
|---|---|
| `rule_id` | Stable identifier for reporting/audit |
| `name` | Human-readable rule name |
| `description` | Business/technical explanation |
| `group` | DQ category |
| `expression` | Spark SQL Boolean expression |
| `action` | `warn`, `drop`, or `fail` |
| `severity` | `low`, `medium`, `high`, `critical` |
| `enabled` | Enable/disable without deleting the rule |

Do not put Python code inside the configuration.

---

# 5. DQ groups

DQ groups make the rules easier to manage and allow the pipeline to apply different policies to different categories.

A practical initial taxonomy is:

```text
completeness
validity
uniqueness
consistency
referential_integrity
timeliness
business
```

For example:

```json
{
  "rules": [
    {
      "rule_id": "TRD-001",
      "name": "trade_id_not_null",
      "description": "Trade ID must be populated",
      "group": "completeness",
      "expression": "trade_id IS NOT NULL",
      "action": "fail",
      "severity": "critical",
      "enabled": true
    },
    {
      "rule_id": "TRD-002",
      "name": "quantity_positive",
      "description": "Quantity must be greater than zero",
      "group": "validity",
      "expression": "quantity > 0",
      "action": "drop",
      "severity": "high",
      "enabled": true
    },
    {
      "rule_id": "TRD-003",
      "name": "trade_id_unique",
      "description": "Trade ID must be unique",
      "group": "uniqueness",
      "expression": "trade_id IS NOT NULL",
      "action": "fail",
      "severity": "critical",
      "enabled": true
    }
  ]
}
```

### Important limitation

A normal DLT/Lakeflow expectation expression is evaluated against rows. A rule such as `trade_id IS NOT NULL` works naturally.

A true uniqueness check such as "no duplicate `trade_id` exists across the entire dataset" is not generally equivalent to a simple row-level expectation. Treat aggregate/global checks separately, typically with a dedicated validation query or transformation that identifies duplicates.

Therefore, don't force every DQ rule into the same expectation mechanism.

---

# 6. Recommended DQ group semantics

A useful default mapping is:

| Group | Typical checks | Default action |
|---|---|---|
| Completeness | NOT NULL, mandatory fields | fail/drop |
| Validity | range, format, domain | drop |
| Uniqueness | duplicate identifiers | fail/quarantine |
| Consistency | column relationships | drop/fail |
| Referential integrity | valid foreign/reference IDs | fail/quarantine |
| Timeliness | dates/freshness | warn/fail |
| Business | domain-specific business rules | warn/drop/fail |

Do not make these defaults mandatory. The rule's explicit `action` should remain authoritative.

---

# 7. Common vs entity-specific DQ rules

The recommended model is:

```text
Effective rules
    =
common rules
    +
region-specific rules
    +
(optional) entity-specific regional rules
```

For example:

```text
configs/dq/common/trades_expectations.json
configs/dq/namr/trades_expectations.json
```

The common file may contain:

```json
{
  "entity": "trades",
  "rules": [
    {
      "rule_id": "TRD-C-001",
      "name": "trade_id_not_null",
      "group": "completeness",
      "expression": "trade_id IS NOT NULL",
      "action": "fail",
      "severity": "critical",
      "enabled": true
    },
    {
      "rule_id": "TRD-C-002",
      "name": "account_id_not_null",
      "group": "completeness",
      "expression": "account_id IS NOT NULL",
      "action": "fail",
      "severity": "critical",
      "enabled": true
    }
  ]
}
```

NAMR can add:

```json
{
  "entity": "trades",
  "region": "namr",
  "rules": [
    {
      "rule_id": "TRD-NAMR-001",
      "name": "settlement_date_present",
      "group": "business",
      "expression": "settlement_date IS NOT NULL",
      "action": "drop",
      "severity": "high",
      "enabled": true
    }
  ]
}
```

The loader merges these into the effective rule set.

## Rule precedence

Define precedence explicitly.

Recommended:

```text
Common
  ↓
Regional
  ↓
Most-specific override
```

Use `rule_id` as the stable merge key.

If the same `rule_id` appears in both common and regional configuration, the more specific rule should replace the common rule.

Avoid silently merging two definitions with the same ID.

---

# 8. DQ configuration validation

`schemas/dq/dq_rules.schema.json` should define the contract for all expectation files.

It should validate:

- required fields
- field data types
- allowed `action` values
- allowed `severity` values
- allowed `group` values
- rule ID format
- whether `enabled` is Boolean
- whether `expression` is a string

Example fragment:

```json
{
  "type": "object",
  "required": ["entity", "rules"],
  "properties": {
    "entity": {
      "type": "string"
    },
    "region": {
      "type": "string"
    },
    "rules": {
      "type": "array",
      "items": {
        "type": "object",
        "required": [
          "rule_id",
          "name",
          "group",
          "expression",
          "action",
          "severity",
          "enabled"
        ],
        "properties": {
          "rule_id": {
            "type": "string"
          },
          "name": {
            "type": "string"
          },
          "group": {
            "enum": [
              "completeness",
              "validity",
              "uniqueness",
              "consistency",
              "referential_integrity",
              "timeliness",
              "business"
            ]
          },
          "expression": {
            "type": "string"
          },
          "action": {
            "enum": ["warn", "drop", "fail"]
          },
          "severity": {
            "enum": ["low", "medium", "high", "critical"]
          },
          "enabled": {
            "type": "boolean"
          }
        }
      }
    }
  }
}
```

Validate every configuration file in CI before deploying.

---

# 9. Rules loader

`src/common/dq/rules_loader.py` should be responsible for locating, parsing, validating, and merging configuration.

Conceptually:

```python
import json
from pathlib import Path


class DQRulesLoader:

    def __init__(self, config_root="configs/dq"):
        self.config_root = Path(config_root)

    def load(self, region: str, entity: str):
        common_path = (
            self.config_root
            / "common"
            / f"{entity}_expectations.json"
        )

        regional_path = (
            self.config_root
            / region
            / f"{entity}_expectations.json"
        )

        common_rules = self._load_file(common_path)
        regional_rules = self._load_file(
            regional_path,
            required=False
        )

        return self._merge(
            common_rules,
            regional_rules
        )

    @staticmethod
    def _load_file(path, required=True):
        if not path.exists():
            if required:
                raise FileNotFoundError(
                    f"DQ configuration not found: {path}"
                )
            return []

        with path.open("r", encoding="utf-8") as file:
            data = json.load(file)

        return data.get("rules", [])

    @staticmethod
    def _merge(common_rules, regional_rules):
        merged = {
            rule["rule_id"]: rule
            for rule in common_rules
        }

        for rule in regional_rules:
            rule_id = rule["rule_id"]
            merged[rule_id] = rule

        return list(merged.values())
```

Production implementation should additionally perform schema validation and duplicate-ID validation.

---

# 10. Rules processor

The processor should turn configuration into the exact dictionaries expected by the DLT/Lakeflow APIs.

```python
def get_expectations(
    rules,
    action=None,
    group=None
):
    filtered = [
        rule
        for rule in rules
        if rule.get("enabled", True)
    ]

    if action:
        filtered = [
            rule
            for rule in filtered
            if rule["action"] == action
        ]

    if group:
        filtered = [
            rule
            for rule in filtered
            if rule["group"] == group
        ]

    return {
        rule["name"]: rule["expression"]
        for rule in filtered
    }
```

Example:

```python
get_expectations(
    rules,
    action="drop",
    group="validity"
)
```

returns:

```python
{
    "quantity_positive": "quantity > 0",
    "price_non_negative": "price >= 0"
}
```

This dictionary can be passed directly to the corresponding expectation decorator.

---

# 11. Proper DLT/Lakeflow expectation syntax

For current Python pipelines, use the `pyspark.pipelines` API:

```python
from pyspark import pipelines as dp
```

A simple expectation:

```python
@dp.expect(
    "valid_trade_id",
    "trade_id IS NOT NULL"
)
@dp.table
def silver_trades():
    return ...
```

Multiple expectations:

```python
@dp.expect_all({
    "valid_trade_id": "trade_id IS NOT NULL",
    "valid_account_id": "account_id IS NOT NULL"
})
@dp.table
def silver_trades():
    return ...
```

Drop invalid rows:

```python
@dp.expect_all_or_drop({
    "valid_quantity": "quantity > 0",
    "valid_price": "price >= 0"
})
@dp.table
def silver_trades():
    return ...
```

Fail the update:

```python
@dp.expect_all_or_fail({
    "valid_trade_id": "trade_id IS NOT NULL",
    "valid_account_id": "account_id IS NOT NULL"
})
@dp.table
def silver_trades():
    return ...
```

The practical semantics are:

```text
expect / expect_all
    invalid row remains
    expectation metrics are recorded

expect_or_drop / expect_all_or_drop
    invalid row is removed
    expectation metrics are recorded

expect_or_fail / expect_all_or_fail
    pipeline update fails
```

Use the current API exposed by your Databricks Runtime/Lakeflow version; older examples may use the historical `dlt` module and should not automatically be copied into a new implementation.

---

# 12. Applying DQ groups

A group is a filtering dimension; it should not itself necessarily determine the action.

For example:

```text
completeness
 ├── trade_id_not_null     → fail
 └── account_id_not_null   → fail

validity
 ├── quantity_positive     → drop
 └── price_positive        → drop

timeliness
 └── trade_date_recent     → warn
```

The pipeline can retrieve rules by both group and action:

```python
completeness_fail = get_expectations(
    rules,
    group="completeness",
    action="fail"
)

validity_drop = get_expectations(
    rules,
    group="validity",
    action="drop"
)

timeliness_warn = get_expectations(
    rules,
    group="timeliness",
    action="warn"
)
```

Then apply them:

```python
@dp.expect_all(timeliness_warn)
@dp.expect_all_or_drop(validity_drop)
@dp.expect_all_or_fail(completeness_fail)
@dp.table
def silver_trades():
    return transform_trades(...)
```

This gives the pipeline a predictable policy.

---

# 13. Generic DLT DQ checker

The goal of `dlt_dq_checker.py` is to avoid writing the same DQ plumbing for every entity.

A useful abstraction is:

```python
from common.dq.rules_loader import DQRulesLoader
from common.dq.rules_processor import get_expectations


class DLTDataQuality:

    def __init__(
        self,
        region: str,
        entity: str
    ):
        self.region = region
        self.entity = entity
        self.loader = DQRulesLoader()

        self.rules = self.loader.load(
            region=region,
            entity=entity
        )

    def expectations(
        self,
        action=None,
        group=None
    ):
        return get_expectations(
            self.rules,
            action=action,
            group=group
        )

    def warn(self, group=None):
        return self.expectations(
            action="warn",
            group=group
        )

    def drop(self, group=None):
        return self.expectations(
            action="drop",
            group=group
        )

    def fail(self, group=None):
        return self.expectations(
            action="fail",
            group=group
        )
```

Then an entity pipeline becomes very small:

```python
from pyspark import pipelines as dp

from common.dq.dlt_dq_checker import DLTDataQuality
from transformation.trades.trades_transformation import (
    transform_trades
)


dq = DLTDataQuality(
    region="namr",
    entity="trades"
)


@dp.expect_all(dq.warn())
@dp.expect_all_or_drop(dq.drop())
@dp.expect_all_or_fail(dq.fail())
@dp.table
def silver_trades():

    return transform_trades(
        spark.readStream.table("bronze_trades")
    )
```

The same framework works for:

```python
DQDataQuality(region="namr", entity="accounts")
DQDataQuality(region="ipb", entity="trades")
DQDataQuality(region="ipb", entity="lots")
```

without changing the DQ implementation.

---

# 14. A caution about decorator-based generic frameworks

There is an important Python limitation to respect.

Decorators are evaluated when the module/function is defined. Therefore, avoid designing a framework that tries to dynamically mutate decorators after the pipeline has already been declared.

Prefer:

```python
dq = DLTDataQuality(
    region=REGION,
    entity="trades"
)

@dp.expect_all(dq.warn())
@dp.expect_all_or_drop(dq.drop())
@dp.expect_all_or_fail(dq.fail())
@dp.table
def silver_trades():
    ...
```

rather than attempting to dynamically decorate an existing function at runtime.

This is simpler and easier for Databricks pipeline discovery.

---

# 15. Transformation code remains independent

For example:

```python
# src/transformation/trades/trades_transformation.py

from pyspark.sql.functions import col


def transform_trades(source_df):

    return (
        source_df
        .select(
            "trade_id",
            "account_id",
            "instrument_id",
            "quantity",
            "price",
            "trade_date"
        )
        .withColumn(
            "notional",
            col("quantity") * col("price")
        )
    )
```

The transformation does not contain:

```python
@dp.expect(...)
```

and it does not know:

```text
configs/dq/namr/trades_expectations.json
```

That is deliberate.

---

# 16. Entity pipeline

The entity pipeline owns orchestration:

```python
# src/transformation/trades/trades_pipeline.py

from pyspark import pipelines as dp

from common.dq.dlt_dq_checker import DLTDataQuality
from transformation.trades.trades_transformation import (
    transform_trades
)

REGION = "namr"

dq = DLTDataQuality(
    region=REGION,
    entity="trades"
)


@dp.expect_all(dq.warn())
@dp.expect_all_or_drop(dq.drop())
@dp.expect_all_or_fail(dq.fail())
@dp.table
def silver_trades():

    source_df = (
        spark.readStream
        .table("bronze_trades")
    )

    return transform_trades(source_df)
```

This is the desired end state:

```text
Pipeline
   │
   ├── source
   │
   ├── transformation
   │
   ├── generic DQ framework
   │       │
   │       ├── common rules
   │       └── regional rules
   │
   └── target
```

---

# 17. Region should be configuration, not hard-coded

Avoid:

```python
REGION = "namr"
```

in production code.

Prefer a Databricks configuration/parameter supplied through DAB.

Conceptually:

```text
DAB target
    │
    ├── namr-dev
    ├── namr-uat
    ├── namr-prod
    ├── ipb-dev
    └── ipb-prod
```

with:

```text
region = namr
```

or:

```text
region = ipb
```

passed into the pipeline.

The same Python artifact can then be deployed to multiple regions.

---

# 18. DAB responsibility

DAB should describe the Databricks pipeline resource and environment-specific configuration.

Conceptually:

```yaml
resources:
  pipelines:

    trades_pipeline:

      name: "${bundle.target}-trades"

      libraries:
        - file:
            path: ../src/transformation/trades/trades_pipeline.py

        - file:
            path: ../src/transformation/trades/trades_transformation.py

        - file:
            path: ../src/common/dq/dlt_dq_checker.py

        - file:
            path: ../src/common/dq/rules_loader.py

        - file:
            path: ../src/common/dq/rules_processor.py
```

The exact resource syntax should follow the Databricks Asset Bundles schema/version used by the organization.

The important architectural point is that DAB does not contain the DQ rules themselves. It deploys the application/configuration package.

---

# 19. CI/CD flow

Recommended flow:

```text
Developer
    │
    ▼
Git PR
    │
    ├── Python linting
    │
    ├── Unit tests
    │
    ├── JSON syntax validation
    │
    ├── DQ schema validation
    │
    ├── Duplicate rule-ID validation
    │
    ├── DQ expression validation
    │
    └── DAB validate
            │
            ▼
         Deploy DEV
            │
            ▼
       Integration tests
            │
            ▼
         Deploy UAT
            │
            ▼
        Approval
            │
            ▼
         Deploy PROD
```

This is especially important because a DQ JSON file is effectively executable configuration: its expressions directly influence production data processing.

---

# 20. What should be unit tested?

### Rule loader

Test:

- common rules load
- region rules load
- missing common configuration fails
- missing regional configuration follows the agreed policy
- duplicate rule IDs are rejected
- regional rules override common rules correctly

### Rule processor

Test:

- disabled rules are excluded
- `warn` rules are returned correctly
- `drop` rules are returned correctly
- `fail` rules are returned correctly
- group filtering works
- invalid actions are rejected

### Transformation

Test transformation independently of DQ.

### Configuration

Validate every JSON file against `dq_rules.schema.json`.

---

# 21. Do not over-engineer DQ groups

Avoid creating dozens of categories such as:

```text
null_check
format_check
range_check
datatype_check
...
```

A group should represent a meaningful DQ dimension, not the implementation mechanism.

Prefer:

```text
completeness
validity
consistency
uniqueness
referential_integrity
timeliness
business
```

The actual expression determines the specific test.

---

# 22. Quarantine strategy

For a banking data platform, consider separating:

```text
Valid records
     │
     ▼
Silver

Invalid records
     │
     ▼
Quarantine
```

However, a simple `expect_all_or_drop` means the invalid record is dropped from the target and may not automatically give you the full rejected record for downstream remediation.

If business users need to investigate/reprocess bad records, implement a deliberate quarantine pattern rather than relying solely on `drop`.

For example:

```text
Bronze
  │
  ▼
Transformation
  │
  ├───────────────┐
  ▼               ▼
Valid           Invalid
  │               │
  ▼               ▼
Silver        Quarantine
                  │
                  ├── rule_id
                  ├── rule_group
                  ├── ingestion_time
                  └── source metadata
```

This is generally more useful operationally than silently discarding failed rows.

---

# 23. DQ observability

Do not treat DQ as only a pipeline gate.

You should eventually expose:

```text
region
entity
pipeline
rule_id
rule_name
group
severity
execution_date
input_count
passed_count
failed_count
failure_rate
```

This enables reporting such as:

```text
NAMR / Trades

Completeness
  TRD-001 → 0.01% failures
  TRD-002 → 0.00% failures

Validity
  TRD-003 → 0.14% failures
  TRD-004 → 0.03% failures
```

The exact metrics available natively from Lakeflow expectations should be used where appropriate; don't unnecessarily duplicate native expectation metrics.

---

# 24. Recommended final architecture

```text
                         Git Repository
                              │
             ┌────────────────┼────────────────┐
             │                │                │
             ▼                ▼                ▼
       databricks.yml     configs/dq       src/
             │                │                │
             │                │         ┌──────┴──────┐
             │                │         │             │
             │                │         ▼             ▼
             │                │   transformation   common/dq
             │                │         │             │
             │                │         │             │
             │                │         │       ┌─────┴─────┐
             │                │         │       │           │
             │                │         │    loader     processor
             │                │         │       │           │
             │                │         │       └─────┬─────┘
             │                │         │             │
             │                └─────────┼─────────────┘
             │                          │
             ▼                          ▼
           DAB                  Lakeflow/DLT
             │                    Pipeline
             │                         │
             │                  ┌──────┴──────┐
             │                  ▼             ▼
             │             Transformation    DQ
             │                                │
             │                     ┌──────────┼──────────┐
             │                     ▼          ▼          ▼
             │                   WARN       DROP       FAIL
             │
             ▼
       DEV / UAT / PROD
```

## 25. Recommended design principles

1. **Keep DQ rules outside Python.**
2. **Keep transformations outside the DQ framework.**
3. **Keep DAB focused on deployment/configuration.**
4. **Use a common + regional configuration model.**
5. **Use stable `rule_id` values.**
6. **Use DQ groups as classification/filtering, not as hard-coded behavior.**
7. **Let each rule explicitly specify its action.**
8. **Validate DQ configuration in CI.**
9. **Do not put arbitrary Python inside JSON/YAML.**
10. **Keep the generic DQ framework small and understandable.**
11. **Separate row-level expectations from aggregate/global DQ checks.**
12. **Use quarantine when failed records need investigation or replay.**
13. **Make region/environment runtime configuration rather than hard-coding it.**
14. **Keep entity transformation code independently testable.**
15. **Treat DQ configuration as production code: version it, review it, test it, and deploy it through the same controlled process.**

## 26. The resulting mental model

The cleanest way to think about the platform is:

```text
                 WHAT?
                  │
                  ▼
           configs/dq/*.json
           "What is valid?"
                  │
                  ▼
                 HOW?
                  │
                  ▼
           src/common/dq
           "How do we apply it?"
                  │
                  ▼
               WHERE?
                  │
                  ▼
       src/transformation/<entity>
       "How do we transform it?"
                  │
                  ▼
               DEPLOY?
                  │
                  ▼
              DAB / CI-CD
```

This gives you a relatively thin internal framework while retaining the flexibility to grow from three entities and two regions to many entities and regions without duplicating DQ implementation code.
