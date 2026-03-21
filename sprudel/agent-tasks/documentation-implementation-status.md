# Strudel DSL Documentation - Implementation Status

**Date**: 2026-02-21

## Functions that need docs and examples

- adsr() and it four separate functions
-

**Date**: 2026-02-19

## ✅ Completed

### 1. Documentation Model (KlangScript)

**Location**: `klangscript/src/commonMain/kotlin/docs/DslDocs.kt`

- ✅ Created general-purpose documentation model in KlangScript (not Strudel-specific)
- ✅ Data classes: `FunctionDoc`, `VariantDoc`, `ParamDoc`, `DslType`
- ✅ `DslDocsRegistry` - centralized registry for all libraries
- ✅ Search and filtering capabilities built-in

**Key Design Decisions**:

- Made it part of KlangScript so ANY library can register docs
- Registry pattern allows multiple libraries to contribute documentation
- Type-safe data structures that can be serialized to JSON

### 2. Strudel Documentation Registration

**Location**: `strudel/src/commonMain/kotlin/lang/docs/StrudelDocs.kt`

- ✅ Created `registerStrudelDocs()` function
- ✅ Documented `seq()` function with all 3 variants
- ✅ Integrated with `initStrudelLang()` for automatic registration
- ✅ Comprehensive tests in `StrudelDocsSpec.kt` (9 tests, all passing)

**Example Registration**:

```kotlin
"seq" to FunctionDoc(
    name = "seq",
    category = "structural",
    tags = listOf("sequence", "timing", "control", "order"),
    library = "strudel",
    variants = listOf(
        // TOP_LEVEL, EXTENSION_METHOD variants with full docs
    )
)
```

### 3. seq() Implementation Pattern

**Location**: `strudel/src/commonMain/kotlin/lang/lang_structural.kt`

- ✅ Private delegates with underscore prefix (`_seq`)
- ✅ Delegates register as `seq` in KlangScript (trimStart('_'))
- ✅ User-facing typed overloads with comprehensive KDoc
- ✅ All tests passing (LangSeqSpec.kt - 11 tests)

**Pattern Established**:

```kotlin
// Private delegate for KlangScript
private val _seq by dslFunction { args, callInfo ->
    applySeq(args.toListOfPatterns())
}

// User-facing typed overload with KDoc
/**
 * Creates a sequence pattern that plays patterns one after another.
 * @param patterns Patterns to play in sequence
 * @return A sequential pattern
 * @sample seq("c d e", "f g a")
 * @category structural
 * @tags sequence, timing
 */
@StrudelDsl
fun seq(vararg patterns: PatternLike): StrudelPattern {
    return _seq(patterns.toList())
}
```

### 4. Frontend Documentation Page

**Location**: `src/jsMain/kotlin/pages/StrudelDocsPage.kt`

- ✅ Created `StrudelDocsPage` component
- ✅ Search functionality (by name, category, tag)
- ✅ Category filtering
- ✅ Displays all function variants with signatures, params, examples
- ✅ Clean, readable UI with code syntax highlighting

**Features**:

- Real-time search as you type
- Filter by category (structural, synthesis, effects, etc.)
- Shows function signature, description, parameters, return value, examples
- Responsive layout with Semantic UI

## 📋 Next Steps

### Immediate

1. **Wire up routing** - Add route to `StrudelDocsPage` in app navigation
2. **Test in browser** - Verify the page renders correctly
3. **Add navigation link** - Add "Documentation" link to main menu

### Short Term (Next Functions)

4. **Convert more functions** using the established pattern:
    - `stack()` - plays patterns simultaneously
    - `fastcat()` / `slowcat()` - concatenation
    - `note()` - note patterns
    - `sound()` - sound patterns
    - `gain()` / `pan()` - basic effects

5. **Register docs** for each converted function

### Medium Term (Enhanced Documentation)

6. **CodeMirror integration** - Export docs as JSON for autocomplete
7. **IDE completion** - KDoc already works, but could enhance with more examples
8. **Static HTML export** - Generate static documentation site
9. **Interactive examples** - Allow running examples directly from docs page

### Long Term (Automation)

10. **KSP processor** - Auto-generate `DslDocs` registration from KDoc
11. **Validation** - Ensure all functions have corresponding docs
12. **Doc coverage report** - Track which functions are documented

## File Structure

```
klangscript/
  src/commonMain/kotlin/docs/
    DslDocs.kt                    # General doc model & registry

strudel/
  src/commonMain/kotlin/lang/
    lang.kt                       # Calls registerStrudelDocs()
    docs/
      StrudelDocs.kt             # Strudel function docs
    lang_structural.kt           # seq() implementation

  src/commonTest/kotlin/lang/
    LangSeqSpec.kt               # seq() tests (11 tests)
    docs/
      StrudelDocsSpec.kt         # Documentation tests (9 tests)

src/jsMain/kotlin/pages/
  StrudelDocsPage.kt             # Frontend docs viewer
```

## Testing Status

- ✅ LangSeqSpec: 11/11 tests passing
- ✅ StrudelDocsSpec: 9/9 tests passing
- ✅ Documentation registry working correctly
- ✅ Search and filtering functional

## Key Insights

1. **KlangScript-level docs** - Making it general-purpose allows any library to participate
2. **Underscore prefix pattern** - `trimStart('_')` allows private delegates without recursion
3. **Single source of truth** - KDoc + DslDocs registration work together
4. **Type-safe API** - PatternLike allows flexible arguments with compile-time safety
5. **Test-driven** - Every function MUST have corresponding LangXXXSpec tests

## Questions for Future

1. Should we auto-generate DslDocs from KDoc annotations? (KSP)
2. How to handle complex function signatures with multiple overloads?
3. Should examples be executable/testable?
4. How to version documentation across releases?

---

**Status**: Foundation complete, ready to scale to more functions
**Next Action**: Wire up routing to StrudelDocsPage and test in browser
