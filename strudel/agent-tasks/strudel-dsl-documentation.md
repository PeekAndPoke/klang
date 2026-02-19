# Strudel DSL Documentation Strategy

## Goals

Create comprehensive, multi-format documentation for all Strudel DSL functions that serves:

1. **IDE Code Completion** - IntelliJ/IDE shows functions with inline documentation
2. **CodeMirror Completion** - Frontend code editor provides autocomplete with docs
3. **Frontend UI Documentation** - In-app documentation viewer
4. **Static HTML Documentation** - Generated reference documentation

## Core Approach

### Phase 1: Manual Implementation (Pilot)

Start with one function (`seq`) to validate the entire pipeline before automation.

### Phase 2: Automation with KSP

Once validated, use Kotlin Symbol Processing (KSP) to auto-generate documentation from source code annotations.

---

## Implementation Pattern

For each DSL function, we create a structured set of overloads:

### 1. Private Delegate (Still Registers with KlangScript)

```kotlin
private val _functionName by dslFunction { args, callInfo ->
    // Implementation
}

private val StrudelPattern._functionName by dslPatternExtension { p, args, callInfo ->
    // Implementation
}

private val String._functionName by dslStringExtension { p, args, callInfo ->
    // Implementation
}
```

**Why private?**

- Delegates still register with KlangScript engine (doesn't affect registration)
- Keeps them as implementation detail
- Public API is through explicit overloads below

### 2. User-Facing Overloads (No CallInfo)

```kotlin
typealias PatternLike = Any  // Accepts String | StrudelPattern | Number | etc.

/**
 * Full KDoc documentation for users.
 *
 * Multi-line description explaining what the function does,
 * how it works, and when to use it.
 *
 * @param param1 Description of first parameter
 * @param param2 Description of second parameter
 * @return Description of return value
 * @sample seq("c d e", "f g a")  // Example 1
 * @sample seq(note("c"), note("e"))  // Example 2
 * @category structural
 * @tags timing, control, sequence
 */
@StrudelDsl
fun functionName(param1: Type, vararg patterns: PatternLike): ReturnType =
    functionName(param1, *patterns, callInfo = CallInfo.empty)

@StrudelDsl
fun StrudelPattern.functionName(vararg patterns: PatternLike): ReturnType =
    this.functionName(*patterns, callInfo = CallInfo.empty)

@StrudelDsl
fun String.functionName(vararg patterns: PatternLike): ReturnType =
    this.functionName(*patterns, callInfo = CallInfo.empty)
```

**Purpose:**

- Primary API that users see in IDE
- Full documentation in KDoc format
- No internal parameters (like callInfo) exposed
- Clean autocomplete experience

### 3. Internal Overloads (With CallInfo)

```kotlin
/** @internal */
@StrudelDsl
fun functionName(param1: Type, vararg patterns: PatternLike, callInfo: CallInfo): ReturnType {
    val args = listOf(StrudelDslArg.of(param1)) + patterns.map { StrudelDslArg.of(it) }
    return _functionName.invoke(args, callInfo)
}

/** @internal */
@StrudelDsl
fun StrudelPattern.functionName(vararg patterns: PatternLike, callInfo: CallInfo): ReturnType {
    val args = patterns.map { StrudelDslArg.of(it) }
    return this._functionName(args, callInfo)
}

/** @internal */
@StrudelDsl
fun String.functionName(vararg patterns: PatternLike, callInfo: CallInfo): ReturnType {
    val args = patterns.map { StrudelDslArg.of(it) }
    return this._functionName(args, callInfo)
}
```

**Purpose:**

- Used by internal Strudel code that needs to pass callInfo
- Marked `@internal` so KSP skips them for user documentation
- Calls through to private delegate with proper argument conversion

---

## Functions with Callbacks

For functions that accept callbacks (like `signal`, `every`, etc.), use the same two-overload pattern:

```kotlin
/**
 * User documentation.
 * @param callback Function that generates values
 */
@StrudelDsl
fun signal(callback: (Double) -> Any): StrudelPattern =
    signal(callback, CallInfo.empty)

/** @internal */
@StrudelDsl
fun signal(callback: (Double) -> Any, callInfo: CallInfo): StrudelPattern {
    return _signal.invoke(listOf(StrudelDslArg.of(callback)), callInfo)
}
```

---

## KDoc Format & Tags

### Standard Tags

```kotlin
/**
 * Brief one-line description.
 *
 * Detailed multi-line explanation of what the function does,
 * how it behaves, and when to use it.
 *
 * @param paramName Description of the parameter's purpose and accepted values
 * @return Description of what the function returns
 * @sample code example showing usage
 * @category categoryName (see categories below)
 * @tags comma, separated, keywords
 * @since version (optional, for tracking when function was added)
 */
```

### Category Taxonomy

Organize functions into logical categories:

- **structural** - Pattern structure and timing (seq, stack, cat, etc.)
- **synthesis** - Sound generation (note, sound, freq, etc.)
- **effects** - Audio effects (lpf, hpf, delay, reverb, etc.)
- **rhythm** - Rhythmic transformations (fast, slow, hurry, etc.)
- **control** - Control flow and modulation (sometimes, every, rand, etc.)
- **continuous** - Continuous patterns (sine, saw, timeOfDay, etc.)
- **euclidean** - Euclidean rhythm patterns (euclid, etc.)
- **utility** - Helper functions (range, scale, etc.)

### Sample Format

Use `@sample` tag for code examples:

```kotlin
@sample seq("c d e", "f g a")  // Two patterns, each plays for 1 cycle
@sample note("c").seq("e", "g")  // Chain patterns in sequence
```

---

## Pilot Implementation: `seq` Function

### Step 1: Implement Overloads

**File**: `strudel/src/commonMain/kotlin/lang/lang_structural.kt`

1. Add `typealias PatternLike = Any` at file level
2. Make existing delegates private with underscore prefix:
    - `private val _seq`
    - `private val StrudelPattern._seq`
    - `private val String._seq`
3. Create 6 new overloads:
    - 3 user-facing (no callInfo)
    - 3 internal (with callInfo)
4. Add comprehensive KDoc to user-facing versions

**Expected outcome**:

- IDE shows `seq()` with full documentation
- Existing KlangScript code continues to work
- Internal code can still pass callInfo when needed

### Step 2: Hand-Write Documentation Data Structure

**File**: `strudel/src/commonMain/kotlin/lang/docs/DslDocs.kt` (new)

Create data classes to represent documentation:

```kotlin
/**
 * Documentation for a single DSL function.
 */
data class FunctionDoc(
    val name: String,
    val variants: List<VariantDoc>,
    val category: String,
    val tags: List<String>
)

/**
 * Documentation for one overload variant of a function.
 */
data class VariantDoc(
    val type: DslType,
    val signature: String,
    val description: String,
    val params: List<ParamDoc>,
    val returnDoc: String,
    val samples: List<String>
)

enum class DslType {
    TOP_LEVEL,
    PATTERN_EXTENSION,
    STRING_EXTENSION
}

data class ParamDoc(
    val name: String,
    val type: String,
    val description: String
)

/**
 * Central registry of all DSL function documentation.
 */
object DslDocs {
    val functions: Map<String, FunctionDoc> = mapOf(
        "seq" to FunctionDoc(
            name = "seq",
            category = "structural",
            tags = listOf("timing", "control", "sequence"),
            variants = listOf(
                VariantDoc(
                    type = DslType.TOP_LEVEL,
                    signature = "seq(vararg patterns: PatternLike): StrudelPattern",
                    description = "Creates a sequence pattern from the given patterns. " +
                            "Each pattern plays for one cycle before moving to the next.",
                    params = listOf(
                        ParamDoc(
                            name = "patterns",
                            type = "vararg PatternLike",
                            description = "Patterns to play in sequence. Can be patterns, strings, or numbers."
                        )
                    ),
                    returnDoc = "A sequential pattern that cycles through each input pattern",
                    samples = listOf(
                        """seq("c d e", "f g a")  // Two patterns, each for 1 cycle""",
                        """seq(note("c"), note("e"), note("g"))  // Three note patterns"""
                    )
                ),
                VariantDoc(
                    type = DslType.PATTERN_EXTENSION,
                    signature = "StrudelPattern.seq(vararg patterns: PatternLike): StrudelPattern",
                    description = "Appends patterns to this pattern in sequence.",
                    params = listOf(
                        ParamDoc(
                            name = "patterns",
                            type = "vararg PatternLike",
                            description = "Additional patterns to append"
                        )
                    ),
                    returnDoc = "Combined sequential pattern",
                    samples = listOf(
                        """note("c e").seq("g a")  // c-e for cycle 1, g-a for cycle 2"""
                    )
                ),
                VariantDoc(
                    type = DslType.STRING_EXTENSION,
                    signature = "String.seq(vararg patterns: PatternLike): StrudelPattern",
                    description = "Converts string to pattern and appends in sequence.",
                    params = listOf(
                        ParamDoc(
                            name = "patterns",
                            type = "vararg PatternLike",
                            description = "Additional patterns to append"
                        )
                    ),
                    returnDoc = "Combined sequential pattern",
                    samples = listOf(
                        """"c e".seq("g a")  // Convert and sequence"""
                    )
                )
            )
        )
        // More functions will be added here...
    )
}
```

**Expected outcome**:

- Type-safe documentation data structure
- Can be serialized to JSON
- Ready for consumption by different systems

### Step 3: Wire Up Documentation Consumers

#### A. CodeMirror Integration (Frontend)

**File**: `strudel/src/jsMain/kotlin/lang/docs/DslDocsExporter.kt` (new)

```kotlin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class CodeMirrorCompletion(
    val label: String,
    val type: String,
    val detail: String,
    val info: String,
    val apply: String?
)

object DslDocsExporter {
    /**
     * Exports documentation as JSON for CodeMirror autocomplete.
     */
    fun exportForCodeMirror(): String {
        val completions = DslDocs.functions.values.flatMap { func ->
            func.variants.map { variant ->
                CodeMirrorCompletion(
                    label = func.name,
                    type = when (variant.type) {
                        DslType.TOP_LEVEL -> "function"
                        DslType.PATTERN_EXTENSION -> "method"
                        DslType.STRING_EXTENSION -> "method"
                    },
                    detail = variant.signature,
                    info = buildInfo(func, variant),
                    apply = null
                )
            }
        }
        return Json.encodeToString(completions)
    }

    private fun buildInfo(func: FunctionDoc, variant: VariantDoc): String {
        return buildString {
            appendLine(variant.description)
            appendLine()
            if (variant.samples.isNotEmpty()) {
                appendLine("**Examples:**")
                variant.samples.forEach { sample ->
                    appendLine("```javascript")
                    appendLine(sample)
                    appendLine("```")
                }
            }
        }
    }
}
```

**Expected outcome**: JSON file that CodeMirror can consume for autocomplete

#### B. Frontend UI Documentation Viewer

**File**: `src/jsMain/kotlin/pages/DocsPage.kt` (or similar)

```kotlin
fun VDom.renderDocsPage() {
    div {
        css { /* styling */ }

        h1 { +"Strudel DSL Reference" }

        // Category navigation
        renderCategoryNav()

        // Function list
        DslDocs.functions.values
            .sortedBy { it.name }
            .forEach { func ->
                renderFunctionDoc(func)
            }
    }
}

fun VDom.renderFunctionDoc(func: FunctionDoc) {
    div {
        css { /* function card styling */ }

        h2 { +func.name }

        // Category badge
        span {
            css { /* badge styling */ }
            +func.category
        }

        // Tags
        func.tags.forEach { tag ->
            span {
                css { /* tag styling */ }
                +tag
            }
        }

        // Variants
        func.variants.forEach { variant ->
            renderVariant(variant)
        }
    }
}
```

**Expected outcome**: In-app documentation browser

#### C. Static HTML Generation

**Gradle Task**: `strudel/build.gradle.kts`

```kotlin
tasks.register("generateDslDocs") {
    group = "documentation"
    description = "Generate static HTML documentation for Strudel DSL"

    doLast {
        val outputDir = file("$buildDir/docs/dsl")
        outputDir.mkdirs()

        // Generate index.html
        val html = generateHtmlDocs()
        file("$outputDir/index.html").writeText(html)

        println("Documentation generated at: ${outputDir.absolutePath}")
    }
}

fun generateHtmlDocs(): String {
    return buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html>")
        appendLine("<head>")
        appendLine("  <title>Strudel DSL Reference</title>")
        appendLine("  <style>/* CSS styling */</style>")
        appendLine("</head>")
        appendLine("<body>")
        appendLine("  <h1>Strudel DSL Reference</h1>")

        DslDocs.functions.values.forEach { func ->
            appendLine("  <div class='function'>")
            appendLine("    <h2>${func.name}</h2>")
            appendLine("    <p class='category'>${func.category}</p>")
            // ... render variants, examples, etc.
            appendLine("  </div>")
        }

        appendLine("</body>")
        appendLine("</html>")
    }
}
```

**Run**: `./gradlew :strudel:generateDslDocs`

**Expected outcome**: Static HTML documentation in `build/docs/dsl/`

### Step 4: Validation Checklist

- [ ] IDE shows `seq()` with full documentation in autocomplete
- [ ] CodeMirror JSON export contains seq documentation
- [ ] Frontend docs page displays seq correctly
- [ ] Static HTML generation produces readable docs
- [ ] Existing KlangScript code using `seq` still works
- [ ] Internal code can pass callInfo when needed

### Step 5: Document the Pattern

Once validated, update this document with:

- Lessons learned
- Edge cases discovered
- Best practices for KDoc writing
- Examples of good documentation

---

## Phase 2: KSP Automation (Future)

### KSP Processor Design

**Module**: `strudel-ksp-processor` (new Gradle module)

The KSP processor will:

1. **Find all `@StrudelDsl` annotated functions**
2. **Skip functions marked `@internal`**
3. **Extract KDoc comments** using `KSDeclaration.docString`
4. **Parse KDoc tags**:
    - `@param` → parameter documentation
    - `@return` → return value documentation
    - `@sample` → code examples
    - `@category` → function category
    - `@tags` → search keywords
5. **Generate `DslDocs.kt`** with the same structure as hand-written version

### KSP Implementation Sketch

```kotlin
class DslDocsProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val strudelDslAnnotation = resolver.getSymbolsWithAnnotation("io.peekandpoke.klang.strudel.lang.StrudelDsl")

        val functions = strudelDslAnnotation
            .filterIsInstance<KSFunctionDeclaration>()
            .filterNot { it.hasAnnotation("internal") }
            .groupBy { it.simpleName.asString() }
            .map { (name, variants) ->
                FunctionDoc(
                    name = name,
                    variants = variants.map { extractVariantDoc(it) },
                    category = extractCategory(variants.first()),
                    tags = extractTags(variants.first())
                )
            }

        generateDslDocsFile(functions)

        return emptyList()
    }

    private fun extractVariantDoc(func: KSFunctionDeclaration): VariantDoc {
        val docString = func.docString ?: ""

        return VariantDoc(
            type = determineType(func),
            signature = func.signature(),
            description = extractDescription(docString),
            params = extractParams(docString, func),
            returnDoc = extractReturn(docString),
            samples = extractSamples(docString)
        )
    }

    private fun generateDslDocsFile(functions: List<FunctionDoc>) {
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(false),
            packageName = "io.peekandpoke.klang.strudel.lang.docs",
            fileName = "DslDocs"
        )

        file.writer().use { writer ->
            writer.write(generateKotlinCode(functions))
        }
    }
}
```

### KSP Benefits

- ✅ **Single source of truth**: KDoc on functions
- ✅ **No manual sync**: Docs auto-update when code changes
- ✅ **Type-safe**: Compiler verifies documentation structure
- ✅ **Scalable**: Easy to add new functions

### KSP Gradle Setup

```kotlin
// strudel/build.gradle.kts
plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    ksp(project(":strudel-ksp-processor"))
}
```

---

## Migration Strategy

### Phase 1: Pilot (Manual)

- ✅ Implement pattern with `seq`
- ✅ Validate all consumer systems work
- ✅ Document best practices

### Phase 2: Expand Manually

- Convert 5-10 more functions manually
- Identify common patterns and edge cases
- Refine KDoc format and guidelines

### Phase 3: Build KSP Processor

- Implement KSP symbol processor
- Generate docs for converted functions
- Validate generated code matches hand-written

### Phase 4: Full Migration

- Convert all remaining functions
- Remove hand-written docs
- KSP becomes source of truth

### Phase 5: Maintenance

- New functions automatically get documentation
- Update KDoc = update all output formats
- Continuous improvement of generated docs

---

## Guidelines for Writing Function Documentation

### 1. Description

Start with a clear, concise one-liner, then expand:

```kotlin
/**
 * Creates a sequence pattern that plays patterns one after another.
 *
 * Each pattern in the sequence occupies exactly one cycle. If you have
 * three patterns, the sequence takes three cycles to complete before
 * looping back to the start.
 *
 * @param patterns Patterns to sequence
 */
```

### 2. Parameters

Be specific about what's accepted:

```kotlin
/**
 * @param n Number of subdivisions (must be positive)
 * @param patterns Patterns to arrange. Accepts patterns, strings, or numbers.
 * @param callback Function that receives the pattern and returns a modified version
 */
```

### 3. Samples

Show real, working examples:

```kotlin
/**
 * @sample seq("c d e", "f g a")  // Simple string patterns
 * @sample seq(note("c"), sound("bd"))  // Mixed pattern types
 * @sample note("c").seq("e", "g")  // Extension method usage
 */
```

### 4. Categories

Choose the most specific category:

```kotlin
/**
 * @category synthesis  // For sound generation
 * @category effects    // For audio effects
 * @category structural // For pattern structure/timing
 */
```

### 5. Tags

Add searchable keywords:

```kotlin
/**
 * @tags sequence, timing, control, order
 */
```

---

## Success Criteria

### Technical

- [ ] All DSL functions have explicit typed overloads
- [ ] All functions have comprehensive KDoc
- [ ] KSP processor generates correct documentation
- [ ] All output formats (IDE, CodeMirror, UI, HTML) work

### User Experience

- [ ] IDE autocomplete shows helpful documentation
- [ ] CodeMirror provides context-aware completions
- [ ] Documentation is searchable and well-organized
- [ ] Examples are runnable and educational

### Maintainability

- [ ] Adding a new function is straightforward
- [ ] Documentation updates automatically propagate
- [ ] Pattern is well-documented for contributors
- [ ] Build process is fast and reliable

---

## Open Questions

1. **Versioning**: Should we track which version each function was added?
2. **Deprecation**: How to mark deprecated functions and suggest alternatives?
3. **Localization**: Should we support multiple languages (German, etc.)?
4. **Interactive Examples**: Can we embed runnable code examples in docs?
5. **Video Tutorials**: Should docs link to video explanations?

---

## Next Steps

1. Review this plan and make adjustments
2. Implement Step 1: `seq` overloads in `lang_structural.kt`
3. Implement Step 2: Hand-write `DslDocs.kt` structure
4. Implement Step 3: Wire up all consumers
5. Validate everything works end-to-end
6. Document learnings and iterate

---

**Document Status**: Draft
**Last Updated**: 2026-02-19
**Author**: Collaborative (User + Claude)
