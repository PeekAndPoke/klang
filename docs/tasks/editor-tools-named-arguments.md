# Editor tools: named arguments resolve the wrong slot

Opened 2026-09-07 during the field-accessor batch E review (`docs/tasks-archive/2026-09/20260907-sprudel-field-accessors.md`).
Maintainer decision the same day: record here, do not mix into batch E; the editor tools get a
rework of their own.

## The finding

The `@param-tool` lookup that opens an editor on the argument under the cursor resolves the
parameter by POSITION and ignores the argument's name:

- `klangscript/src/commonMain/kotlin/ast/AstIndex.kt` `callArgAt` returns a positional
  `argIndex` and drops `Argument.Named.name` (the AST carries it, `ast/Ast.kt` `Argument.Named`).
- `klangscript-ui/src/jsMain/kotlin/codemirror/ArgFinder.kt` `resolveParam(callable, argIndex)`
  indexes `callable.params` by that position, in both the AST path and the text-scanner fallback.

Before batch E every knob had a door of its own with the tool on parameter 0, so position and
name agreed. Since batch E the compound effects are objects with named slots, and a tail slot is
reachable ONLY by name: `room(size = 4)`, `delay(time = 0.25)`, `delay(feedback = 0.4)`,
`tremolo(sync = 4)`. The shipped tutorials use exactly these (`tut_SpaceAndDirt.kt`). Batch F
(2026-09-07, the filters) added the worse shape: a SKIPPED middle slot shifts every later argument, so
in `lpf(freq = 400, env = 36).lpf(attack = 0.001, decay = 0.15, sustain = 0, release = 0.1)`
(`tut_TheFilterEnvelope.kt`) `env = 36` resolves to `q` and opens the resonance editor on a depth in
semitones, and `decay = 0.15` on the second call resolves to `q` as well.

Failure: cursor on the `4` in `.room(size = 4)` resolves argument 0, the `wet` parameter, whose
tool is `SprudelReverbEditor` (a 0..1 send editor) opened on a 0..10 size. `.delay(time = ...)`
and `.delay(feedback = ...)` both open `SprudelDelayEditor` (the wet editor); `tremolo(sync = 4)`
opens `SprudelTremoloEditor` (the 0..1 depth editor) on a rate in Hz. The scalar and sequence
paths are affected the same way (an atom inside `.room(size = "1 2 4")` gets the send sequence
editor).

What still holds (verified in the review): the whole-call modal path is name-aware
(`alignArgsToParams` / `serializeCallArgs` in `ArgFinder.kt`), and every tool's `put()` refuses to
write an untouched absent slot, so the source is not corrupted. The damage is the wrong editor
identity and range, the wrong `paramName` handed to the tool, and the confusion for the user.

Text-scanner fallback detail: the fallback's argument range covers `size = 4` including the
name, so a tool that rewrites `argFrom..argTo` with a bare value would drop the name. The AST
path uses the cursor node range (the value only), so only the fallback has this second problem.

## Also open on the tools, found in the same review

- The whole-envelope editors `SprudelLpAdsrEditor`, `SprudelHpAdsrEditor`, `SprudelBpAdsrEditor`,
  `SprudelNfAdsrEditor` (and their sequence twins) read and write argument slots 0 to 3 of the call
  they sit on (`SprudelFilterAdsrEditorTool.kt`), the shape of the retired `lpadsr(a, d, s, r)`.
  Bound to the `attack` slot of `lpf(freq, q, passes, env, attack, ...)` they would show the cutoff
  as the attack and write a dragged attack into `freq`, so since batch F they are bound to nothing
  and registered for nothing; `SprudelNotchQEditor` and `SprudelNotchFreq*` are unwired the same
  way. The rework binds them by `paramNames.indexOf("attack")` or retires them.

- The delay editor has no control for the `cap` slot; the phaser editor has no control for
  `floor` (`docs/tasks/sprudel-ui-tools.md` table).
- The tremolo `sync` slot is an LFO rate in Hz (`TremoloRenderer.kt`, `lang_effects_modulation.kt`
  `@param sync`); any tool or doc that presents it as cycles per cycle is wrong.
- Compound slots carry no aliases any more; the alias columns in `sprudel-ui-tools.md` were
  collapsed accordingly.

## Sketch of the fix (not started)

1. `CallExpressionAtResult` gets `argName: String?` (from `Argument.Named`).
2. A pure `KlangCallable.paramForArgument(argIndex, argName)`: by name when named, else by
   position with the vararg fallback; unit-tested in klangscript.
3. `ArgFinder` uses it in both paths; `paramIndex` becomes the index of the resolved parameter;
   the text fallback strips a `name =` prefix from the range so the tool edits the value only.
4. Tests: `AstCallFinderTest` for `argName`; a spec on the resolution helper; by-hand check of
   the four tutorial call sites above in the live editor.

Do this as part of the editor-tools rework, together with the missing `cap` and `floor`
controls, so the tools are designed once for named-slot compounds rather than patched.
