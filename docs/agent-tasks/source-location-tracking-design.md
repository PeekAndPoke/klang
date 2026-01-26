# Source Location Tracking Architecture

**Date:** 2026-01-19
**Status:** Design Approved - Ready for Implementation

## Problem Statement

We want to implement live code highlighting in the Strudel live-editor (similar to strudel.cc), where the currently
playing sounds and notes are highlighted in the code editor as the music plays.

### Requirements

1. **Track source locations** from KlangScript code to audio events
2. **Works in KlangScript** - strings parsed as mini-notation
3. **Works in Kotlin DSL** - direct Kotlin code (future)
4. **Multiple editors** - support concurrent executions without interference
5. **Extensible** - prepare for Kotlin Notebooks and IntelliJ plugin
6. **Granular highlighting** - highlight individual atoms within mini-notation strings

### Example

```javascript
// KlangScript code at line 5, column 1:
sound("bd hh sd oh")
```

When the "bd" plays → highlight "bd" in the editor
When the "hh" plays → highlight "hh" in the editor
etc.

---

## Architectural Overview

### Core Flow

```
KlangScript Source
    ↓
Parser (with SourceLocation tracking)
    ↓
Interpreter executes with ExecutionContext
    ↓
Native function called with SourceLocation
    ↓
Strudel pattern created with location
    ↓
Mini-notation parsed with base location
    ↓
Individual atoms get specific locations
    ↓
StrudelPatternEvent emitted with SourceLocationChain
    ↓
StrudelPlayback callback with location
    ↓
Frontend highlights CodeMirror range
```

---

## Key Design Decisions

### 1. Explicit Context Passing (Not Singletons)

**Decision:** Pass `ExecutionContext` explicitly through the interpreter rather than using ThreadLocal or global
singletons.

**Rationale:**

- Avoids ThreadLocal issues with Kotlin coroutines (thread switching)
- Supports multiple concurrent editor executions safely
- Clear ownership and lifecycle (context lives for duration of `execute()` call)
- Single-threaded assumption per execution is simple and correct
- Testable and predictable

**Rejected Alternatives:**

- ❌ ThreadLocal singleton - breaks with coroutines
- ❌ Coroutine context - requires all functions to be suspend
- ❌ Wrapper pattern - performance overhead

### 2. Source Location Chain (Not Single Location)

**Decision:** Use `SourceLocationChain` to track the transformation path from call site to individual atoms.

**Rationale:**

- `sound("bd hh")` has multiple locations:
    - Location of `sound()` call: line 5, column 1
    - Location of "bd" within string: line 5, column 7
    - Location of "hh" within string: line 5, column 10
- Chain preserves entire path for debugging
- Frontend can choose which location to highlight (most specific = first)

**Structure:**

```kotlin
data class SourceLocationChain(
    val locations: List<SourceLocation>  // Most specific first
)
```

### 3. Mini-Notation Parser Cache Removal

**Decision:** Remove the cache from `MiniNotationParser`.

**Rationale:**

- Cache key `(input, factoryHash)` doesn't include source location
- Same string at different locations would return cached pattern with wrong location
- Parsing only happens once per compilation (acceptable performance)
- Simpler implementation without cache invalidation logic

### 4. Token Position Tracking

**Decision:** Track character offsets in mini-notation tokens.

**Rationale:**

- Enables calculation of atom-specific locations within strings
- Required for granular highlighting ("bd" vs "hh" vs "sd")
- Minimal overhead (just two integers per token)

**Implementation:**

```kotlin
data class Token(
    val type: TokenType,
    val text: String,
    val startPos: Int,  // Character offset in input string
    val endPos: Int
)
```

### 5. Pattern Location Method (Not Wrapper)

**Decision:** Add `withSourceLocation(location: SourceLocation?): StrudelPattern` method to interface.

**Rationale:**

- Each pattern type controls its own location storage
- No delegation overhead (wrapper pattern would delegate every method)
- Flexible: patterns can propagate, override, or ignore locations
- Gradual implementation (default implementation returns `this`)

**Rejected Alternative:**

- ❌ `LocationTrackedPattern` wrapper - adds delegation overhead for every pattern

### 6. Native Function Signature Change

**Decision:** Update native functions to receive `SourceLocation?` as second parameter.

**Current:**

```kotlin
data class NativeFunctionValue(
    val name: String,
    val function: (List<RuntimeValue>) -> RuntimeValue,
)
```

**New:**

```kotlin
data class NativeFunctionValue(
    val name: String,
    val function: (List<RuntimeValue>, SourceLocation?) -> RuntimeValue,
)
```

**Rationale:**

- Explicit - no hidden context lookup
- Each native function can access call site location directly
- Backwards compatible via wrapper functions

---

## Implementation Plan

### Phase 1: KlangScript Infrastructure (Core)

#### 1.1 Create ExecutionContext

**File:** `klangscript/src/commonMain/kotlin/runtime/ExecutionContext.kt`

```kotlin
/**
 * Execution context for a KlangScript execution.
 * Carries metadata about the current execution.
 */
data class ExecutionContext(
    /** Source file/module name */
    val sourceName: String?,
    /** Current call site location (updated during execution) */
    var currentLocation: SourceLocation? = null,
    // Future: notebook cell info, etc.
)
```

#### 1.2 Update NativeFunctionValue Signature

**File:** `klangscript/src/commonMain/kotlin/runtime/RuntimeValue.kt`

- Add `SourceLocation?` parameter to function signature
- Update all existing native function registrations

#### 1.3 Update BoundNativeMethod Signature

**File:** `klangscript/src/commonMain/kotlin/runtime/RuntimeValue.kt`

- Add `SourceLocation?` parameter to invoker signature

#### 1.4 Update Interpreter Constructor

**File:** `klangscript/src/commonMain/kotlin/runtime/Interpreter.kt`

- Add `executionContext: ExecutionContext` as required parameter
- Update all `Interpreter` instantiations to pass context

#### 1.5 Update Interpreter.evaluateCall

**File:** `klangscript/src/commonMain/kotlin/runtime/Interpreter.kt`

- Pass `call.location` to native functions
- Pass `call.location` to bound native methods
- Share context with nested interpreters (script functions)

#### 1.6 Update KlangScriptEngine.execute

**File:** `klangscript/src/commonMain/kotlin/KlangScriptEngine.kt`

- Create `ExecutionContext` with source name
- Pass context to `Interpreter` constructor

#### 1.7 Add Backwards-Compatible Registration Helpers

**File:** `klangscript/src/commonMain/kotlin/builder/KlangScriptExtensionBuilder.kt`

```kotlin
// Existing - no location
fun registerFunction(name: String, handler: (List<RuntimeValue>) -> RuntimeValue) {
    registerFunctionWithLocation(name) { args, _ -> handler(args) }
}

// New - with location
fun registerFunctionWithLocation(
    name: String,
    handler: (List<RuntimeValue>, SourceLocation?) -> RuntimeValue
)
```

### Phase 2: Strudel Location Tracking

#### 2.1 Create SourceLocationChain

**File:** `klangscript/src/commonMain/kotlin/ast/Ast.kt` (or new file)

```kotlin
data class SourceLocationChain(
    val locations: List<SourceLocation>
) {
    companion object {
        fun single(location: SourceLocation) = SourceLocationChain(listOf(location))
        fun empty() = SourceLocationChain(emptyList())
    }

    fun with(location: SourceLocation?) =
        if (location != null) SourceLocationChain(listOf(location) + locations)
        else this

    val primary: SourceLocation? get() = locations.firstOrNull()
}
```

#### 2.2 Extend StrudelPatternEvent

**File:** `strudel/src/commonMain/kotlin/StrudelPatternEvent.kt`

```kotlin
data class StrudelPatternEvent(
    val begin: Rational,
    val end: Rational,
    val dur: Rational,
    val data: VoiceData,
    val sourceLocations: SourceLocationChain = SourceLocationChain.empty(),
)
```

#### 2.3 Add withSourceLocation to StrudelPattern Interface

**File:** `strudel/src/commonMain/kotlin/StrudelPattern.kt`

```kotlin
interface StrudelPattern {
    // ... existing members ...

    /**
     * Returns a copy of this pattern with the given source location attached.
     * Default implementation returns this unchanged.
     */
    fun withSourceLocation(location: SourceLocation?): StrudelPattern = this
}
```

#### 2.4 Update Strudel Bridge

**File:** `strudel/src/commonMain/kotlin/lang/KlangScriptStrudelLib.kt`

```kotlin
fun KlangScriptExtensionBuilder.registerStrudelDsl() {
    initStrudelLang()

    // Register functions with location wrapping
    StrudelRegistry.functions.forEach { (name, handler) ->
        registerVarargFunctionWithLocation(name) { args, location ->
            val result = handler(args)
            result.withSourceLocation(location)
        }
    }

    // Register pattern extension methods with location
    registerType<StrudelPattern> {
        StrudelRegistry.patternExtensionMethods.forEach { (name, handler) ->
            registerVarargMethodWithLocation(name) { args, location ->
                val result = handler(this, args)
                result.withSourceLocation(location)
            }
        }
    }

    // Register string extension methods with location
    registerType<StringValue> {
        StrudelRegistry.stringExtensionMethods.forEach { (name, handler) ->
            registerVarargMethodWithLocation(name) { args, location ->
                val result = handler(value, args)
                result.withSourceLocation(location)
            }
        }
    }
}
```

### Phase 3: Mini-Notation Parser Updates

#### 3.1 Remove Cache from MiniNotationParser

**File:** `strudel/src/commonMain/kotlin/lang/parser/MiniNotationParser.kt`

- Remove `companion object` with cache
- Remove cache key and lookup logic
- `parse()` directly calls `parseInternal()`

#### 3.2 Add Position Tracking to Tokens

**File:** `strudel/src/commonMain/kotlin/lang/parser/MiniNotationParser.kt`

```kotlin
private data class Token(
    val type: TokenType,
    val text: String,
    val startPos: Int,
    val endPos: Int
)

private fun tokenize(input: String): List<Token> {
    // Track character positions when creating tokens
    // ...
}
```

#### 3.3 Add baseLocation Parameter

**File:** `strudel/src/commonMain/kotlin/lang/parser/MiniNotationParser.kt`

```kotlin
fun parseMiniNotation(
    input: String,
    atomFactory: (String) -> StrudelPattern,
    baseLocation: SourceLocation? = null
): StrudelPattern

class MiniNotationParser(
    private val input: String,
    private val atomFactory: (String) -> StrudelPattern,
    private val baseLocation: SourceLocation? = null
) {
    // When creating atoms, calculate their location:
    private fun createAtomWithLocation(text: String, token: Token): StrudelPattern {
        val atom = atomFactory(text)

        val atomLocation = baseLocation?.let { base ->
            SourceLocation(
                source = base.source,
                line = base.line,
                column = base.column + token.startPos
            )
        }

        return atom.withSourceLocation(atomLocation)
    }
}
```

#### 3.4 Update String Extension Parsing

**File:** `strudel/src/commonMain/kotlin/lang/lang_helpers.kt`

```kotlin
class DslStringExtensionProvider(...) {
    private fun parse(str: String): StrudelPattern {
        // Get location from current execution context (set by interpreter)
        val baseLocation = // TODO: How to get this?

            return parseMiniNotation(
                input = str,
                atomFactory = { AtomicPattern(VoiceData.empty.defaultModifier(it)) },
                baseLocation = baseLocation
            )
    }
}
```

**Note:** Need to solve how to get location in string extension. Options:

- Option A: String literal location is available in KlangScript AST (need to verify)
- Option B: Pass as part of ExecutionContext
- Option C: Store in CallExpression and make accessible

### Phase 4: Implement withSourceLocation in Patterns

Implement `withSourceLocation()` in pattern types, starting with:

#### 4.1 AtomicPattern

```kotlin
data class AtomicPattern(
    val voiceData: VoiceData,
    private val sourceLocation: SourceLocation? = null
) : StrudelPattern.FixedWeight {

    override fun withSourceLocation(location: SourceLocation?): StrudelPattern {
        return if (this.sourceLocation == null && location != null) {
            copy(sourceLocation = location)
        } else {
            this
        }
    }

    override fun queryArcContextual(...): List<StrudelPatternEvent> {
        return listOf(
            StrudelPatternEvent(
                ...,
            sourceLocations = SourceLocationChain.single(sourceLocation)
        )
        )
    }
}
```

#### 4.2 Other Pattern Types

- **SequencePattern** - propagate to children or set on container
- **StackPattern** - similar to SequencePattern
- **ControlPattern** - propagate to source pattern
- **EmptyPattern** - ignore (no events)
- **WeightedPattern** - propagate to inner pattern
- etc.

Strategy: Most transformations propagate to inner/child patterns.

### Phase 5: Frontend Integration

#### 5.1 Add Location Callback to StrudelPlayback

**File:** `strudel/src/commonMain/kotlin/StrudelPlayback.kt`

```kotlin
class StrudelPlayback(
    // ... existing parameters ...
    private val onEventScheduled: ((StrudelPatternEvent) -> Unit)? = null
) {
    private fun requestNextCyclesAndAdvanceCursor() {
        // ... existing code ...

        for (voice in events) {
            // Notify callback about event location
            onEventScheduled?.invoke(voice)

            // Schedule the voice
            control.send(...)
        }
    }
}
```

#### 5.2 Connect to CodeMirror

**File:** `src/jsMain/kotlin/pages/DashboardPage.kt`

- Subscribe to event location callbacks
- Highlight ranges in CodeMirror based on `SourceLocationChain`
- Clear highlights when events end

```kotlin
val playback = player.playStrudel(pattern, onEventScheduled = { event ->
    event.sourceLocations.primary?.let { location ->
        highlightRange(location.line, location.column, ...)
    }
})
```

---

## Future Extensions

### Kotlin DSL Support

**Challenge:** Kotlin code doesn't have runtime source location tracking.

**Options:**

1. Compiler plugin to inject location metadata
2. Generate KlangScript from Kotlin DSL with source maps
3. Manual annotations (user specifies locations)
4. IntelliJ plugin uses PSI to track locations

### Kotlin Notebooks

**Advantage:** Notebooks provide cell metadata naturally.

**Implementation:**

- Add `notebookCell: String?` to `ExecutionContext`
- Notebook sets context before executing cell
- Locations include cell ID + line within cell
- Notebook UI highlights based on cell + location

### IntelliJ Plugin

- Use PSI (Program Structure Interface) to get source locations
- Inject into runtime somehow (TBD)
- Highlight directly in IntelliJ editor

---

## Testing Strategy

### Unit Tests

1. **ExecutionContext threading** - verify multiple concurrent executions don't interfere
2. **Location propagation** - verify locations flow through pattern transformations
3. **Mini-notation parsing** - verify atom positions are calculated correctly
4. **Source location chain** - verify chain building and primary location selection

### Integration Tests

1. **End-to-end KlangScript** - execute code and verify events have correct locations
2. **Multiple editors** - run two scripts simultaneously, verify no interference
3. **Nested patterns** - verify complex pattern hierarchies preserve locations

### Manual Testing

1. **Live highlighting** - verify visual feedback in browser
2. **Complex patterns** - test with realistic Strudel patterns
3. **Performance** - verify no noticeable overhead

---

## Open Questions

### Q1: String Literal Locations in KlangScript AST

**Question:** When we have `sound("bd hh")`, does the `CallExpression` AST node give us access to the string literal's
`SourceLocation` separately from the call location?

**Current:** We have `call.location` (the entire call expression)
**Need:** Location of the string literal argument

**Resolution Options:**

- A: `StringLiteral` expression has its own location (ideal)
- B: Calculate from call location + argument position (fragile)
- C: Store in ExecutionContext when evaluating arguments (complex)

**Action:** Check AST structure for `CallExpression` arguments.

### Q2: Multi-line String Support

**Question:** Should we support multi-line mini-notation strings?

```javascript
sound("
bd
hh
sd
oh
")
```

**Decision:** Start with single-line only, add multi-line later if needed.

### Q3: Location Granularity for UI

**Question:** What should be highlighted when `bd` plays?

- Option A: Entire `sound("bd hh")` call
- Option B: Just the string `"bd hh"`
- Option C: Just the atom `bd`

**Decision:** Option C (most specific) - highlight individual atoms.
**Fallback:** If atom location unavailable, fall back to string or call location.

---

## Implementation Checklist

### KlangScript Core

- [ ] Create `ExecutionContext` class
- [ ] Update `NativeFunctionValue` signature
- [ ] Update `BoundNativeMethod` signature
- [ ] Add `executionContext` to `Interpreter` constructor
- [ ] Update `Interpreter.evaluateCall` to pass location
- [ ] Update `KlangScriptEngine.execute` to create context
- [ ] Add `registerFunctionWithLocation` helper
- [ ] Update all existing native function registrations

### Strudel Core

- [ ] Create `SourceLocationChain` class
- [ ] Add `sourceLocations` to `StrudelPatternEvent`
- [ ] Add `withSourceLocation` to `StrudelPattern` interface
- [ ] Update Strudel bridge to wrap patterns with location

### Mini-Notation Parser

- [ ] Remove cache from `MiniNotationParser`
- [ ] Add position tracking to `Token` class
- [ ] Update `tokenize()` to track positions
- [ ] Add `baseLocation` parameter to `parseMiniNotation`
- [ ] Implement `createAtomWithLocation` helper
- [ ] Update string extension parsing to pass base location

### Pattern Implementations

- [ ] Implement `withSourceLocation` in `AtomicPattern`
- [ ] Implement `withSourceLocation` in `SequencePattern`
- [ ] Implement `withSourceLocation` in `StackPattern`
- [ ] Implement `withSourceLocation` in `ControlPattern`
- [ ] Implement `withSourceLocation` in other pattern types

### Frontend

- [ ] Add `onEventScheduled` callback to `StrudelPlayback`
- [ ] Implement CodeMirror range highlighting
- [ ] Handle highlight clearing/timing
- [ ] Test with real patterns

---

## References

- Original strudel.cc implementation: https://strudel.cc
- CodeMirror documentation: https://codemirror.net/
- KlangScript parser: `klangscript/src/commonMain/kotlin/parser/KlangScriptParser.kt`
- Mini-notation parser: `strudel/src/commonMain/kotlin/lang/parser/MiniNotationParser.kt`

---

## Notes for Next Session

**Current Status:** Design complete, ready to start implementation.

**Start With:**

1. Create `ExecutionContext` class
2. Update `NativeFunctionValue` signature
3. Modify `Interpreter` to accept and use context

**Critical Path:** The KlangScript infrastructure changes are blocking everything else. Once those are done, Strudel
changes can proceed in parallel with pattern implementations.

**Watch Out For:**

- Need to resolve Q1 (string literal locations) early
- Signature changes will break existing code - need to update all registrations
- Testing multi-editor scenarios early will catch context isolation bugs
