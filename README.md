# Spring AI vs LangGraph4j: Flight Rescheduling Agent Benchmark

A production-style comparative benchmark evaluating **Spring AI (ChatClient loop)** and **LangGraph4j (StateGraph)** for building multi-tool, multi-turn AI agents in **Java 21+**.

This repository implements the **same enterprise business scenario** using both frameworks to compare their approaches to control flow, token consumption, state management, and policy enforcement.

---

## Scenario: Flight Rescheduling Assistant

The agent processes the following passenger request:

> "My flight AC-402 was canceled due to bad weather. Check my current booking status, rebook me on the next available flight to Seattle under $500, and refund the price difference if the new flight is cheaper."

### Tool Capabilities

| Tool | Description |
|------|-------------|
| **getBookingStatus** | Retrieves passenger booking details by reference ID. |
| **searchFlights** | Returns candidate flights for a given route. |
| **rebookAndRefund** | Updates the booking and issues refunds while enforcing a hard **$500 budget cap**. |

---

# Architectural Comparison

| Dimension | Spring AI (ChatClient Advisor Loop) | LangGraph4j (StateGraph Engine) |
|-----------|--------------------------------------|---------------------------------|
| **Control Flow** | Implicit, model-driven execution. The LLM autonomously decides tool invocation order. | Deterministic graph execution with explicit nodes, edges, and conditional routing. |
| **Guardrail Enforcement** | Prompt-based. Relies on system instructions to avoid invalid tool calls. | Programmatic. Conditional graph edges enforce policies before tools execute. |
| **State Management** | Conversation history stored in `MessageChatMemoryAdvisor`. | Strongly typed `AgentState` using channels and reducers (`Channels.last()`, `Channels.appender()`). |
| **Context / Token Growth** | High. Tool outputs remain in prompt history across turns. | Low. Tool outputs remain in internal state unless explicitly injected into prompts. |
| **Developer Overhead** | Low. Annotation-driven with fluent APIs. | Higher. Requires graph schema, node definitions, routing logic, and compilation. |

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

### Module Overview

| Module | Purpose |
|---------|---------|
| `FlightAgentApplication` | Spring Boot entry point |
| `AgentComparisonRunner` | Runs both implementations side-by-side |
| `domain` | Shared domain models (`Booking`, `Flight`) |
| `tools` | Shared Spring AI `@Tool` implementations |
| `springai` | Spring AI agent implementation |
| `langgraph4j` | LangGraph4j graph implementation |

---

# Key Findings

## 1. Spring AI: Token Inflation in Multi-Turn Tool Chains

Spring AI's default `ChatClient` stores every intermediate tool request and response inside chat memory.

When `searchFlights` returns a large JSON payload:

| Step | Approximate Prompt Size |
|------|--------------------------|
| Initial User Request | ~450 tokens |
| After `searchFlights` | ~1,800 tokens |
| Before Rebooking | ~3,400+ tokens |

As conversations grow, prompt size increases significantly because previous tool outputs remain part of the LLM context.

**Implications**

- Higher API costs
- Increased latency
- Greater risk of exceeding context window limits
- More prompt engineering required to manage memory

---

## 2. LangGraph4j: Deterministic Safety Through Graph Routing

LangGraph4j enables business policies to be enforced programmatically rather than relying on prompt instructions.

For example, the graph can prevent rebooking if the selected flight exceeds the budget:

```java
.addConditionalEdges(
    "search_flights",
    state -> state.selectedPrice() <= 500.00
        ? "rebook_flight"
        : "policy_fallback",
    Map.of(
        "rebook_flight", "rebook_flight",
        "policy_fallback", "policy_fallback"
    )
)
```

If the LLM proposes a **$620** flight, execution is routed directly to `policy_fallback` before `rebookAndRefund` can be invoked.

### Benefits

- Deterministic execution
- JVM-level policy enforcement
- No reliance on prompt compliance
- Eliminates an entire class of hallucination-related business logic failures

### Trade-offs

- More boilerplate
- Explicit graph construction
- Higher learning curve compared to Spring AI

---

# Technology Stack

| Component | Version |
|-----------|---------|
| Java | 21+ |
| Spring Boot | 3.3.2 |
| Spring AI | 1.0.x / 1.1.x |
| LangGraph4j Core | 1.8.x |
| Model Providers | OpenAI (`gpt-4o-mini`), Ollama (`qwen2.5`) |

---

# Running the Project

## Prerequisites

Install **Java 21+**.

For OpenAI:

```bash
export OPENAI_API_KEY="your-api-key"
```

Or run a local Ollama instance and configure the application accordingly.

---

## Clone the Repository

```bash
git clone https://github.com/your-username/spring-ai-vs-langgraph4j-flight-agent.git
cd spring-ai-vs-langgraph4j-flight-agent
```

---

## Build

```bash
mvn clean package
```

---

## Run

```bash
mvn spring-boot:run
```

The benchmark executes both implementations against the same flight rescheduling scenario, allowing you to compare:

- Control flow
- Tool execution
- Token growth
- State management
- Guardrail enforcement
- Overall developer experience

---

# Summary

This project demonstrates two distinct philosophies for building enterprise AI agents in Java.

### Spring AI

- Minimal setup
- Annotation-driven tools
- Rapid development
- LLM-directed orchestration
- Better suited for straightforward agent workflows

### LangGraph4j

- Explicit graph orchestration
- Deterministic execution
- Programmatic guardrails
- Efficient state handling
- Better suited for production systems with strict business rules and complex workflows

While both frameworks can solve the same problem, they optimize for different priorities: **Spring AI favors developer productivity**, whereas **LangGraph4j prioritizes deterministic control, safety, and scalability.**