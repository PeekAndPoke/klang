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

Registration happens at app startup in `StrudelUiTools.kt`, called from `index.kt`:

```kotlin
fun registerStrudelUiTools() {
    KlangUiToolRegistry.register("StrudelAdsrEditor", StrudelAdsrEditorTool)
    KlangUiToolRegistry.register(
        "StrudelAdsrSequenceEditor",
        StrudelMiniNotationEditorTool(atomTool = StrudelAdsrEditorTool)
    )
    KlangUiToolRegistry.register("StrudelScaleEditor", StrudelScaleEditorTool)
    KlangUiToolRegistry.register(
        "StrudelScaleSequenceEditor",
        StrudelMiniNotationEditorTool(atomTool = StrudelScaleEditorTool)
    )
    KlangUiToolRegistry.register("StrudelMiniNotationEditor", StrudelMiniNotationEditorTool())
    KlangUiToolRegistry.register("StrudelNoteEditor", StrudelNoteEditorTool)
    KlangUiToolRegistry.register("StrudelScaleDegreeEditor", StrudelScaleDegreeEditorTool)
}
```

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

## Key Files

| File                                                            | Role                                        |
|-----------------------------------------------------------------|---------------------------------------------|
| `klangui/src/jsMain/kotlin/KlangUiTool.kt`                      | Interfaces + registry                       |
| `strudel-ksp/src/main/kotlin/KDocParser.kt`                     | Parses `@param-tool` tags                   |
| `klangscript/src/commonMain/kotlin/types/KlangParam.kt`         | `uitools` field                             |
| `src/jsMain/kotlin/codemirror/ArgFinder.kt`                     | Finds arg under cursor                      |
| `src/jsMain/kotlin/codemirror/DslGoToDocsExtension.kt`          | Right-click → tool launch                   |
| `strudel/src/jsMain/kotlin/ui/StrudelUiTools.kt`                | Registration function                       |
| `src/jsMain/kotlin/index.kt`                                    | Calls `registerStrudelUiTools()` at startup |
| `strudel/src/jsMain/kotlin/ui/StrudelAdsrEditorTool.kt`         | Example: Embeddable tool                    |
| `strudel/src/jsMain/kotlin/ui/StrudelScaleEditorTool.kt`        | Example: Embeddable tool                    |
| `strudel/src/jsMain/kotlin/ui/StrudelMiniNotationEditorTool.kt` | Example: Tool with sub-tool                 |
| `strudel/src/jsMain/kotlin/ui/MnSharedPanels.kt`                | Shared: modifier panel + text input         |
| `strudel/src/jsMain/kotlin/ui/MnEditorBase.kt`                  | Shared: abstract note editor base           |
| `strudel/src/jsMain/kotlin/ui/NoteStaff.kt`                     | Shared: SVG staff rendering + drag          |
| `strudel/src/jsMain/kotlin/ui/StrudelNoteEditorTool.kt`         | `note()` editor                             |
| `strudel/src/jsMain/kotlin/ui/StrudelScaleDegreeEditorTool.kt`  | `n()` editor                                |

## Existing Tools

| Registry Name                | Class                                                   | Embeddable | Used by                 |
|------------------------------|---------------------------------------------------------|------------|-------------------------|
| `StrudelAdsrEditor`          | `StrudelAdsrEditorTool`                                 | yes        | `adsr()` param directly |
| `StrudelAdsrSequenceEditor`  | `StrudelMiniNotationEditorTool(StrudelAdsrEditorTool)`  | no         | `adsr()` pattern params |
| `StrudelScaleEditor`         | `StrudelScaleEditorTool`                                | yes        | `scale()` param         |
| `StrudelScaleSequenceEditor` | `StrudelMiniNotationEditorTool(StrudelScaleEditorTool)` | no         | sequence of scales      |
| `StrudelMiniNotationEditor`  | `StrudelMiniNotationEditorTool()`                       | no         | generic pattern params  |
| `StrudelNoteEditor`          | `StrudelNoteEditorTool`                                 | no         | `note()` param          |
| `StrudelScaleDegreeEditor`   | `StrudelScaleDegreeEditorTool`                          | no         | `n()` param             |
