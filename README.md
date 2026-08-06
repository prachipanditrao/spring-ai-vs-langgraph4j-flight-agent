# Spring AI vs LangGraph4j: Flight Rescheduling Agent Benchmark

A production-style comparative benchmark evaluating **Spring AI (ChatClient advisor loop)** and **LangGraph4j (StateGraph)** for building multi-tool, multi-turn AI agents in **Java 21+**.

This repository implements the exact same enterprise scenario across both frameworks to highlight fundamental trade-offs in **control flow, type safety, state management, and policy enforcement**.

---

## Scenario: Flight Rescheduling Assistant

The agent processes the following customer request:

> **"My flight AC-402 was canceled due to bad weather. Check my current booking status, search replacement flights to Seattle, and rebook me if a flight is under $500."**

### Available Tools

| Tool | Description |
|------|-------------|
| **getBookingStatus** | Fetches active passenger booking details. |
| **searchFlights** | Returns candidate replacement flights for a given route. |
| **rebookAndRefund** | Updates the booking and calculates refunds while enforcing a hard **$500 budget cap**. |

---

# Execution Output & Comparison

Running both agents sequentially against a local **qwen2.5:7b** model via **Ollama** demonstrates the fundamental architectural differences between the two frameworks.

## LangGraph4j (Deterministic State Graph)

```text
==================================================
1. RUNNING LANGGRAPH4J AGENT (DETERMINISTIC GRAPH)
==================================================

LangGraph4j Final Log:
[
  Fetched booking record for passenger: Prachi Panditrao,
  Selected candidate flight AC-999 priced at $620.0,
  POLICY INTERCEPT: Selected flight price ($620.0) exceeds $500 threshold.
  Execution bypassed rebook tool and routed to manual approval.
]
```

### What happened?

LangGraph4j evaluates business constraints using Java conditional routing (`addConditionalEdges`). Since the selected flight costs **$620**, execution is redirected to a policy node before the rebooking tool can ever be invoked. The guardrail is enforced at the JVM level rather than relying on the LLM.

---

## Spring AI (Implicit Tool Loop)

```text
==================================================
2. RUNNING SPRING AI AGENT (IMPLICIT TOOL LOOP)
==================================================

Spring AI Response:

I found the following replacement flights to Seattle:

1. Flight AC-501, priced at $380.00.
2. Flight AC-509, priced at $490.00.
3. Flight AC-999, priced at $620.00.

The current flight you are booked on (AC-402) was paid for at $450.00.

Since the price of the first replacement flight (AC-501) is less than or equal to your original payment ($380.00 <= $450.00), I will proceed with rebooking and refunding the difference.

Would you like me to go ahead with this rebooking now?
```

### What happened?

Spring AI relies on the model to reason over system prompts and tool descriptions. Rather than selecting the expensive flight, the LLM evaluates the available options, filters out flights above the budget, and asks the user for confirmation before invoking the rebooking tool.

---

# Technical Lessons

## 1. Spring AI Type Erasure Workaround

When registering generic `Function<T, R>` beans as Spring AI tools, Jackson cannot infer generic record types at runtime. The request payload is deserialized into a `LinkedHashMap`, resulting in a `ClassCastException`.

### Solution

- Register tools using `FunctionCallbackWrapper.builder(...)`
- Explicitly specify `.withInputType(RequestRecord.class)`
- Annotate record fields with `@JsonProperty` to ensure reliable parameter binding

---

## 2. Dependency Isolation

To avoid `NoClassDefFoundError` caused by schema generation conflicts between Spring AI and `langgraph4j-spring-ai`, this project depends directly on **langgraph4j-core**.

This provides access to:

- `StateGraph`
- `AgentState`
- `Channels`

while allowing Spring Boot to manage Jackson dependencies independently.

---

# Project Structure

```text
spring-ai-vs-langgraph4j-flight-agent/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── example/
        │           └── flightagent/
        │               ├── FlightAgentApplication.java
        │               ├── AgentComparisonRunner.java
        │               ├── domain/
        │               ├── tools/
        │               ├── springai/
        │               └── langgraph4j/
        └── resources/
            └── application.properties
```

---

# Running the Project

## Option A: Local Ollama (Default)

Start an Ollama instance:

```bash
ollama run qwen2.5:7b
```

Build and run the application:

```bash
mvn clean compile
mvn spring-boot:run
```

---

## Option B: OpenAI

### Update `pom.xml`

Disable the Ollama starter and enable the OpenAI starter.

```xml
<!--
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
</dependency>
-->

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>
```

### Update `application.properties`

Disable the Ollama configuration and enable the OpenAI configuration.

```properties
# spring.ai.ollama.base-url=http://localhost:11434
# spring.ai.ollama.chat.options.model=qwen2.5:7b

spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.openai.chat.options.model=gpt-4o-mini
```

Export your API key:

```bash
export OPENAI_API_KEY="your-api-key"
```

Run the application:

```bash
mvn spring-boot:run
```

---

# Summary Comparison

| Dimension | Spring AI (ChatClient) | LangGraph4j (StateGraph) |
|------------|------------------------|---------------------------|
| **Primary Paradigm** | Declarative, LLM-driven tool loop | Deterministic state graph |
| **Policy Enforcement** | Prompt-based guardrails | JVM-level conditional routing |
| **State Storage** | Chat history (`ChatMemory`) | Typed reducers (`Channels`) |
| **Setup Overhead** | Low | Moderate |
| **Best Suited For** | Conversational assistants, rapid prototyping | Regulated, financial, and workflow-heavy applications |

---

## Key Takeaways

- **Spring AI** offers a concise, annotation-driven developer experience where the LLM orchestrates tool execution, making it ideal for conversational applications and rapid development.

- **LangGraph4j** provides deterministic execution with explicit state management and JVM-level policy enforcement, making it well suited for production workflows where business rules must be guaranteed regardless of model behavior.

This benchmark demonstrates how the same business problem can be solved using two fundamentally different orchestration paradigms, allowing developers to evaluate the trade-offs between flexibility and deterministic control.