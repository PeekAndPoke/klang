# Fix: Completion shows wrong library in member-access context

## Bug

When typing `Osc.sine().ad|` inside a register() call with both stdlib and sprudel imported,
completion shows `add - sprudel` and `adsr - sprudel` instead of `adsr` from stdlib's IgnitorDsl.

Hovering the fully-typed `.adsr` correctly shows stdlib IgnitorDsl docs. Only completion is wrong.

## Root Cause Analysis (Six Hats, 2026-04-03)

### The evidence

- Completion detail text shows "sprudel" without a receiver type prefix
- `memberCompletions()` always produces detail like "ReceiverType . library"
- `topLevelCompletions()` produces detail like "category . library" — which is "sprudel" when category is empty
- Therefore: **the code is taking the top-level completion path, not the member-access path**

### Why `isMemberAccess` is false

The current detection is a character-peeking heuristic:

```kotlin
val charBefore = context.state.doc.sliceString(from - 1, from)  // char before word start
val isMemberAccess = charBefore == "."
```

`from` comes from `context.matchBefore(js("/\\w*/"))`. The regex `\w*` can match zero characters,
and CodeMirror's `matchBefore` may return unexpected boundaries. If `from` is at the cursor position
rather than the start of the typed word, `charBefore` would be the last typed character ("d"),
not the dot.

### Why hover works

`DslEditorExtension.wordDocAt()` uses **AST-based detection**: it finds the node at the cursor
position, walks up to find a `MemberAccess` parent, and resolves the receiver type from
`memberAccess.obj`. This doesn't depend on any character-peeking heuristic.

## Proposed Fix

### Replace character heuristic with AST-based member-access detection

After `processCodeImmediate()` builds a fresh `AnalyzedAst` from the current editor text:

1. Find the AST node at the cursor position using `analysis.astIndex.nodeAt(cursorFrom)`
2. Check if the cursor is on the property of a `MemberAccess` node (node itself or parent)
3. If yes, get the receiver type from `analysis.typeOf(memberAccess.obj)` — this is the same
   approach that hover uses, and hover works correctly
4. Fall back to the `charBefore == "."` heuristic only when the AST isn't available (parse error)

```kotlin
private fun findMemberAccessContext(docContext: EditorDocContext, cursorFrom: Int): KlangType? {
    val analysis = docContext.lastAnalysis ?: return null
    val node = analysis.astIndex.nodeAt(cursorFrom) ?: return null

    val memberAccess = when {
        node is MemberAccess -> node
        else -> {
            val parent = analysis.astIndex.parentOf(node)
            if (parent is MemberAccess) parent else null
        }
    } ?: return null

    return analysis.typeOf(memberAccess.obj)
}
```

### Completion flow after fix

```
User types Osc.sine().ad|
  → processCodeImmediate(currentCode)  // fresh parse, ".ad" is a MemberAccess
  → findMemberAccessContext(from)
    → nodeAt(from) finds Identifier("ad") or MemberAccess node
    → parent is MemberAccess(obj=CallExpression(Osc.sine()), property="ad")
    → typeOf(Osc.sine()) = IgnitorDsl
  → memberCompletions(IgnitorDsl, "ad")
  → returns stdlib's adsr
```

### Also needed: remove duplicate autocompletion

`basicSetup` from CodeMirror includes its own `autocompletion()` extension. The code adds a second
`autocompletion(override = ...)`. Having two autocompletion facets may cause CodeMirror to run both
sources. Either:

- Use `basicSetup({ autocompletion: false })` to disable the built-in one, OR
- Verify that the `override` array properly replaces all sources

## Files to modify

| File                                                      | Change                                               |
|-----------------------------------------------------------|------------------------------------------------------|
| `klangscript-ui/.../DslCompletionSource.kt`               | Replace `charBefore == "."` with AST-based detection |
| `klangscript-ui/.../CodeMirrorComp.kt`                    | Potentially disable basicSetup's autocompletion      |
| `klangscript/src/jvmTest/kotlin/intel/AnalyzedAstTest.kt` | Tests for AST-based member detection                 |

## Diagnostic step (confirm before fixing)

Add `console.log` to `completeDsl` to verify the hypothesis:

```kotlin
console.log("COMPLETION: from=$from to=$to typed='$typed' charBefore='$charBefore' isMemberAccess=$isMemberAccess")
```

If `isMemberAccess` is false when it should be true, the root cause is confirmed.
If `isMemberAccess` is true but `receiverType` is wrong, the issue is elsewhere.

## Test plan

1. Add `console.log`, rebuild, check browser console to confirm root cause
2. Implement AST-based detection
3. Write JVM test: parse `Osc.sine().ad` → nodeAt cursor pos → find MemberAccess → typeOf receiver = IgnitorDsl
4. Write JVM test: full flow with multi-lib registry → memberCompletions returns stdlib adsr
5. Test in browser with the exact user scenario
