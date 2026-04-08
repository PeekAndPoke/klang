---
name: Doc-block completeness quality standard
description: All DSL functions and ignitor params MUST have complete doc annotations (description, default, range) for live docs (Lexikon / KlangSymbolDocsComp)
type: project
---

**Standard**: Every sprudel DSL function and ignitor DSL parameter must ship with complete doc-block annotations that
feed into the live documentation system (KlangSymbolDocsComp, Lexikon page).

**Required for every parameter**:

1. A description of what it does
2. Its default value (if any)
3. A "normal" usage range (e.g., "0.0 to 1.0", "typically 100-5000 Hz")
4. What happens at extremes

**Why:** The live documentation popup and Lexikon page are primary discovery and learning surfaces. When a user hovers
over a function in the editor, they should see actionable information — not blank parameter tables. Incomplete docs turn
discoverable features into invisible ones. This is especially harmful for beginners, solo learners, and anyone exploring
the platform without a teacher. Music parameters are frequently non-obvious (is gain 0-1 or 0-100? is time in seconds or
cycles?) and missing range info leads to frustration or avoidance.

**How to apply:**

- Any new DSL function or ignitor parameter must include all three annotation fields before being considered complete
- This is an ongoing quality standard, not a one-time task
- Treat doc completeness as a launch-readiness concern: the Lexikon is only as useful as its content

**Backlog status (2026-04-08):** Major documentation pass COMPLETED across all sprudel DSL and ignitor DSL functions.
The backlog is cleared. The concern now shifts to **preventing drift** — ensuring new functions ship with complete docs.

**Open design question (2026-04-08):** Editor tooltips should show a SHORT form (one-line description + default + range)
with a link to FULL docs (extremes, formulas, option lists). The short-form template needs to be defined before the
format hardens.

**Lexikon integration (2026-04-08):** Terms in param docs should link to Lexikon entries where applicable, but param
descriptions themselves should use plain language, not Lexikon vocabulary (degrading/carving/contouring). The two
vocabularies are parallel, not merged.
