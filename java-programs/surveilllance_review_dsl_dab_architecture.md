# Surveillance Review DSL → Compiler → PySpark → Databricks DAB Architecture

**Version:** 1.0  
**Date:** 2026-09-02  
**Status:** Proposed target architecture

## 1. Executive Summary

The target architecture migrates market-manipulation and sales-practice surveillance reviews from Scala/Kubernetes to Databricks using a declarative Review DSL, a deterministic compiler, a reusable PySpark common engine, and Databricks Declarative Automation Bundles (DABs).

The entire solution remains in **one Git repository**:

```text
Review DSL + Immutable Review Versions
              |
        Release Manifest
              |
        make validate/build/test/package
              |
        generated/ -> dist/
              |
        DAB validation/deployment
              |
          Databricks Jobs
              |
      Common Framework WHL
              |
        Silver / DQ Data
              |
          Alert Tables
```

Core decisions:

1. Review versions are immutable.
2. A release manifest selects the exact review versions deployed.
3. Compilation happens during CI/build, never in production runtime.
4. `generated/` and `dist/` are build outputs and are not committed to Git.
5. DAB resource definitions live in the same repository and reference stable paths under `dist/`.
6. DAB owns Databricks infrastructure/deployment, not surveillance semantics.
7. DDL separately owns final tables/views and database objects.
8. The common surveillance engine is a versioned Python wheel, supplied as a dependency from a governed Databricks Volume.
9. One immutable release artifact is promoted DEV → UAT → PROD.
10. Production alerts retain enough metadata to trace back to review version, release, compiler, framework, source commit and execution.

---

## 2. Target Architecture

```text
                           ONE GIT REPOSITORY
┌───────────────────────────────────────────────────────────────────┐
│                                                                   │
│  reviews/       compiler/       framework/       releases/         │
│  Review DSL     Compiler/IR    Common Engine    Release manifests │
│                                                                   │
│  resources/     ddl/           tests/           Makefile           │
│  DAB jobs       DDL            Tests                              │
│                                                                   │
│  databricks.yml                                                     │
└──────────────────────────────┬────────────────────────────────────┘
                               |
                        CI BUILD STAGE
                               |
                  +------------+------------+
                  |                         |
                  v                         v
           make validate              make build
                                            |
                                            v
                                    Surveillance Compiler
                                            |
                         +------------------+------------------+
                         |                  |                  |
                         v                  v                  v
                       Parser             IR              Codegen
                         |                  |                  |
                         +------------------+------------------+
                                            |
                                            v
                                      generated/
                                            |
                                      make test
                                            |
                                      make package
                                            |
                                            v
                                          dist/
                                            |
                                  DAB bundle validate
                                            |
                                  DAB bundle deploy
                                            |
                     +----------------------+----------------------+
                     |                      |                      |
                     v                      v                      v
                    DEV                    UAT                    PROD
                     |                      |                      |
                     +----------------------+----------------------+
                                            |
                                            v
                                  Databricks Job(s)
                                            |
                                            v
                              Common Framework Python WHL
                                            |
                                            v
                                      Silver / DQ
                                            |
                                            v
                                   Review execution
                                            |
                                            v
                                   Alert DataFrame
                                            |
                                            v
                                   Delta alert table
```

---

## 3. Separation of Responsibilities

### Review DSL

Defines **what the review does**:

- source datasets
- joins
- filters
- derived fields
- aggregations
- windows
- ranking
- thresholds
- scoring
- alert conditions
- evidence
- LOB variations

It should not contain arbitrary Python.

### Compiler

Defines **how the DSL becomes executable code**:

```text
DSL
 ↓
Schema validation
 ↓
Semantic validation
 ↓
Review IR
 ↓
IR normalization/optimization
 ↓
PySpark generation
 ↓
Metadata generation
```

The compiler is deterministic.

### Common Surveillance Framework

Contains reusable runtime functionality:

```text
source loading
joins
filters
derivations
aggregations
windows
ranking
rules
scoring
evidence
alert construction
audit metadata
```

Package it as a versioned wheel, e.g.:

```text
surveillance_framework-2.2.1-py3-none-any.whl
```

### DAB

Owns:

- jobs
- tasks
- compute
- schedules
- permissions
- environment configuration
- libraries
- references to generated Python entrypoints

### DDL

Owns:

- target table schema
- views
- table properties
- constraints
- grants
- database objects

---

## 4. Repository Structure

```text
surveillance-platform/
│
├── compiler/
│   ├── parser/
│   │   ├── manifest_loader.py
│   │   └── dsl_loader.py
│   ├── validator/
│   │   ├── schema_validator.py
│   │   └── semantic_validator.py
│   ├── ir/
│   │   ├── nodes.py
│   │   └── review_plan.py
│   ├── optimizer/
│   │   ├── canonicalizer.py
│   │   └── common_subplans.py
│   ├── codegen/
│   │   ├── expression_generator.py
│   │   ├── join_generator.py
│   │   ├── aggregate_generator.py
│   │   ├── window_generator.py
│   │   ├── rule_generator.py
│   │   └── review_generator.py
│   └── cli.py
│
├── framework/
│   └── surveillance_framework/
│       ├── sources.py
│       ├── joins.py
│       ├── filters.py
│       ├── aggregations.py
│       ├── windows.py
│       ├── rules.py
│       ├── alerts.py
│       └── audit.py
│
├── reviews/
│   ├── MP001/
│   │   ├── manifest.yaml
│   │   └── versions/
│   │       ├── 1.0/
│   │       │   ├── review.yaml
│   │       │   ├── rules.yaml
│   │       │   └── lob/
│   │       └── 2.1/
│   │           ├── review.yaml
│   │           ├── rules.yaml
│   │           └── lob/
│   ├── MP002/
│   │   └── versions/
│   │       └── 1.4/
│   └── SP001/
│       └── versions/
│           └── 1.7/
│
├── releases/
│   ├── dev.yaml
│   ├── uat.yaml
│   └── prod.yaml
│
├── resources/
│   ├── mp001.job.yml
│   ├── mp002.job.yml
│   └── sp001.job.yml
│
├── ddl/
│   ├── mp001.sql
│   ├── mp002.sql
│   └── sp001.sql
│
├── tests/
│   ├── compiler/
│   ├── generated/
│   ├── golden/
│   └── integration/
│
├── generated/              # .gitignore
├── dist/                   # .gitignore
├── databricks.yml
├── Makefile
├── pyproject.toml
└── .gitignore
```

---

## 5. Immutable Review Versioning

Do not maintain a mutable production definition such as:

```text
reviews/MP001/review.yaml
```

Instead:

```text
reviews/MP001/versions/
    1.0/
    2.0/
    2.1/
```

Once a version is released, it is immutable.

If a threshold changes from:

```text
1,000,000 -> 750,000
```

create a new version, for example:

```text
2.0 -> 2.1
```

Do not modify 2.0.

---

## 6. Release Manifest

The release manifest determines what is active in a deployment.

```yaml
release:
  id: surveillance-2026.09.02

compiler:
  version: 1.3.0

framework:
  name: surveillance_framework
  version: 2.2.1
  artifact: /Volumes/platform/shared/artifacts/surveillance_framework-2.2.1-py3-none-any.whl

reviews:

  MP001:
    version: 2.1

  MP002:
    version: 1.4

  SP001:
    version: 1.7
```

This is the deployment composition.

The review itself does not need to say “I am currently active.”

---

## 7. Example Review DSL

```yaml
review:
  id: MP001
  version: 2.1

sources:

  trades:
    table: silver.trades

  instruments:
    table: silver.instruments

steps:

  - id: enriched_trades
    type: join
    left: trades
    right: instruments
    join_type: left
    condition:
      expression: "trades.instrument_id = instruments.instrument_id"

  - id: eligible_trades
    type: filter
    input: enriched_trades
    condition:
      expression: "trades.status = 'EXECUTED'"

  - id: rolling_metrics
    type: aggregate
    input: eligible_trades

    group_by:
      - account_id
      - instrument_id

    window:
      duration: "7 days"
      timestamp_column: trade_timestamp

    metrics:

      rolling_notional:
        expression: "SUM(quantity * price)"

      rolling_trade_count:
        expression: "COUNT(*)"

  - id: suspicious_activity
    type: threshold
    input: rolling_metrics

    conditions:

      - metric: rolling_notional
        operator: ">"
        value: 1000000

      - metric: rolling_trade_count
        operator: ">"
        value: 50

alert:
  evidence:
    - account_id
    - instrument_id
    - rolling_notional
    - rolling_trade_count
```

---

## 8. DSL Validation

Use Pydantic/JSON Schema for structural validation.

```python
from pydantic import BaseModel
from typing import Literal


class Source(BaseModel):
    table: str


class Condition(BaseModel):
    expression: str


class Step(BaseModel):
    id: str
    type: Literal[
        "join",
        "filter",
        "derive",
        "aggregate",
        "window",
        "rank",
        "threshold"
    ]


class Review(BaseModel):
    id: str
    version: str
    sources: dict[str, Source]
    steps: list[Step]
```

Semantic validation should additionally check:

```text
source references exist
step references exist
threshold metrics exist
join columns exist
window columns exist
LOB overrides are legal
types are compatible
unsupported operations are rejected
no circular dependencies
```

---

## 9. Review IR

Do not compile directly:

```text
YAML -> PySpark
```

Use:

```text
YAML
 ↓
Review IR
 ↓
PySpark
```

Example:

```python
from dataclasses import dataclass
from typing import Any


@dataclass
class JoinNode:
    id: str
    left: str
    right: str
    join_type: str
    condition: Any


@dataclass
class FilterNode:
    id: str
    input: str
    condition: Any


@dataclass
class AggregateNode:
    id: str
    input: str
    group_by: list[str]
    metrics: dict[str, Any]


@dataclass
class ThresholdNode:
    id: str
    input: str
    metric: str
    operator: str
    value: Any


@dataclass
class ReviewPlan:
    review_id: str
    review_version: str
    sources: dict
    nodes: list
```

The IR is the compiler's stable internal contract.

---

## 10. Compiler Pipeline

```text
CLI
 |
 v
Release Manifest Loader
 |
 v
Version Resolver
 |
 v
DSL Loader
 |
 v
Schema Validator
 |
 v
Semantic Validator
 |
 v
Review IR Builder
 |
 v
IR Optimizer
 |
 +--> canonicalization
 +--> common-subplan detection
 |
 v
PySpark Generator
 |
 v
Metadata Generator
 |
 v
generated/
```

---

## 11. Expression Parsing

Use SQLGlot selectively for SQL-like expressions.

For example:

```python
import sqlglot

expression = sqlglot.parse_one(
    "quantity * price > 1000000",
    read="spark"
)
```

The compiler can translate supported AST nodes to controlled PySpark Column expressions.

The DSL must not become arbitrary Python execution.

Spark Catalyst/AQE remains responsible for physical execution optimization.

---

## 12. PySpark Code Generation

Use deterministic generators by IR node type:

```text
compiler/codegen/
    expression_generator.py
    source_generator.py
    join_generator.py
    filter_generator.py
    derive_generator.py
    aggregation_generator.py
    window_generator.py
    ranking_generator.py
    threshold_generator.py
    alert_generator.py
    review_generator.py
```

Conceptual example:

```python
class JoinGenerator:

    def generate(self, node):
        return (
            f'{node.left}.join('
            f'{node.right}, '
            f'{self.generate_condition(node.condition)}, '
            f'"{node.join_type}"'
            f')'
        )
```

For production, use a structured Python source-generation technique rather than uncontrolled string concatenation.

---

## 13. Generated PySpark

Prefer thin generated entrypoints where the common framework can execute the plan.

```python
# AUTO-GENERATED
# DO NOT EDIT

from surveillance_framework import execute_review


REVIEW_ID = "MP001"
REVIEW_VERSION = "2.1"
LOB = "EQUITIES"

COMPILER_VERSION = "1.3.0"
FRAMEWORK_VERSION = "2.2.1"


def main(spark):

    return execute_review(
        spark=spark,
        review_id=REVIEW_ID,
        review_version=REVIEW_VERSION,
        lob=LOB
    )


if __name__ == "__main__":
    main(spark)
```

Generated metadata can sit beside it:

```text
dist/reviews/MP001/
    review.py
    logical_plan.json
    review_manifest.json
```

---

## 14. Common Framework

Example runtime engine:

```python
def execute_review(
    spark,
    review_id,
    review_version,
    lob
):

    config = load_compiled_review(
        review_id,
        review_version,
        lob
    )

    df = load_sources(
        spark,
        config.sources
    )

    df = apply_joins(
        df,
        config.joins
    )

    df = apply_preprocessing(
        df,
        config.preprocessing
    )

    df = apply_aggregations(
        df,
        config.aggregations
    )

    df = apply_windows(
        df,
        config.windows
    )

    df = evaluate_rules(
        df,
        config.rules
    )

    return build_alerts(
        df,
        config.alert
    )
```

The common engine should be versioned independently from review versions.

---

## 15. Common-Subplan Optimization

If several reviews calculate the same feature:

```text
                    Silver Trades
                         |
                         v
                 Common Features
                  /                              /                       7D Notional             7D Count
             |                     |
       +-----+-----+               |
       |     |     |               |
      MP001 MP002 MP003          MP004
```

Canonicalize IR subplans and hash them:

```python
import hashlib


def subplan_hash(canonical_plan: str) -> str:
    return hashlib.sha256(
        canonical_plan.encode("utf-8")
    ).hexdigest()
```

Equivalent logical subplans can then be shared/materialized.

---

## 16. Makefile

The Makefile provides a Maven-like lifecycle.

```makefile
RELEASE ?= releases/dev.yaml
COMPILER = python -m surveillance_compiler

.PHONY: clean validate build test package

clean:
	rm -rf generated
	rm -rf dist

validate:
	$(COMPILER) validate 		--release $(RELEASE)

build: validate
	$(COMPILER) build 		--release $(RELEASE) 		--output generated

test: build
	pytest tests/

package: test
	$(COMPILER) package 		--release $(RELEASE) 		--input generated 		--output dist
```

Developer usage:

```bash
make clean
make validate RELEASE=releases/prod.yaml
make build RELEASE=releases/prod.yaml
make test RELEASE=releases/prod.yaml
make package RELEASE=releases/prod.yaml
```

Or simply:

```bash
make package RELEASE=releases/prod.yaml
```

---

## 17. Build Output

After:

```bash
make build
```

produce:

```text
generated/
└── surveillance-2026.09.02/
    ├── MP001/
    │   └── 2.1/
    │       ├── review.py
    │       ├── logical_plan.json
    │       ├── review_manifest.json
    │       └── tests/
    ├── MP002/
    │   └── 1.4/
    │       └── review.py
    └── SP001/
        └── 1.7/
            └── review.py
```

`generated/` is build output and should not be committed.

---

## 18. Packaging

Package into a stable deployment layout:

```text
dist/
└── reviews/
    ├── MP001/
    │   └── review.py
    ├── MP002/
    │   └── review.py
    └── SP001/
        └── review.py

dist/
└── manifests/
    └── release.json
```

Prefer the stable path:

```text
dist/reviews/MP001/review.py
```

instead of:

```text
dist/reviews/MP001/2.1/review.py
```

The release manifest already determines which version was compiled.

---

## 19. DAB Job Definition

DAB resources live in the same repository.

Example:

```yaml
# resources/mp001.job.yml

resources:

  jobs:

    mp001_surveillance:

      name: surveillance-mp001

      tasks:

        - task_key: execute_mp001

          spark_python_task:
            python_file: ../dist/reviews/MP001/review.py

          libraries:

            - whl: /Volumes/platform/shared/artifacts/surveillance_framework-2.2.1-py3-none-any.whl

          job_cluster_key: surveillance_cluster

      job_clusters:

        - job_cluster_key: surveillance_cluster

          new_cluster:
            spark_version: "<approved-version>"
            node_type_id: "<approved-node-type>"
            num_workers: 2
```

The DAB definition knows:

```text
MP001 entrypoint
compute
library
permissions
schedule
```

It does not know how MP001's surveillance logic works.

---

## 20. Why the Stable `dist` Path Is Better

Avoid:

```yaml
python_file: ../dist/reviews/MP001/2.1/review.py
```

because it duplicates version information in infrastructure.

Prefer:

```yaml
python_file: ../dist/reviews/MP001/review.py
```

The build process determines that this file was generated from:

```text
MP001 v2.1
```

and records that in metadata.

The DAB configuration remains stable when MP001 moves from 2.1 to 2.2.

---

## 21. Framework Dependency

Store the approved framework wheel in a governed Databricks Volume:

```text
/Volumes/platform/shared/artifacts/
    surveillance_framework-2.2.1-py3-none-any.whl
```

Attach it to the job:

```yaml
libraries:

  - whl: /Volumes/platform/shared/artifacts/surveillance_framework-2.2.1-py3-none-any.whl
```

Never use an unversioned production dependency such as:

```text
surveillance_framework-latest.whl
```

The framework version must be part of the release manifest.

---

## 22. DDL Separation

Keep final table/view definitions separately:

```text
ddl/
    mp001.sql
    mp002.sql
    sp001.sql
```

Example:

```sql
CREATE TABLE IF NOT EXISTS surveillance.alerts.mp001_alerts
(
    alert_id        STRING,
    review_id       STRING,
    review_version  STRING,
    rule_id         STRING,
    lob             STRING,
    alert_timestamp TIMESTAMP,
    evidence        STRING,
    risk_score      DECIMAL(18,6)
)
USING DELTA;
```

The generated review returns a DataFrame/result.

The data-object deployment layer owns table definitions.

---

## 23. Environment Independence

Do not hard-code:

```text
DEV table names
UAT table names
PROD table names
```

into generated review logic.

Use DAB/environment configuration for:

```text
catalog
schema
target table
compute
permissions
schedules
```

This allows the same compiled artifact to move:

```text
DEV → UAT → PROD
```

without recompilation.

---

## 24. CI/CD Flow

Recommended pipeline:

```text
                         Git PR
                           |
                           v
                  +-------------------+
                  | CI Build Pipeline |
                  +-------------------+
                           |
                    make validate
                           |
                           v
                       make build
                           |
                           v
                       make test
                           |
                           v
                     make package
                           |
                           v
                         dist/
                           |
                    artifact boundary
                           |
                           v
                databricks bundle validate
                           |
                           v
                 databricks bundle deploy
                           |
              +------------+------------+
              |            |            |
              v            v            v
             DEV          UAT          PROD
```

The critical property is that `dist/` exists in the same CI workspace when the DAB deployment executes.

No manual copying is required.

---

## 25. Example CI Shell Sequence

Conceptually:

```bash
set -e

make clean

make validate RELEASE=releases/prod.yaml

make build RELEASE=releases/prod.yaml

make test RELEASE=releases/prod.yaml

make package RELEASE=releases/prod.yaml

databricks bundle validate

databricks bundle deploy
```

Your organization's CI wrapper can replace the final Databricks CLI commands.

---

## 26. Do Not Compile at Runtime

Avoid:

```text
Databricks Job
    |
    +-- download DSL
    +-- run compiler
    +-- generate PySpark
    +-- execute
```

Prefer:

```text
CI
 |
 +-- validate
 +-- compile
 +-- test
 +-- package
 |
 v
Immutable artifact
 |
 v
Databricks
 |
 v
execute
```

This improves reproducibility, auditability, startup time and production safety.

---

## 27. Release Manifest Generated by Build

Example:

```json
{
  "release_id": "surveillance-2026.09.02",

  "compiler": {
    "version": "1.3.0",
    "git_commit": "abc123"
  },

  "framework": {
    "name": "surveillance_framework",
    "version": "2.2.1"
  },

  "reviews": [
    {
      "review_id": "MP001",
      "version": "2.1",
      "lob": "EQUITIES",
      "source_sha256": "..."
    },
    {
      "review_id": "MP002",
      "version": "1.4",
      "lob": "EQUITIES",
      "source_sha256": "..."
    }
  ]
}
```

This should travel with the release artifact.

---

## 28. Audit Lineage

Every production alert should be traceable:

```text
Alert
  |
  v
Review ID
  |
  v
Review Version
  |
  v
Release ID
  |
  +--> Compiler Version
  |
  +--> Framework Version
  |
  +--> Git Commit
  |
  +--> Source Hash
  |
  +--> Generated Artifact Hash
  |
  v
DAB Deployment
  |
  v
Databricks Job Run
```

Recommended alert metadata:

```text
alert_id
review_id
review_version
rule_id
rule_version
lob
release_id
execution_id
alert_timestamp
evidence
risk_score
```

---

## 29. Compiler Manifest

Generated metadata can contain:

```json
{
  "review_id": "MP001",
  "review_version": "2.1",
  "lob": "EQUITIES",

  "compiler_version": "1.3.0",
  "framework_version": "2.2.1",

  "source_schema_version": "silver-v5",

  "git_commit": "abc123",

  "generated_at": "2026-09-02T05:30:00Z",

  "artifact_sha256": "..."
}
```

---

## 30. Testing Strategy

### Level 1 — DSL schema validation

Verify that the YAML conforms to the DSL schema.

### Level 2 — Semantic validation

Verify:

- references
- types
- supported operations
- dependency graph
- thresholds
- LOB configuration

### Level 3 — Compiler tests

Test:

```text
DSL → IR
IR → generated PySpark
```

Use golden snapshots where appropriate.

### Level 4 — Generated code tests

Check:

```text
syntax
imports
static analysis
```

### Level 5 — Golden-data tests

During migration:

```text
Existing Scala
      |
      v
Golden output
      |
      | compare
      v
Generated PySpark
```

### Level 6 — Integration tests

Run generated reviews against representative Silver data.

### Level 7 — Performance regression

Compare execution time, shuffle, input/output sizes and relevant Spark plans against baseline.

---

## 31. Migration Strategy

Do not build all ten reviews and the complete compiler simultaneously.

Choose one representative review containing:

- joins
- aggregations
- windows
- thresholds
- LOB-specific behavior
- preprocessing
- evidence generation

Migration sequence:

```text
Existing Scala MP001
        |
        v
Document actual semantics
        |
        v
Create MP001 DSL
        |
        v
Build compiler support
        |
        v
Generate PySpark
        |
        v
Compare Scala vs PySpark
        |
        v
Validate production behavior
```

Then migrate the remaining reviews.

The existing Scala implementation is the initial golden reference.

---

## 32. Spark Optimization Boundary

Do not duplicate Spark's optimizer.

Architecture:

```text
Surveillance DSL
       |
       v
Surveillance Compiler
       |
       v
Review IR
       |
       v
Generated PySpark
       |
       v
Spark Catalyst
       |
       v
Physical Plan
       |
       v
AQE
       |
       v
Execution
```

The surveillance compiler optimizes **surveillance logic** and identifies common logical subplans.

Spark optimizes **physical execution**.

---

## 33. SQLGlot and Calcite

### SQLGlot

Use selectively for:

- expression parsing
- AST construction
- supported expression transformations
- Spark dialect handling

Do not make it the surveillance compiler.

### Apache Calcite

Do not introduce Calcite in V1.

Avoid:

```text
DSL
 ↓
Custom Compiler
 ↓
Calcite
 ↓
Spark
 ↓
Catalyst
```

This adds another planner/optimizer layer without a strong initial requirement.

Calcite can be reconsidered later if cross-engine execution becomes important.

---

## 34. Why One Repository Is the Preferred Starting Point

One repository provides one PR boundary:

```text
Review DSL change
       +
Compiler change
       +
Generated artifact
       +
DAB resource
       +
DDL
       +
Tests
```

The change can be reviewed and validated together.

A separate repository can be introduced later if organizational ownership or approval boundaries require it.

The architecture does not depend on having multiple repositories.

---

## 35. Build Artifact Rules

`.gitignore`:

```gitignore
generated/
dist/
.pytest_cache/
__pycache__/
*.pyc
```

CI creates these directories from source.

Every build should be reproducible from:

```text
Git commit
+
release manifest
+
compiler version
+
framework version
```

---

## 36. Final Developer Experience

A developer changes a review:

```bash
mkdir -p reviews/MP001/versions/2.1

vim reviews/MP001/versions/2.1/review.yaml
```

Selects it in:

```yaml
# releases/prod.yaml

reviews:
  MP001:
    version: 2.1
```

Then:

```bash
make validate RELEASE=releases/prod.yaml
make build RELEASE=releases/prod.yaml
make test RELEASE=releases/prod.yaml
make package RELEASE=releases/prod.yaml
```

CI then performs:

```text
make validate
      ↓
make build
      ↓
make test
      ↓
make package
      ↓
dist/
      ↓
DAB validate
      ↓
DAB deploy
      ↓
Databricks
```

No manual copying of generated notebooks.

No production compilation.

No overwriting of released review versions.

No duplicated review-version selection in DAB YAML.

---

# 37. Final Architecture Decision

The recommended target is:

```text
                         ONE REPOSITORY
                              |
        +---------------------+---------------------+
        |                     |                     |
        v                     v                     v
   Review DSL             Compiler              DAB/DDL
        |                     |                     |
        +----------+----------+                     |
                   |                                |
                   v                                |
            Release Manifest                         |
                   |                                |
                   v                                |
             make build                             |
                   |                                |
                   v                                |
             generated/                             |
                   |                                |
              make test                             |
                   |                                |
              make package                           |
                   |                                |
                   v                                |
                 dist/ <-----------------------------+
                   |
                   v
          bundle validate/deploy
                   |
                   v
              Databricks
                   |
             +-----+-----+
             |           |
             v           v
          MP001 Job   MP002 Job
             |           |
             +-----+-----+
                   |
                   v
          Common Framework WHL
                   |
                   v
             Silver / DQ
                   |
                   v
             Alert Tables
```

This gives the platform a **Maven-like build lifecycle**, while DAB remains the Databricks deployment mechanism.

The most important architectural boundary is:

> **The review compiler produces the application artifact; DAB deploys that artifact.**

The compiler owns review semantics.  
The common framework owns reusable runtime behavior.  
`make` owns the build lifecycle.  
`dist/` is the immutable deployment input.  
DAB owns Databricks infrastructure.  
DDL owns persistent data objects.  
The release manifest defines the exact production composition.

This is the recommended architecture for migrating the ten surveillance reviews while reducing review-specific PySpark code and improving versioning, reproducibility and auditability.
