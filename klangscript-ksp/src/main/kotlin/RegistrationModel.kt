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
 * A fixed-arity method with NO Kotlin defaults. Renders as a single-line
 * `registerMethod(name, specs) { params -> Owner.fn(callArgs) }` (inside a
 * registerObject/registerType block) or
 * `registerFunction(name, specs) { params -> fn(callArgs) }` (top-level).
 */
data class FixedMethodItem(
    override val scriptName: String,
    override val specsExpr: String,
    val fnCall: String,
    val params: List<Pair<String, String>>,
    val callArgs: String,
    val isTopLevel: Boolean,
) : RegistrationItem() {
    override fun renderRegistration(): String {
        val paramDecl = params.joinToString(", ") { "${it.first}: ${it.second}" }
        return if (isTopLevel) {
            if (params.isEmpty()) {
                "registerFunction(\"$scriptName\", $specsExpr) { $fnCall() }"
            } else {
                "registerFunction(\"$scriptName\", $specsExpr) { $paramDecl -> $fnCall($callArgs) }"
            }
        } else {
            if (params.isEmpty()) {
                "registerMethod(\"$scriptName\", $specsExpr) { $fnCall($callArgs) }"
            } else {
                "registerMethod(\"$scriptName\", $specsExpr) { $paramDecl -> $fnCall($callArgs) }"
            }
        }
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
    val receiverClassName: String?,
    val isTopLevel: Boolean,
) : RegistrationItem() {

    data class ResolvedParam(val name: String, val kotlinType: String, val hasDefault: Boolean, val index: Int)
    data class ReceiverCast(val typeName: String, val useConvertToKotlin: Boolean)

    override fun renderRegistration(): String = buildString {
        val requiredCount = scriptParams.count { !it.hasDefault }
        val firstOptionalIdx = scriptParams.indexOfFirst { it.hasDefault }

        if (isTopLevel) {
            appendLine("registerFunctionWithSpecs(\"$scriptName\", $specsExpr) { args, loc ->")
        } else {
            appendLine("builder.registerExtensionMethodWithSpecs(cls, \"$scriptName\", $specsExpr) { receiver, args, loc ->")
        }

        val indent = if (isTopLevel) "        " else "                "

        if (receiverCast != null) {
            appendLine("$indent@Suppress(\"UNCHECKED_CAST\")")
            if (receiverCast.useConvertToKotlin) {
                appendLine("${indent}val typedReceiver = wrapAsRuntimeValue(receiver).convertToKotlin(${receiverCast.typeName}::class, loc)")
            } else {
                appendLine("${indent}val typedReceiver = receiver as ${receiverCast.typeName}")
            }
        }

        appendLine("${indent}checkArgsSize(fn = \"$scriptName\", args = args, expected = $requiredCount, location = loc)")

        // Required params — always convert
        scriptParams.forEach { param ->
            if (!param.hasDefault) {
                appendLine("${indent}val ${param.name} = convertArgToKotlin(fn = \"$scriptName\", args = args, index = ${param.index}, cls = ${param.kotlinType}::class, nullable = false, loc = loc) as ${param.kotlinType}")
            }
        }

        appendLine("${indent}wrapAsRuntimeValue(")

        // Arity-dispatch chain
        for (level in scriptParams.size downTo firstOptionalIdx + 1) {
            val argsForLevel = scriptParams.take(level)
            val prefix = if (level == scriptParams.size) "if" else "} else if"
            appendLine("$indent    $prefix (args.size >= $level) {")

            argsForLevel.forEach { param ->
                if (param.hasDefault) {
                    appendLine("$indent        val ${param.name} = convertArgToKotlin(fn = \"$scriptName\", args = args, index = ${param.index}, cls = ${param.kotlinType}::class, nullable = false, loc = loc) as ${param.kotlinType}")
                }
            }

            val callArgs = argsForLevel.map { it.name }.joinToString(", ")
            appendLine("$indent        $fnCall(${joinCallArgs(selfArg, callArgs)})")
        }

        val requiredArgs = scriptParams.filter { !it.hasDefault }.map { it.name }.joinToString(", ")
        appendLine("$indent    } else {")
        appendLine("$indent        $fnCall(${joinCallArgs(selfArg, requiredArgs)})")
        appendLine("$indent    }")
        appendLine("$indent)")

        val closing = if (isTopLevel) "    }" else "            }"
        append(closing)
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
    val selfArg: String,
    val fnCallPrefix: String,
) : RegistrationItem() {
    override fun renderRegistration(): String = buildString {
        appendLine("registerExtensionMethodWithSpecs($receiverClassName::class, \"$scriptName\", $specsExpr) { receiver, args, loc ->")

        if (receiverCast != null) {
            appendLine("        @Suppress(\"UNCHECKED_CAST\")")
            if (receiverCast.useConvertToKotlin) {
                appendLine("        val typedReceiver = wrapAsRuntimeValue(receiver).convertToKotlin(${receiverCast.typeName}::class, loc)")
            } else {
                appendLine("        val typedReceiver = receiver as ${receiverCast.typeName}")
            }
        }

        if (hasDefaults) {
            // Delegate to the shared arity-dispatch rendering
            val dispatchItem = ArityDispatchItem(
                scriptName = scriptName,
                specsExpr = specsExpr,
                fnCall = "$fnCallPrefix$fnName",
                selfArg = selfArg,
                scriptParams = scriptParams,
                receiverCast = null, // already emitted above
                receiverClassName = receiverClassName,
                isTopLevel = false,
            )
            // Extract just the body (skip the opening line + receiver cast we already emitted)
            val requiredCount = scriptParams.count { !it.hasDefault }
            val firstOptionalIdx = scriptParams.indexOfFirst { it.hasDefault }
            val indent = "        "

            appendLine("${indent}checkArgsSize(fn = \"$scriptName\", args = args, expected = $requiredCount, location = loc)")
            scriptParams.forEach { param ->
                if (!param.hasDefault) {
                    appendLine("${indent}val ${param.name} = convertArgToKotlin(fn = \"$scriptName\", args = args, index = ${param.index}, cls = ${param.kotlinType}::class, nullable = false, loc = loc) as ${param.kotlinType}")
                }
            }
            appendLine("${indent}wrapAsRuntimeValue(")
            for (level in scriptParams.size downTo firstOptionalIdx + 1) {
                val argsForLevel = scriptParams.take(level)
                val prefix = if (level == scriptParams.size) "if" else "} else if"
                appendLine("$indent    $prefix (args.size >= $level) {")
                argsForLevel.forEach { param ->
                    if (param.hasDefault) {
                        appendLine("$indent        val ${param.name} = convertArgToKotlin(fn = \"$scriptName\", args = args, index = ${param.index}, cls = ${param.kotlinType}::class, nullable = false, loc = loc) as ${param.kotlinType}")
                    }
                }
                val callArgs = argsForLevel.map { it.name }.joinToString(", ")
                appendLine("$indent        $fnCallPrefix$fnName(${joinCallArgs(selfArg, callArgs)})")
            }
            val requiredArgs = scriptParams.filter { !it.hasDefault }.map { it.name }.joinToString(", ")
            appendLine("$indent    } else {")
            appendLine("$indent        $fnCallPrefix$fnName(${joinCallArgs(selfArg, requiredArgs)})")
            appendLine("$indent    }")
            appendLine("$indent)")
        } else {
            appendLine("        checkArgsSize(fn = \"$scriptName\", args = args, expected = ${scriptParams.size}, location = loc)")
            scriptParams.forEach { param ->
                appendLine("        val ${param.name} = convertArgToKotlin(fn = \"$scriptName\", args = args, index = ${param.index}, cls = ${param.kotlinType}::class, nullable = false, loc = loc) as ${param.kotlinType}")
            }
            val callArgs = scriptParams.map { it.name }.joinToString(", ")
            if (hasExtensionReceiver && receiverCast != null) {
                appendLine("        wrapAsRuntimeValue(${fnCallPrefix}$fnName($callArgs))")
            } else {
                appendLine("        wrapAsRuntimeValue($fnName(${joinCallArgs(selfArg, callArgs)}))")
            }
        }

        append("    }")
    }
}

// ============================================================================
//  Shared utility
// ============================================================================

internal fun joinCallArgs(selfArg: String, args: String): String = when {
    selfArg.isEmpty() -> args
    args.isEmpty() -> selfArg.trimEnd(' ', ',')
    else -> "$selfArg$args"
}
