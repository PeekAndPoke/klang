# UI Tools — Architecture Reference

## Interfaces

**Location:** `klangui/src/jsMain/kotlin/KlangUiTool.kt`

```kotlin
fun interface KlangUiTool {
    fun FlowContent.render(ctx: KlangUiToolContext)
}

interface KlangUiToolEmbeddable : KlangUiTool {
    fun FlowContent.renderEmbedded(ctx: KlangUiToolContext)
}
```

- **KlangUiTool**: opens in a `CodeToolModal` dialog.
- **KlangUiToolEmbeddable**: also supports inline rendering without buttons.
  `renderEmbedded` must call `onCommit()` on every live change.
- **prefersPopover** (C0.3): an embeddable tool that returns true opens as a small
  anchored POPOVER with live commits instead of a modal (the inline tier for scalar
  tools like gain/pan; `SprudelNumericEditorTool` opts in for all its instances).

## KlangUiToolContext

```kotlin
data class KlangUiToolContext(
    val symbol: KlangSymbol,
    val paramName: String,
    val currentValue: String?,   // raw source text of the argument
    val onCommit: (String) -> Unit,
    val onCancel: () -> Unit,
    val call: KlangUiToolCall?,  // whole-call view (C0.3), null when unresolvable
)
```

## Whole-call editing (C0.3 MultiParam tier)

Since the per-param DSL migration (docs/plans/filter-unification.md, C0), compound tools
edit ALL parameters of the host call instead of one colon-string argument:

```kotlin
data class KlangUiToolCall(
    val paramNames: List<String>,      // declared params, in order
    val args: List<String?>,           // current raw text per param index, null = absent
    val onCommitCall: (List<String?>) -> Unit,
)
```

`onCommitCall` rewrites the entire argument list: a contiguous prefix of provided values
serializes as positional args, anything with gaps as ALL-named args (KlangScript forbids
mixing). Tools must start from `call.args.toMutableList()`, pad with nulls, and overwrite
only the indices they manage. When `call == null` (e.g. as a sequence-atom editor) the
tool falls back to editing its single argument as one scalar (the head param).
String-typed slots (distort/tremolo shape) commit QUOTED literals; numbers commit bare.

## Registry

```kotlin
object KlangUiToolRegistry {
    fun register(name: String, tool: KlangUiTool)
    fun get(name: String): KlangUiTool?
}
```

Registration happens at app startup in `SprudelUiTools.kt`, called from `index.kt`.

## @param-tool KDoc Tag

```kotlin
/**
 * @param name The scale name.
 * @param-tool name SprudelScaleEditor
 */
fun SprudelPattern.scale(name: PatternLike? = null): SprudelPattern
```

- Parsed by KSP processor in `sprudel-ksp/src/main/kotlin/KDocParser.kt`.
- Stored in `KlangParam.uitools: List<String>`.
- Multiple tools per param: `@param-tool name ToolA, ToolB`.

## Trigger Flow

1. User right-clicks a function argument in CodeMirror.
2. `DslGoToDocsExtension` calls `ArgFinder.findCallArgAt()`.
3. That resolves `param.uitools` via `KlangUiToolRegistry.resolve()`.
4. Context menu shows "Open ToolName…" for each registered tool.
5. `CodeSongPage.openTool()` opens the tool in `CodeToolModal`.
6. On commit: `onCommit(newValue)` replaces the argument text in the editor.

## Implemented Editor Tools

All tool source files are in `sprudel/src/jsMain/kotlin/ui/`.

| File                               | Description                                                             | Tool Instances                                                                                                                                        |
|------------------------------------|-------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SprudelNumericEditorTool.kt`      | Configurable numeric editor with drag bar                               | Gain, Pan, RoomSize, DelayTime, DelayFeedback, all individual filter params (cutoff, resonance, env, attack, decay, sustain, release per filter type) |
| `SprudelAdsrEditorTool.kt`         | ADSR envelope editor with interactive SVG curve                         | `SprudelAdsrEditorTool`                                                                                                                               |
| `SprudelFilterAdsrEditorTool.kt`   | Configurable filter ADSR editor, reuses ADSR SVG                        | LP/HP/BP/Notch ADSR instances                                                                                                                         |
| `SprudelFilterEditorTool.kt`       | Whole-call filter editor (freq, q) with frequency response SVG | LP/HP/BP/Notch filter instances                                                                                                                       |
| `SprudelCompressorEditorTool.kt`   | Compressor editor with transfer function SVG + 8 presets                | `SprudelCompressorEditorTool`                                                                                                                         |
| `SprudelDelayEditorTool.kt`        | Delay editor (amount, time, feedback) with decay curve SVG                       | `SprudelDelayEditorTool`                                                                                                                              |
| `SprudelReverbEditorTool.kt`       | Reverb amount editor                                                    | `SprudelReverbEditorTool`                                                                                                                             |
| `SprudelNoteEditorTool.kt`         | Note picker with staff visualization                                    | `SprudelNoteEditorTool`                                                                                                                               |
| `SprudelScaleEditorTool.kt`        | Scale picker (root + mode)                                              | `SprudelScaleEditorTool`                                                                                                                              |
| `SprudelScaleDegreeEditorTool.kt`  | Scale degree picker                                                     | `SprudelScaleDegreeEditorTool`                                                                                                                        |
| `SprudelWaveformEditorTool.kt`     | Waveform selector (sine, saw, square, triangle, etc.)                   | `SprudelWaveformEditorTool`                                                                                                                           |
| `SprudelSampleEditorTool.kt`       | Sample browser                                                          | `SprudelSampleEditorTool`                                                                                                                             |
| `SprudelEuclidEditorTool.kt`       | Euclidean rhythm editor (pulses:steps:rotation)                         | `SprudelEuclidEditorTool`                                                                                                                             |
| `SprudelMiniNotationEditorTool.kt` | Sequence editor — wraps any atom tool for mini-notation patterns        | Generic + all sequence variants                                                                                                                       |

## Shared UI Components

| File                | Role                                      |
|---------------------|-------------------------------------------|
| `MnSharedPanels.kt` | Modifier panel + text input for MN editor |
| `MnEditorBase.kt`   | Abstract note editor base class           |
| `NoteStaff.kt`      | SVG staff rendering + drag interaction    |

## How to Add a New Tool

1. Create a tool class implementing `KlangUiTool` (or `KlangUiToolEmbeddable` for inline use).

- For simple numeric params, configure `SprudelNumericEditorTool` with min/max/step/label.
  - For complex multi-field params, create a dedicated tool file.

2. Register in `SprudelUiTools.kt`:
   ```kotlin
   registry.register("SprudelMyEditor", SprudelMyEditorTool)
   registry.register("SprudelMySequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelMyEditorTool))
   ```
3. Add `@param-tool` annotation to the DSL function KDoc:
   ```kotlin
   /** @param-tool amount SprudelMySequenceEditor */
   ```
4. The KSP processor picks it up automatically on next build.

## Info Icons & Help Text

Every UI tool surfaces help text from KDoc via (i) info icons.

### Tool-level (i)

Use `toolHeaderWithInfo(title, ctx, popupCtrl)` instead of `ui.small.header { +title }` in modal mode.
Shows the first paragraph of the function's KDoc description on hover. Only renders the icon when
a description exists.

```kotlin
private val infoPopup = HoverPopupCtrl(popups)
// ...
toolHeaderWithInfo("Delay", props.toolCtx, infoPopup)
```

### Per-field (i) for single-param tools

Use `paramInfoIcon(paramName, ctx, popupCtrl)` next to the field label. Data comes from `@param` KDoc.

```kotlin
label {
    +cfg.fieldLabel
    paramInfoIcon(props.toolCtx.paramName, props.toolCtx, infoPopup)
}
```

### Per-param (i) icons in whole-call tools

Whole-call tools label each field with `paramInfoIcon(paramName, ctx, popupCtrl)` — the
description comes from the parameter's own `@param` KDoc (the helper takes the first
NON-BLANK description across the symbol's variants). There is no separate sub-field
documentation mechanism any more: `@param-sub` and `KlangParam.subFields` were deleted in
C0.3 together with the compound colon-strings they described.

Both helpers only render when description text exists (no empty popups).
All helpers live in `sprudel/src/jsMain/kotlin/ui/KlangToolInfoHelpers.kt`.


## Key Files

| File                                                    | Role                                        |
|---------------------------------------------------------|---------------------------------------------|
| `klangui/src/jsMain/kotlin/KlangUiTool.kt`              | Interfaces + registry                       |
| `klangscript/src/commonMain/kotlin/types/KlangParam.kt` | `uitools` field                             |
| `sprudel/src/jsMain/kotlin/ui/KlangToolInfoHelpers.kt`  | Info icon helpers + `HoverPopupCtrl`        |
| `src/jsMain/kotlin/codemirror/ArgFinder.kt`             | Finds arg under cursor                      |
| `src/jsMain/kotlin/codemirror/DslGoToDocsExtension.kt`  | Right-click → tool launch                   |
| `sprudel/src/jsMain/kotlin/ui/SprudelUiTools.kt`        | Registration function                       |
| `src/jsMain/kotlin/index.kt`                            | Calls `registerSprudelUiTools()` at startup |
