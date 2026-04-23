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
 * A method/function with Kotlin defaults → arity-dispatch body.
 * Always uses a raw `registerExtensionMethodWithSpecs` or `registerFunctionWithSpecs` closure.
 */
data class ArityDispatchItem(
    override val scriptName: String,
    override val specsExpr: String,
    val fnCall: String,
    val selfArg: String,
    val scriptParams: List<ResolvedParam>,
    val receiverCast: ReceiverCast?,
    val isTopLevel: Boolean,
    val hasCallInfo: Boolean = false,
) : RegistrationItem() {

    data class ResolvedParam(
        val name: String,
        val kotlinType: String,
        val castType: String,
        val hasDefault: Boolean,
        val isNullable: Boolean,
        val index: Int,
    )
    data class ReceiverCast(val typeName: String, val useConvertToKotlin: Boolean)

    private fun callWithCallInfo(args: String): String = withCallInfo(args, hasCallInfo)

    override fun renderRegistration(): String = buildString {
        val requiredCount = scriptParams.count { !it.hasDefault }
        val firstOptionalIdx = scriptParams.indexOfFirst { it.hasDefault }
        val hasDefaults = firstOptionalIdx != -1

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

        if (receiverCast != null) {
            appendLine("$indent@Suppress(\"UNCHECKED_CAST\")")
            if (receiverCast.useConvertToKotlin) {
                appendLine("${indent}val typedReceiver = wrapAsRuntimeValue(receiver).convertToKotlin(${receiverCast.typeName}::class, loc)")
            } else {
                appendLine("${indent}val typedReceiver = receiver as ${receiverCast.typeName}")
            }
        }

        if (hasCallInfo) {
            val receiverLocExpr = if (isTopLevel) {
                "null"
            } else {
                "(receiver as? StringValue)?.location ?: (receiver as? NumberValue)?.location"
            }
            appendLine("${indent}val callInfo = CallInfo(")
            appendLine("$indent    callLocation = loc,")
            appendLine("$indent    receiverLocation = $receiverLocExpr,")
            appendLine("$indent    paramLocations = args.map { arg -> (arg as? StringValue)?.location ?: (arg as? NumberValue)?.location },")
            appendLine("$indent)")
        }

        appendLine("${indent}checkArgsSize(fn = \"$scriptName\", args = args, expected = $requiredCount, location = loc)")

        // Required params — always convert
        scriptParams.forEach { param ->
            if (!param.hasDefault) {
                appendLine(
                    "${indent}val ${param.name} = convertArgToKotlin(fn = \"$scriptName\", args = args, index = ${param.index}, cls = ${param.kotlinType}::class, nullable = ${param.isNullable}, loc = loc)${
                        castSuffix(param.castType, param.isNullable)
                    }"
                )
            }
        }

        if (!hasDefaults) {
            // No defaults — single call with all required params
            val callArgs = scriptParams.map { it.name }.joinToString(", ")
            appendLine("${indent}wrapAsRuntimeValue($fnCall(${callWithCallInfo(joinCallArgs(selfArg, callArgs))}))")
            append("}")
            return@buildString
        }

        appendLine("${indent}wrapAsRuntimeValue(")

        // Arity-dispatch chain
        for (level in scriptParams.size downTo firstOptionalIdx + 1) {
            val argsForLevel = scriptParams.take(level)
            val prefix = if (level == scriptParams.size) "if" else "} else if"
            appendLine("$indent    $prefix (args.size >= $level) {")

            argsForLevel.forEach { param ->
                if (param.hasDefault) {
                    appendLine(
                        "$indent        val ${param.name} = convertArgToKotlin(fn = \"$scriptName\", args = args, index = ${param.index}, cls = ${param.kotlinType}::class, nullable = ${param.isNullable}, loc = loc)${
                            castSuffix(param.castType, param.isNullable)
                        }"
                    )
                }
            }

            val callArgs = argsForLevel.map { it.name }.joinToString(", ")
            appendLine("$indent        $fnCall(${callWithCallInfo(joinCallArgs(selfArg, callArgs))})")
        }

        val requiredArgs = scriptParams.filter { !it.hasDefault }.map { it.name }.joinToString(", ")
        appendLine("$indent    } else {")
        appendLine("$indent        $fnCall(${callWithCallInfo(joinCallArgs(selfArg, requiredArgs))})")
        appendLine("$indent    }")
        appendLine("$indent)")

        append("}")
    }
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
    val receiverCast: ArityDispatchItem.ReceiverCast?,
    val fnName: String,
    val scriptParams: List<ArityDispatchItem.ResolvedParam>,
    val hasExtensionReceiver: Boolean,
    val hasDefaults: Boolean,
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

        if (receiverCast != null) {
            appendLine("$indent@Suppress(\"UNCHECKED_CAST\")")
            if (receiverCast.useConvertToKotlin) {
                appendLine("${indent}val typedReceiver = wrapAsRuntimeValue(receiver).convertToKotlin(${receiverCast.typeName}::class, loc)")
            } else {
                appendLine("${indent}val typedReceiver = receiver as ${receiverCast.typeName}")
            }
        }

        if (hasCallInfo) {
            appendLine("${indent}val callInfo = CallInfo(")
            appendLine("$indent    callLocation = loc,")
            appendLine("$indent    receiverLocation = (receiver as? StringValue)?.location ?: (receiver as? NumberValue)?.location,")
            appendLine("$indent    paramLocations = args.map { arg -> (arg as? StringValue)?.location ?: (arg as? NumberValue)?.location },")
            appendLine("$indent)")
        }

        fun withCallInfo(args: String): String = withCallInfo(args, hasCallInfo)

        if (hasDefaults) {
            val requiredCount = scriptParams.count { !it.hasDefault }
            val firstOptionalIdx = scriptParams.indexOfFirst { it.hasDefault }

            appendLine("${indent}checkArgsSize(fn = \"$scriptName\", args = args, expected = $requiredCount, location = loc)")
            scriptParams.forEach { param ->
                if (!param.hasDefault) {
                    appendLine(
                        "${indent}val ${param.name} = convertArgToKotlin(fn = \"$scriptName\", args = args, index = ${param.index}, cls = ${param.kotlinType}::class, nullable = ${param.isNullable}, loc = loc)${
                            castSuffix(param.castType, param.isNullable)
                        }"
                    )
                }
            }
            appendLine("${indent}wrapAsRuntimeValue(")
            for (level in scriptParams.size downTo firstOptionalIdx + 1) {
                val argsForLevel = scriptParams.take(level)
                val prefix = if (level == scriptParams.size) "if" else "} else if"
                appendLine("$indent    $prefix (args.size >= $level) {")
                argsForLevel.forEach { param ->
                    if (param.hasDefault) {
                        appendLine(
                            "$indent        val ${param.name} = convertArgToKotlin(fn = \"$scriptName\", args = args, index = ${param.index}, cls = ${param.kotlinType}::class, nullable = ${param.isNullable}, loc = loc)${
                                castSuffix(param.castType, param.isNullable)
                            }"
                        )
                    }
                }
                val callArgs = argsForLevel.map { it.name }.joinToString(", ")
                appendLine("$indent        $fnCallPrefix$fnName(${withCallInfo(joinCallArgs(selfArg, callArgs))})")
            }
            val requiredArgs = scriptParams.filter { !it.hasDefault }.map { it.name }.joinToString(", ")
            appendLine("$indent    } else {")
            appendLine("$indent        $fnCallPrefix$fnName(${withCallInfo(joinCallArgs(selfArg, requiredArgs))})")
            appendLine("$indent    }")
            appendLine("$indent)")
        } else {
            appendLine("${indent}checkArgsSize(fn = \"$scriptName\", args = args, expected = ${scriptParams.size}, location = loc)")
            scriptParams.forEach { param ->
                appendLine(
                    "${indent}val ${param.name} = convertArgToKotlin(fn = \"$scriptName\", args = args, index = ${param.index}, cls = ${param.kotlinType}::class, nullable = ${param.isNullable}, loc = loc)${
                        castSuffix(param.castType, param.isNullable)
                    }"
                )
            }
            val callArgs = scriptParams.map { it.name }.joinToString(", ")
            // Note: for hasExtensionReceiver, selfArg is "" (set in buildFileLevelExtItem),
            // so joinCallArgs collapses to just callArgs — both branches produce identical output.
            appendLine("${indent}wrapAsRuntimeValue(${fnCallPrefix}$fnName(${withCallInfo(joinCallArgs(selfArg, callArgs))}))")
        }

        append("}")
    }
}

// ============================================================================
//  Shared utility
// ============================================================================

/** Produces the `as Type` suffix for a convertArgToKotlin call. Returns empty string when the cast is redundant (e.g. `as Any?`). */
internal fun castSuffix(kotlinType: String, isNullable: Boolean): String {
    val fullType = "$kotlinType${if (isNullable) "?" else ""}"
    return if (fullType == "Any?") "" else " as $fullType"
}

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
