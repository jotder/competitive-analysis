# Architecture Decision Records

Load-bearing decisions for the Real Estate CxO Decision-Support Agent.
See [`../architecture_v2_prudent.md`](../architecture_v2_prudent.md) for the full design.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-llm-orchestrates-never-calculates.md) | LLM orchestrates and narrates; never calculates | Accepted |
| [0002](0002-reconcile-dont-average.md) | Reconcile conflicting source values; never flat-average | Accepted |
| [0003](0003-modular-monolith-java25-postgres.md) | Modular monolith on Java 25 + PostgreSQL/pgvector | Accepted |
| [0004](0004-langchain4j-native-defer-mcp-adk.md) | LangChain4j native tools; defer MCP and Google ADK | Accepted |

Convention: ADRs are immutable once Accepted. To change a decision, add a new ADR that supersedes it.
