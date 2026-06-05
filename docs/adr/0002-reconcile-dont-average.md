# ADR-0002: Reconcile conflicting source values; never flat-average

**Status:** Accepted
**Date:** 2026-06-01
**Deciders:** Product owner, Eng lead

## Context
The same parameter differs across sources — e.g. a unit's price on the developer site, on MahaRERA, and
on MagicBricks are all different, and its area may be quoted as carpet, built-up, or super built-up. The
sources also differ in credibility (RERA is authoritative; listing portals are advertised/indicative).
The naive instinct is to average the three into one number. That is wrong here: (1) it pollutes a credible
value with an unreliable one; (2) the values often measure different things (carpet vs super built-up
area; asking vs registered price), so averaging is apples-to-oranges; (3) it hides the disagreement, which
is itself the signal (negotiation room / RERA-vs-listing discrepancy).

## Decision
Keep **every** source value as an append-only `ParameterObservation` (never overwrite). Reconcile
deterministically in three steps:
1. **Normalize** to one canonical basis first (e.g. ₹ per **carpet** sqft using RERA carpet area; tag price
   definition: asking / quoted / registered).
2. **Consolidate by credibility — never flat-average.** Default method **ANCHOR**: canonical value = the
   most credible source for that parameter; others shown as variances. Alternatives: **WEIGHTED** (by tier)
   when one number is required, **RANGE/MEDIAN** to express uncertainty.
3. **Quantify and expose the spread** (`variance_pct`, agreement band). A wide spread is surfaced as a
   signal, not smoothed away.
The consolidated value stores `inputs_ref` to all observations, and the UI/agent **always cite all source
values with their tiers**.

## Options Considered

### Option A: Flat arithmetic mean → single value
| Dimension | Assessment |
|---|---|
| Complexity | Low |
| Correctness | **Poor** — mixes tiers, mixes definitions, hides signal |
| Auditability | Poor |

**Pros:** Trivial.
**Cons:** Destroys the credibility signal; misleading for decisions.

### Option B: Normalize → anchor by credibility → show spread (chosen)
| Dimension | Assessment |
|---|---|
| Complexity | Medium |
| Correctness | **High** — like-for-like, credibility-aware |
| Auditability | High — all observations retained and cited |

**Pros:** Honest, defensible, turns disagreement into insight.
**Cons:** Requires per-parameter normalization rules and an authoritative-source map.

## Trade-off Analysis
More modelling work (units, area basis, anchor rules) in exchange for numbers a CxO can defend in a board
room. Given sparse credible data, transparency about disagreement *is* the product's trustworthiness.

## Consequences
- **Easier:** auditing; surfacing discrepancies; supporting gap-fill and what-if on the same observation model.
- **Harder:** must maintain normalization rules and an authoritative-source-per-parameter map.
- **Revisit:** the default consolidation method per parameter as we learn which sources to trust.

## Action Items
1. [ ] `ParameterObservation` (append-only) + `ConsolidatedParameter` (method, value/range, variance, inputs_ref).
2. [ ] Normalization rules: area basis → carpet; price definition tagging.
3. [ ] Authoritative-source map per parameter (drives ANCHOR).
4. [ ] Encode the v2.2 §6 worked example (3BHK) as a reconciliation test fixture.
