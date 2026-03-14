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

## KlangUiToolContext

```kotlin
data class KlangUiToolContext(
    val symbol: KlangSymbol,
    val paramName: String,
    val currentValue: String?,   // raw source text of the argument
    val onCommit: (String) -> Unit,
    val onCancel: () -> Unit,
)
```

## Registry

```kotlin
object KlangUiToolRegistry {
    fun register(name: String, tool: KlangUiTool)
    fun get(name: String): KlangUiTool?
}
```

Registration happens at app startup in `StrudelUiTools.kt`, called from `index.kt`.

## @param-tool KDoc Tag

```kotlin
/**
 * @param name The scale name.
 * @param-tool name StrudelScaleEditor
 */
fun StrudelPattern.scale(name: PatternLike? = null): StrudelPattern
```

- Parsed by KSP processor in `strudel-ksp/src/main/kotlin/KDocParser.kt`.
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

All tool source files are in `strudel/src/jsMain/kotlin/ui/`.

| File                               | Description                                                             | Tool Instances                                                                                                                                        |
|------------------------------------|-------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `StrudelNumericEditorTool.kt`      | Configurable numeric editor with drag bar                               | Gain, Pan, RoomSize, DelayTime, DelayFeedback, all individual filter params (cutoff, resonance, env, attack, decay, sustain, release per filter type) |
| `StrudelAdsrEditorTool.kt`         | ADSR envelope editor with interactive SVG curve                         | `StrudelAdsrEditorTool`                                                                                                                               |
| `StrudelFilterAdsrEditorTool.kt`   | Configurable filter ADSR editor, reuses ADSR SVG                        | LP/HP/BP/Notch ADSR instances                                                                                                                         |
| `StrudelFilterEditorTool.kt`       | Combined filter editor (freq:resonance:env) with frequency response SVG | LP/HP/BP/Notch filter instances                                                                                                                       |
| `StrudelCompressorEditorTool.kt`   | Compressor editor with transfer function SVG + 8 presets                | `StrudelCompressorEditorTool`                                                                                                                         |
| `StrudelDelayEditorTool.kt`        | Delay editor (time:feedback) with decay curve SVG                       | `StrudelDelayEditorTool`                                                                                                                              |
| `StrudelReverbEditorTool.kt`       | Reverb amount editor                                                    | `StrudelReverbEditorTool`                                                                                                                             |
| `StrudelNoteEditorTool.kt`         | Note picker with staff visualization                                    | `StrudelNoteEditorTool`                                                                                                                               |
| `StrudelScaleEditorTool.kt`        | Scale picker (root + mode)                                              | `StrudelScaleEditorTool`                                                                                                                              |
| `StrudelScaleDegreeEditorTool.kt`  | Scale degree picker                                                     | `StrudelScaleDegreeEditorTool`                                                                                                                        |
| `StrudelWaveformEditorTool.kt`     | Waveform selector (sine, saw, square, triangle, etc.)                   | `StrudelWaveformEditorTool`                                                                                                                           |
| `StrudelSampleEditorTool.kt`       | Sample browser                                                          | `StrudelSampleEditorTool`                                                                                                                             |
| `StrudelEuclidEditorTool.kt`       | Euclidean rhythm editor (pulses:steps:rotation)                         | `StrudelEuclidEditorTool`                                                                                                                             |
| `StrudelMiniNotationEditorTool.kt` | Sequence editor — wraps any atom tool for mini-notation patterns        | Generic + all sequence variants                                                                                                                       |

## Shared UI Components

| File                | Role                                      |
|---------------------|-------------------------------------------|
| `MnSharedPanels.kt` | Modifier panel + text input for MN editor |
| `MnEditorBase.kt`   | Abstract note editor base class           |
| `NoteStaff.kt`      | SVG staff rendering + drag interaction    |

## How to Add a New Tool

1. Create a tool class implementing `KlangUiTool` (or `KlangUiToolEmbeddable` for inline use).
  - For simple numeric params, configure `StrudelNumericEditorTool` with min/max/step/label.
  - For complex multi-field params, create a dedicated tool file.
2. Register in `StrudelUiTools.kt`:
   ```kotlin
   registry.register("StrudelMyEditor", StrudelMyEditorTool)
   registry.register("StrudelMySequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelMyEditorTool))
   ```
3. Add `@param-tool` annotation to the DSL function KDoc:
   ```kotlin
   /** @param-tool amount StrudelMySequenceEditor */
   ```
4. The KSP processor picks it up automatically on next build.

## Key Files

| File                                                    | Role                                        |
|---------------------------------------------------------|---------------------------------------------|
| `klangui/src/jsMain/kotlin/KlangUiTool.kt`              | Interfaces + registry                       |
| `strudel-ksp/src/main/kotlin/KDocParser.kt`             | Parses `@param-tool` tags                   |
| `klangscript/src/commonMain/kotlin/types/KlangParam.kt` | `uitools` field                             |
| `src/jsMain/kotlin/codemirror/ArgFinder.kt`             | Finds arg under cursor                      |
| `src/jsMain/kotlin/codemirror/DslGoToDocsExtension.kt`  | Right-click → tool launch                   |
| `strudel/src/jsMain/kotlin/ui/StrudelUiTools.kt`        | Registration function                       |
| `src/jsMain/kotlin/index.kt`                            | Calls `registerStrudelUiTools()` at startup |
