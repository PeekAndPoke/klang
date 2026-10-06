/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

/**
 * Unified data model for one KlangScript native-function registration.
 *
 * Built during Pass 1 from `@KlangScript.*` annotations. Each item knows
 * how to [renderRegistration] — producing the exact Kotlin source for its
 * registration call. No external if-else dispatch needed.
 */
sealed class RegistrationItem {
    abstract val scriptName: String
    abstract val specsExpr: String

    /** Produce the Kotlin source for this registration call (WITHOUT leading indent). */
    abstract fun renderRegistration(): String
}

// ============================================================================
//  Concrete subtypes
// ============================================================================

/**
 * A fixed-arity method with NO Kotlin defaults.
 *
 * Renders with explicit generic type arguments so Kotlin can infer the lambda
 * parameter types without us writing them out. This is the only sane way to
 * pass function-type parameters (like `(SprudelPattern) -> SprudelPattern`)
 * through — typed lambda params `{ x: (A) -> B -> body }` are ambiguous at
 * the parser level because the lambda's `->` collides with the type's `->`.
 *
 * Top-level: `registerFunction<P1, ..., PN, R>("name", specs) { p1, ..., pN -> fn(callArgs) }`
 * Method:    `registerMethod<P1, ..., PN, R>("name", specs) { p1, ..., pN -> fn(callArgs) }`
 */
data class FixedMethodItem(
    override val scriptName: String,
    override val specsExpr: String,
    val fnCall: String,
    val params: List<Pair<String, String>>,
    val returnType: String,
    val callArgs: String,
    val isTopLevel: Boolean,
) : RegistrationItem() {
    override fun renderRegistration(): String {
        val registerFn = if (isTopLevel) "registerFunction" else "registerMethod"

        if (params.isEmpty()) {
            return "$registerFn<$returnType>(\"$scriptName\", $specsExpr) { $fnCall($callArgs) }"
        }

        val genericArgs = (params.map { it.second } + returnType).joinToString(", ")
        val paramNames = params.joinToString(", ") { it.first }
        return "$registerFn<$genericArgs>(\"$scriptName\", $specsExpr) { $paramNames -> $fnCall($callArgs) }"
    }
}

/**
 * A method or function on the spec-aware path: one with Kotlin defaults, a `CallInfo` parameter, or
 * more parameters than the fixed-arity overloads cover. Always a raw `registerExtensionMethodWithSpecs`
 * or `registerFunctionWithSpecs` closure whose body is [appendConversionsAndCall]: one call per door,
 * with no arity dispatch.
 */
data class SpecAwareItem(
    override val scriptName: String,
    override val specsExpr: String,
    val fnCall: String,
    val selfArg: String,
    val scriptParams: List<ResolvedParam>,
    val receiverCast: ReceiverCast?,
    val isTopLevel: Boolean,
    val hasCallInfo: Boolean = false,
) : RegistrationItem() {

    /**
     * One script parameter as the emitter needs it.
     *
     * @property defaultLiteral the parameter's Kotlin default as a safe literal (`null`, `0.5`,
     *   `"normal"`), pasted into the call when the caller left the parameter out. Null when the
     *   parameter has no default. A non-literal default never gets here: the processor refuses the
     *   door (see [decideDefault]).
     */
    data class ResolvedParam(
        val name: String,
        val kotlinType: String,
        val castType: String,
        val hasDefault: Boolean,
        val isNullable: Boolean,
        val index: Int,
        val defaultLiteral: String? = null,
    )

    data class ReceiverCast(val typeName: String, val useConvertToKotlin: Boolean)

    override fun renderRegistration(): String = buildString {
        // Emit at column 0; the caller wraps with `prependIndent` to position the whole
        // block within its surrounding context (top-level vs nested register block).
        if (isTopLevel) {
            appendLine("registerFunctionWithSpecs(")
            appendLine("    name = \"$scriptName\",")
            appendLine("    paramSpecs = $specsExpr,")
            appendLine(") { args, loc ->")
        } else {
            appendLine("builder.registerExtensionMethodWithSpecs(")
            appendLine("    receiver = cls,")
            appendLine("    name = \"$scriptName\",")
            appendLine("    paramSpecs = $specsExpr,")
            appendLine(") { receiver, args, loc ->")
        }

        val indent = "    "

        appendReceiverCast(indent, receiverCast)

        if (hasCallInfo) {
            val receiverExpr = if (isTopLevel) "null" else "receiver"
            appendLine("${indent}val callInfo = callInfoOf($receiverExpr, args, loc)")
        }

        appendConversionsAndCall(indent, scriptName, scriptParams, fnCall, selfArg, hasCallInfo)

        append("}")
    }
}

/**
 * A raw rendered registration block. Use when none of the specialised items
 * (VarargItem / SpecAwareItem / FixedMethodItem / FileLevelExtItem) fit
 * the shape — e.g. file-level vararg extensions which need a custom
 * `registerExtensionMethodWithSpecs` body plus manual vararg spread.
 */
data class RawBlockItem(
    override val scriptName: String,
    override val specsExpr: String,
    val rendered: String,
) : RegistrationItem() {
    override fun renderRegistration(): String = rendered
}

/**
 * A vararg method/function — uses legacy helpers, no paramSpecs threading.
 */
data class VarargItem(
    override val scriptName: String,
    override val specsExpr: String,
    val paramType: String,
    val returnType: String,
    val fnCallWithArgs: String,
    val hasCallInfo: Boolean,
    val isTopLevel: Boolean,
) : RegistrationItem() {
    override fun renderRegistration(): String {
        val helper = if (isTopLevel) {
            if (hasCallInfo) "registerVarargFunctionWithCallInfo" else "registerVarargFunction"
        } else {
            if (hasCallInfo) "registerVarargMethodWithCallInfo" else "registerVarargMethod"
        }
        return if (hasCallInfo) {
            "$helper<$paramType, $returnType>(\"$scriptName\") { args, callInfo -> $fnCallWithArgs }"
        } else {
            "$helper<$paramType, $returnType>(\"$scriptName\") { args -> $fnCallWithArgs }"
        }
    }
}

/**
 * A raw-args method where first Kotlin param is `List<RuntimeValue>`.
 */
data class RawArgsItem(
    override val scriptName: String,
    override val specsExpr: String,
    val ownerName: String,
    val fnName: String,
    val hasLocation: Boolean,
) : RegistrationItem() {
    override fun renderRegistration(): String {
        val callArgs = if (hasLocation) "args, loc" else "args, null"
        return "registerExtensionMethod($ownerName::class, \"$scriptName\") { _, args, loc -> $ownerName.$fnName($callArgs) }"
    }
}

/**
 * A file-level extension method registered at top level via
 * `registerExtensionMethodWithSpecs(Type::class, ...)`.
 */
data class FileLevelExtItem(
    override val scriptName: String,
    override val specsExpr: String,
    val receiverClassName: String,
    val receiverCast: SpecAwareItem.ReceiverCast?,
    val fnName: String,
    val scriptParams: List<SpecAwareItem.ResolvedParam>,
    val hasCallInfo: Boolean,
    val selfArg: String,
    val fnCallPrefix: String,
) : RegistrationItem() {
    override fun renderRegistration(): String = buildString {
        // Emit at column 0; the caller wraps with `prependIndent` to position the whole
        // block within its surrounding context.
        appendLine("registerExtensionMethodWithSpecs(")
        appendLine("    receiver = $receiverClassName::class,")
        appendLine("    name = \"$scriptName\",")
        appendLine("    paramSpecs = $specsExpr,")
        appendLine(") { receiver, args, loc ->")

        val indent = "    "

        appendReceiverCast(indent, receiverCast)

        if (hasCallInfo) {
            appendLine("${indent}val callInfo = callInfoOf(receiver, args, loc)")
        }

        // For an extension receiver, selfArg is "" (set in buildFileLevelExtItem) and the receiver
        // travels in fnCallPrefix (`typedReceiver.`) instead.
        appendConversionsAndCall(indent, scriptName, scriptParams, "$fnCallPrefix$fnName", selfArg, hasCallInfo)

        append("}")
    }
}

// ============================================================================
//  Shared utility
// ============================================================================

/**
 * Produces the `as Type` suffix for a converted argument. A [castType] that already ends in `?` is
 * not suffixed again: `resolveCastType` renders a nullable function type as `((A) -> B)?` and a
 * nullable plain type arrives as `Double?`, and `((A) -> B)??` is not a type Kotlin accepts.
 *
 * Empty when the cast is redundant: the converters return `T?` for `cls = T::class`, so a nullable
 * parameter whose cast type is exactly its [classLiteral] (`Number?` with `Number::class`, and
 * `Any?`) already has the right type, and Kotlin would warn "No cast needed". Exact string equality
 * only: a generic type (`List<Double>?` with `List::class`), a type alias or a function type keeps
 * its cast. A non-null cast on the `T?` result is a real cast and always stays.
 */
internal fun castSuffix(castType: String, isNullable: Boolean, classLiteral: String): String {
    val fullType = if (isNullable && !castType.endsWith("?")) "$castType?" else castType

    if (fullType == "Any?") {
        return ""
    }

    if (isNullable && fullType == "$classLiteral?") {
        return ""
    }

    return " as $fullType"
}

/** The receiver line of a spec-aware closure, when the door has a typed receiver. */
internal fun StringBuilder.appendReceiverCast(indent: String, receiverCast: SpecAwareItem.ReceiverCast?) {
    if (receiverCast == null) {
        return
    }

    appendLine("$indent@Suppress(\"UNCHECKED_CAST\")")

    if (receiverCast.useConvertToKotlin) {
        appendLine("${indent}val typedReceiver = wrapAsRuntimeValue(receiver).convertToKotlin(${receiverCast.typeName}::class, loc)")
    } else {
        appendLine("${indent}val typedReceiver = receiver as ${receiverCast.typeName}")
    }
}

/**
 * The body every spec-aware closure shares after its receiver and `CallInfo` lines: the arity check,
 * one `val` per required parameter, then ONE call of the native.
 *
 * An optional parameter is converted inside the call when the caller passed it and otherwise gets
 * its Kotlin default, pasted as the literal the processor extracted
 * ([SpecAwareItem.ResolvedParam.defaultLiteral]). No arity dispatch: every script call, positional or
 * named, arrives with every optional filled by the spec's default thunk, so the pasted literal serves
 * only native callers that pass fewer arguments (a native function handed to a Kotlin function slot,
 * which calls it with the slot's arity).
 *
 * Conversion order is the old one: required parameters first, then the optional ones by index.
 * Named arguments make the Kotlin parameter order irrelevant (`callInfo` in the middle, a trailing
 * lambda last).
 */
internal fun StringBuilder.appendConversionsAndCall(
    indent: String,
    scriptName: String,
    scriptParams: List<SpecAwareItem.ResolvedParam>,
    fnCall: String,
    selfArg: String,
    hasCallInfo: Boolean,
) {
    val requiredCount = scriptParams.count { !it.hasDefault }

    appendLine("${indent}${arityCheck(scriptName, scriptParams, requiredCount)}")

    scriptParams.forEach { param ->
        if (!param.hasDefault) {
            appendLine("${indent}val ${param.name} = ${requiredArgExpression(scriptName, param)}")
        }
    }

    if (scriptParams.none { it.hasDefault }) {
        val callArgs = scriptParams.joinToString(", ") { "${it.name} = ${it.name}" }
        appendLine("${indent}wrapAsRuntimeValue($fnCall(${withCallInfo(joinCallArgs(selfArg, callArgs), hasCallInfo)}))")

        return
    }

    appendLine("${indent}wrapAsRuntimeValue(")
    appendLine("$indent    $fnCall(")

    val self = selfArg.trimEnd(' ', ',')

    if (self.isNotEmpty()) {
        appendLine("$indent        $self,")
    }

    scriptParams.forEach { param ->
        val value = if (param.hasDefault) optionalArgExpression(scriptName, param) else param.name
        appendLine("$indent        ${param.name} = $value,")
    }

    if (hasCallInfo) {
        appendLine("$indent        callInfo = callInfo,")
    }

    appendLine("$indent    )")
    appendLine("${indent})")
}

/** The conversion of a required parameter (and of a passed optional one): `convertArgToKotlin(...)` plus its cast. */
internal fun requiredArgExpression(scriptName: String, param: SpecAwareItem.ResolvedParam): String =
    "convertArgToKotlin(fn = \"$scriptName\", args = args, index = ${param.index}, cls = ${param.kotlinType}::class, " +
            "nullable = ${param.isNullable}, loc = loc)${castSuffix(param.castType, param.isNullable, param.kotlinType)}"

/**
 * The value of an optional parameter: the converted argument when the caller passed it, else the
 * Kotlin default literal. The common shape, a nullable parameter defaulting to `null`, is the
 * runtime's `optArg`, which converts exactly as `convertArgToKotlin` does.
 */
internal fun optionalArgExpression(scriptName: String, param: SpecAwareItem.ResolvedParam): String {
    // The processor refuses a door whose optional parameter has no safe literal default, so the
    // fallback only keeps the emitted text well-formed until that error stops the build.
    val literal = param.defaultLiteral ?: "null"

    if (param.isNullable && literal == "null") {
        return "optArg(args, ${param.index}, ${param.kotlinType}::class, loc)${castSuffix(param.castType, true, param.kotlinType)}"
    }

    return "if (args.size > ${param.index}) ${requiredArgExpression(scriptName, param)} else $literal"
}

/**
 * What the processor does with the default of one optional parameter of the script door [door]:
 * paste it as [DefaultDecision.literal], or refuse the door with [DefaultDecision.error].
 *
 * [defaultText] is the default as `DefaultValueExtractor` read it (comments already stripped), null
 * when it could not be read; both the refusal and the pasting use this one decision. A safe literal
 * ([SafeDefaultLiteral]) is pasted into the spec's default thunk (every script call) and into the
 * generated call (a native caller with fewer arguments). Anything else (`IgnitorDsl.Slots.rate`,
 * `emptyList()`, `kotlin.math.PI`) has no literal to paste and is refused.
 */
internal fun decideDefault(door: String, parameter: String, defaultText: String?): DefaultDecision {
    val text = defaultText?.trim()

    if (text != null && SafeDefaultLiteral.isSafe(text)) {
        return DefaultDecision(literal = text, error = null)
    }

    val shown = text?.let { "`$it`" } ?: "(a default the processor could not read)"

    return DefaultDecision(
        literal = null,
        error = "KlangScript door '$door': optional parameter '$parameter' has the non-literal default $shown. " +
                "A script-door default must be a literal (number, string, boolean or null): the generated " +
                "registration pastes it for an omitted argument. Bake the value as a literal, or make the " +
                "parameter's type nullable with `= null`, and resolve the real default in the body.",
    )
}

/** The outcome of [decideDefault]: exactly one of [literal] and [error] is set. */
data class DefaultDecision(val literal: String?, val error: String?)

/**
 * The type name usable in a class literal (`X::class`) for a resolved Kotlin type name. A class
 * literal has no nullability, so `Function1?` (a nullable `configure: ((B) -> B)? = null`
 * parameter) must become `Function1`; nullability travels separately in `isNullable`.
 */
internal fun classLiteralTypeName(resolvedKotlinType: String): String = resolvedKotlinType.removeSuffix("?")

/**
 * The area of a generated registration block: its source file's name without `.kt`, cut after the second
 * `_`-separated part. Sprudel's `lang_structural_chunk.kt` and `lang_structural_seq.kt` share the area
 * `lang_structural`; a file without `_` (`KlangScriptIgnitor.kt`) is an area of its own. A block whose
 * file is unknown goes to `misc`.
 */
internal fun sourceArea(fileName: String?): String {
    val base = fileName?.removeSuffix(".kt")?.takeIf { it.isNotBlank() } ?: return "misc"

    return base.split('_').take(2).joinToString("_")
}

/**
 * An [sourceArea] as an identifier part: `lang_structural` becomes `LangStructural`, and an area with no
 * letter or digit becomes `Misc`. The identifier, not the raw area, groups the blocks into files and
 * names them, so two areas that normalize alike share one file instead of colliding on its name, and
 * no area can produce the entry point's file name.
 */
internal fun areaIdentifier(area: String): String =
    area.split('_', '-', '.', ' ')
        .filter { it.isNotEmpty() }
        .joinToString("") { part -> part.filter { it.isLetterOrDigit() }.replaceFirstChar { it.uppercase() } }
        .ifEmpty { "Misc" }

/** A chunk function of the generated registration: its area identifier, its name and its rendered blocks. */
internal class RegistrationChunk(
    val area: String,
    val functionName: String,
    val blocks: MutableList<String>,
    var size: Int,
)

/**
 * Distributes rendered registration [blocks] (area identifier to text, in collection order) over chunk
 * functions named `<functionPrefix><area>Chunk<n>`, numbered per area. A chunk holds blocks of ONE area
 * and stays within [budget] characters (a block larger than the budget gets a chunk of its own). The
 * result is in collection order, which is the order the entry point calls the chunks in ([entryPointCalls]),
 * so the registration order is the order the blocks were collected in.
 */
internal fun distributeIntoChunks(
    blocks: List<Pair<String, String>>,
    budget: Int,
    functionPrefix: String,
): List<RegistrationChunk> {
    val chunks = mutableListOf<RegistrationChunk>()
    val chunkCountPerArea = mutableMapOf<String, Int>()

    for ((area, text) in blocks) {
        val current = chunks.lastOrNull()
        val fits = current != null && current.area == area && current.size + text.length <= budget

        if (fits) {
            current.blocks.add(text)
            current.size += text.length
            continue
        }

        val index = chunkCountPerArea.getOrElse(area) { 0 }
        chunkCountPerArea[area] = index + 1
        chunks.add(RegistrationChunk(area, "$functionPrefix${area}Chunk$index", mutableListOf(text), text.length))
    }

    return chunks
}

/** The entry point's calls, one per chunk, in the order of [chunks] (never re-sorted: that order is the registration order). */
internal fun entryPointCalls(chunks: List<RegistrationChunk>): List<String> = chunks.map { "${it.functionName}()" }

internal fun joinCallArgs(selfArg: String, args: String): String = when {
    selfArg.isEmpty() -> args
    args.isEmpty() -> selfArg.trimEnd(' ', ',')
    else -> "$selfArg$args"
}

/** Appends `callInfo = callInfo` to a call-args string when [hasCallInfo] is true. */
internal fun withCallInfo(args: String, hasCallInfo: Boolean): String = when {
    !hasCallInfo -> args
    args.isEmpty() -> "callInfo = callInfo"
    else -> "$args, callInfo = callInfo"
}

/**
 * The generated arity check: a door with no script parameter refuses any argument (`checkNoArgs`),
 * every other door checks the required count (`checkArgsSize`, which only rejects too FEW arguments
 * because the parameter specs catch a surplus). Without the split, `3.14159.round(2)` returned 3 with
 * the 2 silently dropped (2026-09-08).
 */
internal fun arityCheck(scriptName: String, scriptParams: List<Any?>, requiredCount: Int): String =
    if (scriptParams.isEmpty()) {
        "checkNoArgs(fn = \"$scriptName\", args = args, location = loc)"
    } else {
        "checkArgsSize(fn = \"$scriptName\", args = args, expected = $requiredCount, location = loc)"
    }

/**
 * Identifiers the generated registration bodies bind themselves. An owner whose simple name is one
 * of these can never be shortened, because the local binding would win the name lookup.
 */
internal val GENERATED_LOCAL_NAMES: Set<String> = setOf(
    "arg", "args", "builder", "callInfo", "cls", "index", "kotlinArgs", "loc", "receiver", "typedReceiver",
)

/**
 * How the generated code should spell a reference to the class or object that owns a registered
 * function: its simple name when that is guaranteed to resolve to [ownerFqcn], the fully qualified
 * name otherwise.
 *
 * The generated file already imports every owner, so the qualifier is usually pure noise. It is
 * kept in exactly three cases, each of which would otherwise resolve to the wrong thing silently:
 *
 * 1. [ownerFqcn] is not among [importedFqcns]. The file also has a few wildcard imports, but this
 *    helper cannot see what they contain, so an unimported name stays spelled out.
 * 2. Two imports share the simple name. Then neither may use it, whichever one we are emitting.
 * 3. The generated body binds the simple name itself, as a local `val` or a lambda parameter.
 *    This is the real one: sprudel has `object vowel` with a `vowel` parameter, so the body of
 *    `vowel(vowel = ...)` reads `val vowel = convertArgToKotlin(...)` and a shortened
 *    `vowel.invoke(...)` would call through that local instead of the object.
 *
 * The result is not keyword-escaped; the caller escapes it, which works the same for one segment
 * as for many.
 */
internal fun ownerReference(
    ownerFqcn: String,
    importedFqcns: Set<String>,
    localNames: Set<String>,
): String {
    val simpleName = ownerFqcn.substringAfterLast('.')

    if (simpleName.isEmpty() || simpleName == ownerFqcn) {
        return ownerFqcn
    }

    if (ownerFqcn !in importedFqcns) {
        return ownerFqcn
    }

    if (simpleName in localNames) {
        return ownerFqcn
    }

    val importsWithThisSimpleName = importedFqcns.count { it.substringAfterLast('.') == simpleName }

    if (importsWithThisSimpleName != 1) {
        return ownerFqcn
    }

    return simpleName
}
