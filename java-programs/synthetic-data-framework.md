# 🧩 Pluggable Synthetic Data Generation Framework

**(Copula + TVAE + Rules + Lineage + Scenario Engine)**

---

## 🧠 Overview

This framework enables **enterprise-grade synthetic data generation** by cleanly separating:

* **Data Realism** → ML models (Copula, TVAE)
* **Data Correctness** → Rules + Constraints
* **Data Dependency** → Lineage-driven orchestration
* **Test Control** → Scenario injection

---

## 🎯 Goals

* Support **100+ repositories (federated ownership)**
* Handle **non-linear relationships**
* Enforce **field-level validations & business rules**
* Enable **conditional + scenario-based generation**
* Provide **deterministic, scalable pipelines**
* Allow **plug-and-play model replacement**

---

## 🏗️ High-Level Architecture

```
                ┌──────────────────────────────┐
                │   Federated Metadata Layer   │
                │ (Schemas + Lineage + Rules)  │
                └──────────────┬───────────────┘
                               │
                     ┌─────────▼─────────┐
                     │ Dataset Composer  │
                     │ (dependency graph)│
                     └─────────┬─────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
┌───────▼────────┐   ┌────────▼────────┐   ┌────────▼────────┐
│ Copula Engine  │   │   TVAE Engine   │   │ Bayesian Engine │
│ (fast baseline)│   │ (complex data)  │   │ (rule-driven)   │
└───────┬────────┘   └────────┬────────┘   └────────┬────────┘
        │                     │                     │
        └──────────────┬──────┴──────────────┬──────┘
                       │                     │
                ┌──────▼────────┐
                │ Merge Engine  │
                └──────┬────────┘
                       │
                ┌──────▼────────┐
                │ Rule Engine   │
                └──────┬────────┘
                       │
                ┌──────▼────────┐
                │ Validator     │
                └──────┬────────┘
                       │
                ┌──────▼────────┐
                │ Scenario Eng. │
                └──────┬────────┘
                       │
                ┌──────▼────────┐
                │ Output Layer  │
                │ (S3 / DB / MQ)│
                └───────────────┘
```

---

## 🧩 Core Components

---

### 🔷 1. Federated Metadata + Lineage Layer

Each application owns its schema, constraints, and lineage.

```yaml
entity: account
source: account-service

fields:
  - name: age
    type: int
    constraints:
      min: 18

  - name: salary
    type: float
    depends_on: [age]
    rule: salary >= age * 1000

  - name: risk_score
    type: int
    derived: true
    formula: risk_score = f(age, salary)
```

### Responsibilities:

* Define schema
* Define constraints
* Define dependencies
* Drive execution order

---

### 🔷 2. Generator Interface (Pluggable Design)

```python
from abc import ABC, abstractmethod

class BaseGenerator(ABC):

    @abstractmethod
    def train(self, df, metadata):
        pass

    @abstractmethod
    def sample(self, n, conditions=None):
        pass
```

---

### 🔷 3. Copula Generator (Fast Baseline)

```python
from sdv.tabular import GaussianCopula

class CopulaGenerator(BaseGenerator):

    def __init__(self):
        self.model = GaussianCopula()

    def train(self, df, metadata):
        self.model.fit(df)

    def sample(self, n, conditions=None):
        if conditions:
            return self.model.sample_conditions(
                conditions=[conditions],
                num_rows=n
            )
        return self.model.sample(n)
```

---

### 🔷 4. TVAE Generator (Non-linear Patterns)

```python
from sdv.tabular import TVAE

class TVAEGenerator(BaseGenerator):

    def __init__(self):
        self.model = TVAE(epochs=300)

    def train(self, df, metadata):
        self.model.fit(df)

    def sample(self, n, conditions=None):
        return self.model.sample(n)
```

---

### 🔷 5. Generator Factory (Dynamic Selection)

```python
class GeneratorFactory:

    @staticmethod
    def get_generator(metadata):
        if metadata.get("complexity") == "high":
            return TVAEGenerator()
        elif metadata.get("type") == "rule-heavy":
            return CopulaGenerator()
        else:
            return CopulaGenerator()
```

---

### 🔷 6. Dataset Composer (Lineage-Aware Execution)

```python
import networkx as nx

class DatasetComposer:

    def __init__(self, metadata_registry):
        self.graph = self.build_graph(metadata_registry)

    def build_graph(self, registry):
        g = nx.DiGraph()
        for entity in registry:
            for dep in entity.get("depends_on", []):
                g.add_edge(dep, entity["name"])
        return g

    def execution_order(self):
        return list(nx.topological_sort(self.graph))
```

---

### 🔷 7. Rule Engine (Deterministic Enforcement)

```python
class RuleEngine:

    def apply(self, df, rules):
        for rule in rules:
            df = self.apply_rule(df, rule)
        return df

    def apply_rule(self, df, rule):

        if rule["type"] == "min":
            df[rule["field"]] = df[rule["field"]].clip(lower=rule["value"])

        elif rule["type"] == "max":
            df[rule["field"]] = df[rule["field"]].clip(upper=rule["value"])

        elif rule["type"] == "expression":
            df = df.eval(rule["expression"])

        return df
```

---

### 🔷 8. Validator Layer

```python
class Validator:

    def validate(self, df, constraints):
        for c in constraints:
            assert df[c["field"]].between(
                c.get("min", float("-inf")),
                c.get("max", float("inf"))
            ).all()
```

---

### 🔷 9. Scenario Engine (Test Injection)

```python
class ScenarioEngine:

    def inject(self, df, scenario):

        if scenario["type"] == "edge_case":
            df.loc[:10, "salary"] = 0

        elif scenario["type"] == "high_value":
            df.loc[:10, "salary"] = 1_000_000

        return df
```

---

### 🔷 10. Orchestrator (Core Engine)

```python
class SyntheticDataOrchestrator:

    def __init__(self, registry):
        self.registry = registry
        self.composer = DatasetComposer(registry)

    def run(self, data_map, n, scenario=None):

        results = {}

        for entity in self.composer.execution_order():

            metadata = self.registry[entity]
            df = data_map[entity]

            # Select generator
            generator = GeneratorFactory.get_generator(metadata)

            # Train model
            generator.train(df, metadata)

            # Generate synthetic data
            synthetic = generator.sample(n)

            # Apply rules
            synthetic = RuleEngine().apply(
                synthetic,
                metadata.get("rules", [])
            )

            # Validate
            Validator().validate(
                synthetic,
                metadata.get("constraints", [])
            )

            # Scenario injection
            if scenario:
                synthetic = ScenarioEngine().inject(synthetic, scenario)

            results[entity] = synthetic

        return results
```

---

## 🚀 Execution Example

```python
orchestrator = SyntheticDataOrchestrator(registry)

synthetic_data = orchestrator.run(
    data_map={
        "account": df_account,
        "customer": df_customer
    },
    n=1000,
    scenario={"type": "edge_case"}
)
```

---

## 🧠 Design Principles

### ✅ Separation of Concerns

* Models → realism
* Rules → correctness
* Lineage → dependency

---

### ✅ Pluggability

* Add new generators easily:

  * Diffusion models
  * GANs
  * Custom domain generators

---

### ✅ Deterministic Layer

* Rules override ML randomness

---

### ✅ Federated Ownership

* Each repo owns:

  * schema
  * rules
  * lineage

---

### ✅ Composability

* Multi-entity datasets via dependency graph

---

## ⚠️ Production Enhancements

---

### 🔷 1. Distributed Execution

* Apache Spark / Flink
* Parallel generation per entity

---

### 🔷 2. Model Caching

* Store trained models
* Avoid retraining overhead

---

### 🔷 3. Versioning

* Dataset reproducibility
* Version:

  * metadata
  * models
  * rules

---

### 🔷 4. Quality Metrics

* KS Test (distribution similarity)
* Correlation drift
* Coverage metrics
* Constraint violation rate

---

### 🔷 5. Rejection Sampling Layer

```python
def generate_valid_samples(generator, n):
    result = []

    while len(result) < n:
        batch = generator.sample(200)
        batch = RuleEngine().apply(batch, rules)

        valid = batch[
            (batch["age"] >= 18) &
            (batch["salary"] >= 0)
        ]

        result.append(valid)

    return pd.concat(result).head(n)
```

---

## 🧩 Recommended Technology Stack

| Layer             | Technology           |
| ----------------- | -------------------- |
| Generation        | SDV (Copula, TVAE)   |
| Rules             | Drools / Python DSL  |
| Lineage           | YAML + NetworkX      |
| Orchestration     | Python / Spark       |
| Storage           | S3 / Delta Lake      |
| Metadata Registry | Git + Config Service |

---

## 🏁 Final Takeaway

> Synthetic data generation at scale is not just a modeling problem.

It requires:

* **Statistical Models (Copula / TVAE)**
* **Rule Enforcement Engine**
* **Lineage-Based Composition**
* **Scenario Control Layer**

---

## 🔚 Conclusion

This framework delivers:

* ✅ Realistic synthetic data
* ✅ Business-rule correctness
* ✅ Scalable and distributed architecture
* ✅ Pluggable model ecosystem
* ✅ Deterministic validation

---

**This is the architecture used in modern enterprise-grade synthetic data platforms.**
