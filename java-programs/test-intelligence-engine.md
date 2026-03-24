# 🧠 Test Knowledge Intelligence System (Generic, Framework-Agnostic)

## 📌 Problem Statement

Modern test automation repositories (Selenium, Cucumber, Cypress, Playwright, Karate, TestNG, etc.) contain hundreds of scenarios, step definitions, stubs, and persona configurations. While these repositories are rich in testing knowledge, they suffer from the following challenges:

### ❗ Challenges

1. **Lack of Knowledge Reusability**

   * Existing test cases are not easily reusable for new requirements.
   * Engineers rewrite similar scenarios repeatedly.

2. **Framework Lock-in**

   * Test logic is tightly coupled with specific frameworks (Selenium, Cypress, etc.).
   * No unified understanding across different tools.

3. **No Intelligent Guidance**

   * Given a new requirement (e.g., JIRA ticket), there is no system to:

     * Suggest relevant flows
     * Recommend test scenarios
     * Identify required stubs
     * Determine correct persona
     * Maintain consistency in feature/step definitions

4. **Complex Code Semantics**

   * Test logic is often hidden behind:

     * Page Object Models
     * Helper methods
     * Abstractions
   * Hard to statically analyze with traditional parsing

---

## 🎯 Objective

Build a **Generic Test Knowledge Intelligence System** that:

### Given:

* A test automation repository (any framework)
* A new requirement (e.g., JIRA ticket)

### Should output:

* Relevant existing test flows
* Suggested new test scenarios
* Required stub data (CRITICAL)
* Appropriate persona/user
* Standardized feature file format
* Step definition guidance

---

## 🧠 Core Design Principle

> ❗ Do NOT deeply parse code
> ✅ Extract signals + use AI for semantic understanding

---

## 🏗️ High-Level Architecture

```id="architecture"
Test Repo (Any Framework)
        ↓
[1. Adapter Layer (Signal Extraction)]
        ↓
[2. AI Normalization Layer]
        ↓
[3. Canonical Test Model (CTM)]
        ↓
[4. Knowledge Graph + Embeddings]
        ↓
[5. Retrieval + Reasoning Engine]
        ↓
[JIRA Input → Intelligent Suggestions]
```

---

## 🧩 Component Breakdown

---

### 🟢 1. Adapter Layer (Framework-Agnostic)

#### Purpose:

Extract **raw signals**, NOT full semantics.

#### Responsibilities:

* Read files
* Detect framework
* Extract:

  * Scenario names
  * Raw steps (gherkin or code lines)
  * Raw code snippets
  * Stub references and raw stub content (if accessible)

---

#### ⚠️ IMPORTANT:

Adapters should attempt to extract:

* Stub file references
* Inline stub payloads
* Mock configurations (WireMock, Karate, Cypress intercepts)

Adapters are **not responsible for correctness**, only signal capture.

---

#### Example Output:

```json id="adapter-output"
{
  "intent": "Login success",
  "raw_steps": [
    "When user enters username",
    "And clicks login"
  ],
  "raw_code": [
    "driver.findElement(By.id(\"login\")).click()",
    "loginPage.submit()"
  ],
  "raw_stub_content": [
    {
      "name": "auth_success",
      "content": {
        "status": "SUCCESS",
        "token": "abc123"
      }
    }
  ]
}
```

---

### 🔵 2. AI Normalization Layer (Semantic Brain)

#### Purpose:

Convert raw signals → structured understanding

#### Input:

* raw_steps
* raw_code
* raw_stub_content
* optional context (file name, API hints)

---

#### Output:

Structured CTM with **schema-driven stub intelligence**

---

#### Example Output:

```json id="ai-output"
{
  "intent": "Login success",
  "actions": [
    {"type": "input", "target": "username"},
    {"type": "input", "target": "password"},
    {"type": "click", "target": "login_button"}
  ],
  "assertions": [
    {"type": "url", "expected": "/dashboard"}
  ],
  "apis": [
    {
      "endpoint": "/auth/login",
      "method": "POST"
    }
  ],
  "persona": "user",
  "stubs": [
    {
      "name": "auth_success",
      "summary": "Successful login response returning auth token",
      "schema": {
        "status": "string",
        "token": "string"
      },
      "key_fields": {
        "status": "SUCCESS"
      },
      "scenario_type": "success",
      "related_api": "/auth/login",
      "variations_possible": [
        "INVALID_PASSWORD",
        "EXPIRED_PASSWORD",
        "USER_LOCKED"
      ]
    }
  ]
}
```

---

### 🟣 3. Canonical Test Model (CTM)

#### Purpose:

Unified representation across ALL frameworks

---

## 🔴 CRITICAL REQUIREMENT: Stub Intelligence MUST Be Embedded

> CTM must store **stub meaning**, not just references or full payloads.

---

### Final CTM Schema

```json id="ctm-schema"
{
  "id": "string",
  "intent": "string",
  "tags": [],

  "persona": {
    "role": "",
    "auth_type": ""
  },

  "actions": [
    {
      "type": "navigate | input | click | api_call | wait",
      "target": "",
      "value": "",
      "meta": {}
    }
  ],

  "assertions": [
    {
      "type": "url | text | api_response",
      "target": "",
      "expected": ""
    }
  ],

  "apis": [
    {
      "endpoint": "",
      "method": ""
    }
  ],

  "stubs": [
    {
      "name": "string",

      "summary": "string",

      "schema": {
        "field": "type"
      },

      "key_fields": {
        "field": "example_value"
      },

      "scenario_type": "success | failure | edge_case",

      "related_api": "string",

      "variations_possible": []
    }
  ],

  "flow": {
    "pattern": "",
    "sequence": []
  }
}
```

---

### 🧠 Why This Design Works

* No dependency on external files or storage
* Fully self-contained intelligence
* Optimized for AI reasoning and retrieval
* Avoids large payload overhead
* Enables generation of new scenarios

---

### 🟡 4. Knowledge Layer

---

#### 4.1 Graph (Relationships)

```id="graph"
Scenario → Actions → APIs → Stubs → Persona → Assertions
```

---

#### 4.2 Embeddings (RAG)

Embed:

```id="embedding"
"Login flow using /auth/login returning SUCCESS token"
```

Include:

* intent
* actions summary
* stub summary
* API

---

### 🔴 5. Retrieval + Reasoning Engine

---

#### Input:

```id="jira"
User should not login with expired password
```

---

#### Step 1: Retrieve Similar Flows

Using embeddings:

* login success
* auth failure

---

#### Step 2: Graph Query

```json id="graph-output"
{
  "api": "/auth/login",
  "stubs": ["auth_success", "auth_failure"],
  "persona": ["user"]
}
```

---

#### Step 3: Rule Engine

```id="rules"
IF API = /auth/login AND failure
THEN generate failure stub variation
```

---

#### Step 4: AI Reasoning

```json id="final-output"
{
  "newScenarios": [
    "Login with expired password should fail",
    "User should be redirected to reset password"
  ],
  "stub": "auth_expired_password",
  "persona": "existing_user"
}
```

---

## ⚙️ Implementation Plan

---

### Phase 1: Adapter Layer

* Extract raw_steps, raw_code
* Extract raw_stub_content

---

### Phase 2: AI Normalization

* Generate:

  * actions
  * assertions
  * APIs
  * stub summary
  * stub schema
  * scenario classification

---

### Phase 3: CTM Storage

* Store structured CTM JSON
* Ensure each scenario has stub intelligence (or explicit "no stub")

---

### Phase 4: Embeddings

* Include stub summary + API context

---

### Phase 5: Query Engine

* Use CTM + embeddings
* No dependency on external stub storage

---

## 🤖 AI Prompt (Final)

```id="ai-prompt"
You are a test automation expert.

Convert the given raw test scenario into structured JSON.

You MUST:
- Identify actions
- Identify assertions
- Identify APIs
- Analyze stub content
- Generate stub summary
- Generate full schema of stub
- Extract key fields
- Classify stub (success/failure/edge)
- Suggest possible variations

INPUT:
<raw scenario>

OUTPUT:
{
  "intent": "",
  "actions": [],
  "assertions": [],
  "apis": [],
  "stubs": [],
  "persona": ""
}
```

---

## 🔑 Key Design Principles

---

### ✅ 1. Store Meaning, Not Payload

* No full stub storage
* Only schema + summary + key fields

---

### ✅ 2. Adapters Are Shallow

* No deep parsing
* No semantic understanding

---

### ✅ 3. AI Handles Semantics

* Infers flow
* Maps APIs and stubs
* Generates structure

---

### ✅ 4. Stub Intelligence is Core

Without stub intelligence:

* ❌ No negative scenarios
* ❌ No edge case generation
* ❌ Weak reasoning

---

### ✅ 5. System is Language Agnostic

Because it operates on:

```id="agnostic"
intent + actions + assertions + APIs + stubs
```

---

## 🚀 Expected Outcome

Given:

```id="example"
User should not login with expired password
```

System outputs:

* 🔁 Flow: LOGIN_FLOW
* 🔗 API: /auth/login
* 📦 Stub: auth_expired_password
* 👤 Persona: existing_user
* 🧪 Scenarios:

  * expired password failure
  * password reset redirect

---

## 💡 Final Insight

👉 This system is:

**A Test Knowledge Intelligence Engine**

NOT:

* a test framework
* a parser
* a runner

---

## ✅ What Claude Should Do

1. Implement adapter pipeline
2. Build AI normalization layer
3. Define CTM schema
4. Extract and store stub intelligence (schema + summary)
5. Build knowledge graph
6. Implement embedding + retrieval
7. Build JIRA → suggestion engine

---

**End of Specification**
