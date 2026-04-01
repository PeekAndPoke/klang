---
name: Engine metaphor naming system
description: Complete signal chain naming using combustion engine metaphor — Motor, Fuel, Injection, Cylinder, Ignitor, Katalyst, master output TBD. Updated 2026-04-01.
type: project
---

**Engine metaphor vocabulary (decided 2026-04-01, refined 2026-04-01):**

| Engine term   | Audio meaning                                           | Role                                         | Narrative         |
|---------------|---------------------------------------------------------|----------------------------------------------|-------------------|
| **Motor**     | The whole audio engine                                  | The system                                   | —                 |
| **Fuel**      | Voices, oscillators, sound sources                      | Raw material                                 | The energy source |
| **Injection** | Event scheduler / distributor                           | Routes fuel into Cylinders at the right time | Prepare           |
| **Cylinder**  | Independent audio channel/bus (replaces "Orbit")        | Parallel processing lane                     | —                 |
| **Ignitor**   | Core transformation (distortion, waveshaping, exciters) | What changes it                              | Ignite            |
| **Katalyst**  | Bus effects (reverb, delay, compression)                | What refines it                              | Refine            |
| **??? (TBD)** | Master output / final stereo mix                        | Where all Cylinders converge                 | Deliver           |

**Signal chain slogan:** "Prepare, Ignite, Refine" (Injection -> Ignitor -> Katalyst)

**Fuel / Injection / Distributor clarification (2026-04-01):**

- **Fuel** = Voices/oscillators are the raw material, not the injection. Gasoline is not "the injection."
- **Injection** = The system that decides WHEN and WHERE to deliver fuel (voices) into Cylinders. This IS the
  event scheduler/distributor. In a real engine, the fuel injection system handles routing and timing.
- **"Distributor" eliminated** — it was proposed as a separate concept for the event scheduler, but Injection
  already covers this role completely. A distributor in a real engine is part of the ignition/injection system,
  not a separate stage.

**Master output: under consideration (2026-04-01):**
Top candidates analyzed:

- **Dynamo** — converts engine output to usable energy (electricity/sound). Best overall: strong metaphor,
  good mouth-feel, flows in DSL as `dynamo { }`. Vintage/industrial aesthetic fits.
- **Torque** — the engine's output force. Punchy and visceral but abstract (a force, not a component).
- **Turbine** — powerful and modern but breaks piston-engine consistency (turbines are jet engines, not piston engines).
- **Crankshaft** — mechanically correct but inert. A hidden component, not evocative.
- **Manifold** — intellectually neat (many-fold convergence) but cold and triggers "exhaust" association.
  Ruled out: Output (no personality), Generator (too generic/wordy), Collector (mundane associations).
  Decision pending user feedback.

**Ignitor rename rationale (was "Combustion/Exciter"):**

- Aligns perfectly with the existing slogan "Prepare, Ignite, Refine" — the stage that ignites should be called Ignitor
- More accurate to the engine metaphor: ignition is the transformative spark, combustion is the resulting process.
  The DSL block defines the spark, not the sustained burn.
- Aesthetic pairing: Injection / Ignitor / Katalyst has I-I-K alliterative flow; all three syllables, clean endings
- Mouth-feel: "Ignitor" is crisp and energetic; "Combustor" has a muddy -bust- cluster
- Secondary resonance: "ignite creativity," "ignite sound" — carries meaning beyond the engine metaphor
- In DSL: `ignitor { }` reads better than `combustor { }` alongside `katalyst { }`

**What was ruled out:**

- **Combustor**: phonetically heavy, breaks rhythmic pairing with Katalyst, contradicts "Ignite" in the slogan
- **Exciter**: original working name; too generic, no metaphor connection
- **Distributor**: redundant with Injection — eliminated 2026-04-01

**Key structural insight:** Cylinder fills the missing architectural level. A Motor has N Cylinders. Each Cylinder
contains the full Injection -> Ignitor -> Katalyst chain. This maps 1:1 to how a real combustion engine works
(independent chambers running in parallel, feeding one output).

**Orbit -> Cylinder rename rationale:**

- Structurally accurate: cylinders are independent parallel chambers, exactly like audio buses
- Fills the gap: the other terms describe stages, Cylinder describes where stages live
- Low migration cost: "orbit" is Tidal/Strudel infrastructure plumbing, not identity-defining vocabulary
- Klang already established independent identity via Sprudel rename
- Documentation bridge is trivial: "Cylinders (called 'orbits' in Tidal Cycles)"
- Fits the committed aesthetic: Motor with umlaut, Ignitor, Injection -- industrial/engineering-flavored

**Why:** Coherent metaphor system that actually helps users understand the architecture, not just branding.

**How to apply:** Use Cylinder everywhere Orbit was used. Use Ignitor for the core sound transformation stage.
Use Fuel for voices/oscillators (the raw material). Use Injection for the event scheduler/distributor.
In documentation and UI, a parenthetical "(orbits in Tidal/Strudel)" eases the transition for users coming
from that ecosystem. Master output name TBD.
