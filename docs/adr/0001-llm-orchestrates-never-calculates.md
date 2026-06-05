# ADR-0001: The LLM orchestrates and narrates; it never calculates

**Status:** Accepted
**Date:** 2026-06-01
**Deciders:** Product owner, Eng lead

## Context
This is a decision-support tool for CxOs. Numbers (price/sqft, amenity premium, variance, absorption)
drive capital-allocation and pricing decisions. A hallucinated figure that looks plausible is a
catastrophic, trust-destroying failure. LLMs are strong at language and orchestration but unreliable at
arithmetic and prone to inventing confident numbers. Credible source data is also sparse, so the
provenance of every figure must be explicit.

## Decision
The LLM (Gemini via LangChain4j) **only** decides which tool to call, with what parameters, and phrases
the answer. **All numbers are produced by deterministic, unit-tested Java services** and passed to the
LLM as tool results carrying a value, a credibility tier, and a source reference. The LLM may not perform
arithmetic, estimate, average, or infer a number that no tool returned. If a tool returns no data, the
agent says "no data" — it never fills the gap with an estimate. RAG supplies qualitative context only,
never figures.

## Options Considered

### Option A: LLM computes / free-form reasoning over raw data
| Dimension | Assessment |
|---|---|
| Complexity | Low (less plumbing) |
| Cost | Low upfront |
| Reliability | **Unacceptable** — hallucinated/incorrect numbers |
| Auditability | Poor — no traceable derivation |

**Pros:** Fast to build; flexible.
**Cons:** Non-deterministic numbers; no audit trail; fatal for a CxO tool.

### Option B: Deterministic tools; LLM orchestrates + narrates (chosen)
| Dimension | Assessment |
|---|---|
| Complexity | Medium — fixed tool layer over tested services |
| Cost | Medium |
| Reliability | **High** — every figure deterministic and reproducible |
| Auditability | High — each number cites its source observation(s) |

**Pros:** Trustworthy, testable, auditable; the math can be verified independently of the model.
**Cons:** More engineering; the agent can only answer what a tool exposes (acceptable, even desirable).

## Trade-off Analysis
We trade some flexibility and upfront speed for correctness, reproducibility, and auditability. For a
tool whose entire value is trustworthy strategic numbers, that trade is non-negotiable.

## Consequences
- **Easier:** unit-testing analytics; citing every figure; swapping the LLM provider.
- **Harder:** every new quantitative capability needs a real, tested tool — no shortcuts via prompting.
- **Revisit:** never for correctness-critical numbers. Qualitative summarization latitude can grow.

## Action Items
1. [ ] Expose analytics/reconciliation only through a fixed, audited `@Tool` set.
2. [ ] System prompt forbids unsourced figures; add a response check that each number maps to a tool output.
3. [ ] Tool results carry `{value, tier, source_ref, computed_at}`.
