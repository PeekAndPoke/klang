package io.peekandpoke.klang.script.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.validate

/**
 * KSP processor that generates registration and documentation code
 * from KlangScript annotations (@Library, @Object, @TypeExtensions, @Function, @Method).
 */
class KlangScriptProcessor(
    environment: SymbolProcessorEnvironment,
) : SymbolProcessor {

    private val codeGenerator: CodeGenerator = environment.codeGenerator
    private val logger: KSPLogger = environment.logger

    companion object {
        private const val ANN_PKG = "io.peekandpoke.klang.script.annotations.KlangScript"
        private const val ANN_LIBRARY = "$ANN_PKG.Library"
        private const val ANN_OBJECT = "$ANN_PKG.Object"
        private const val ANN_TYPE_EXTENSIONS = "$ANN_PKG.TypeExtensions"
        private const val ANN_FUNCTION = "$ANN_PKG.Function"
        private const val ANN_METHOD = "$ANN_PKG.Method"

        /** Maps Kotlin/RuntimeValue types to KlangScript display names. */
        private val TYPE_DISPLAY_NAMES = mapOf(
            "Double" to "Number",
            "Float" to "Number",
            "Int" to "Number",
            // Long intentionally excluded — it boxes in Kotlin/JS. Use Int or Double instead.
            "String" to "String",
            "Boolean" to "Boolean",
            "NumberValue" to "Number",
            "StringValue" to "String",
            "BooleanValue" to "Boolean",
            "ArrayValue" to "Array",
            "ObjectValue" to "Object",
            "NullValue" to "Null",
        )

        private const val CALL_INFO_TYPE = "CallInfo"

        /**
         * Max parameters supported by the inline `registerMethod<P1, ..., R>` overloads.
         * Beyond this, fall back to the spec-aware path.
         */
        private const val MAX_FIXED_PARAMS_METHOD = 3

        /**
         * Max parameters supported by the inline `registerFunction<P1, ..., R>` overloads.
         * Beyond this, fall back to the spec-aware path.
         */
        private const val MAX_FIXED_PARAMS_FUNCTION = 2

        /** Kotlin hard keywords that must be backticked when used as identifiers. */
        private val KOTLIN_HARD_KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
            "if", "in", "interface", "is", "null", "object", "package", "return",
            "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
            "var", "when", "while",
        )
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        logger.info("KlangScriptProcessor: Starting processing")

        // Collect all annotated symbols
        val objectClasses = resolver.getSymbolsWithAnnotation(ANN_OBJECT)
            .filterIsInstance<KSClassDeclaration>().toList()
        val typeExtClasses = resolver.getSymbolsWithAnnotation(ANN_TYPE_EXTENSIONS)
            .filterIsInstance<KSClassDeclaration>().toList()
        val topLevelFunctions = resolver.getSymbolsWithAnnotation(ANN_FUNCTION)
            .filterIsInstance<KSFunctionDeclaration>().toList()

        logger.info("KlangScriptProcessor: Found ${objectClasses.size} @Object, ${typeExtClasses.size} @TypeExtensions, ${topLevelFunctions.size} @Function")

        // Validate orphaned @Method annotations (on classes without @Object or @TypeExtensions)
        val validParents = (objectClasses + typeExtClasses).toSet()
        val allMethods = resolver.getSymbolsWithAnnotation(ANN_METHOD)
            .filterIsInstance<KSFunctionDeclaration>().toList()
        for (method in allMethods) {
            val parent = method.parentDeclaration
            if (parent is KSClassDeclaration && parent !in validParents) {
                logger.error(
                    "@Method '${method.simpleName.asString()}' is inside '${parent.simpleName.asString()}' " +
                            "which has neither @Object nor @TypeExtensions — it will be ignored",
                    method
                )
            }
        }

        if (objectClasses.isEmpty() && typeExtClasses.isEmpty() && topLevelFunctions.isEmpty()) {
            return emptyList()
        }

        // Group by library
        val libraryEntries = mutableMapOf<String, LibraryEntries>()

        for (cls in objectClasses) {
            val library = getLibraryName(cls)
            if (library == null) {
                logger.error("@Object class '${cls.simpleName.asString()}' must have @Library annotation", cls)
                continue
            }
            if (cls.classKind != ClassKind.OBJECT) {
                logger.warn("@Object should be used on a Kotlin 'object', not a '${cls.classKind}': ${cls.simpleName.asString()}", cls)
            }
            val objectName = getAnnotationStringArg(cls, ANN_OBJECT, "name")
                .let { if (it.isNullOrEmpty()) cls.simpleName.asString() else it }
            val methods = collectMethods(cls)

            libraryEntries.getOrPut(library) { LibraryEntries() }
                .objects.add(ObjectEntry(objectName, cls, methods))
        }

        for (cls in typeExtClasses) {
            val library = getLibraryName(cls)
            if (library == null) {
                logger.error("@TypeExtensions class '${cls.simpleName.asString()}' must have @Library annotation", cls)
                continue
            }
            val typeArg = getAnnotationClassArg(cls, ANN_TYPE_EXTENSIONS, "type")
            if (typeArg == null) {
                logger.error("@TypeExtensions must specify a type", cls)
                continue
            }
            val methods = collectMethods(cls)

            libraryEntries.getOrPut(library) { LibraryEntries() }
                .typeExtensions.add(TypeExtEntry(typeArg, cls, methods))
        }

        for (fn in topLevelFunctions) {
            val library = getLibraryNameForFunction(fn)
            if (library == null) {
                logger.error("@Function '${fn.simpleName.asString()}' must have @Library (on file or enclosing class)", fn)
                continue
            }
            val functionName = getAnnotationStringArg(fn, ANN_FUNCTION, "name")
                .let { if (it.isNullOrEmpty()) fn.simpleName.asString() else it }

            // Determine receiver class for extension-method registration:
            //   1. Explicit annotation param: @Function(receiver = Foo::class) — takes priority.
            //   2. Kotlin extension receiver: fun Foo.bar() — auto-detected.
            //   3. Neither → top-level function.
            val explicitReceiver = getAnnotationClassArg(fn, ANN_FUNCTION, "receiver")
            val isExplicitUnit = explicitReceiver?.simpleName?.asString() == "Unit"

            val receiverClass = when {
                explicitReceiver != null && !isExplicitUnit -> explicitReceiver
                else -> {
                    val extType = fn.extensionReceiver?.resolve()
                    if (extType != null) {
                        var decl = extType.declaration
                        while (decl is KSTypeAlias) decl = (decl as KSTypeAlias).type.resolve().declaration
                        val cls = decl as? KSClassDeclaration

                        // Auto-map Kotlin platform types to their KlangScript runtime
                        // value types (e.g. String → StringValue). This lets sprudel write
                        // `fun String.gain(...)` without needing `receiver = StringValue::class`.
                        val mapped = cls?.let { mapReceiverType(resolver, it) }
                        mapped ?: cls
                    } else null
                }
            }

            if (receiverClass != null) {
                libraryEntries.getOrPut(library) { LibraryEntries() }
                    .typeExtensions.add(
                        TypeExtEntry(
                            typeDecl = receiverClass,
                            cls = fn.parentDeclaration as? KSClassDeclaration
                                ?: receiverClass,
                            methods = listOf(MethodEntry(functionName, fn)),
                        )
                    )
            } else {
                libraryEntries.getOrPut(library) { LibraryEntries() }
                    .functions.add(FunctionEntry(functionName, fn))
            }
        }

        // Generate code per library
        for ((libraryName, entries) in libraryEntries) {
            generateLibraryCode(libraryName, entries)
        }

        // Return unprocessed symbols for deferred processing
        val unprocessed = mutableListOf<KSAnnotated>()
        unprocessed.addAll(objectClasses.filterNot { it.validate() })
        unprocessed.addAll(typeExtClasses.filterNot { it.validate() })
        unprocessed.addAll(topLevelFunctions.filterNot { it.validate() })
        return unprocessed
    }

    // ===== Data classes for collected entries =====

    private data class LibraryEntries(
        val objects: MutableList<ObjectEntry> = mutableListOf(),
        val typeExtensions: MutableList<TypeExtEntry> = mutableListOf(),
        val functions: MutableList<FunctionEntry> = mutableListOf(),
    )

    private data class ObjectEntry(
        val name: String,
        val cls: KSClassDeclaration,
        val methods: List<MethodEntry>,
    )

    private data class TypeExtEntry(
        val typeDecl: KSClassDeclaration,
        val cls: KSClassDeclaration,
        val methods: List<MethodEntry>,
    )

    private data class FunctionEntry(
        val name: String,
        val fn: KSFunctionDeclaration,
    )

    private data class MethodEntry(
        val name: String,
        val fn: KSFunctionDeclaration,
    )

    // ===== Annotation helpers =====

    private fun getLibraryName(cls: KSClassDeclaration): String? {
        return getAnnotationStringArg(cls, ANN_LIBRARY, "name")
    }

    private fun getLibraryNameForFunction(fn: KSFunctionDeclaration): String? {
        // Check enclosing class first
        val parent = fn.parentDeclaration
        if (parent is KSClassDeclaration) {
            val lib = getAnnotationStringArg(parent, ANN_LIBRARY, "name")
            if (lib != null) return lib
        }
        // Check file-level annotation
        val file = fn.containingFile ?: return null
        for (ann in file.annotations) {
            val annType = ann.annotationType.resolve().declaration.qualifiedName?.asString()
            if (annType == ANN_LIBRARY) {
                return ann.arguments.firstOrNull { it.name?.asString() == "name" }?.value as? String
            }
        }
        return null
    }

    private fun getAnnotationStringArg(decl: KSAnnotated, annotationFqn: String, argName: String): String? {
        for (ann in decl.annotations) {
            val annType = ann.annotationType.resolve().declaration.qualifiedName?.asString()
            if (annType == annotationFqn) {
                return ann.arguments.firstOrNull { it.name?.asString() == argName }?.value as? String
            }
        }
        return null
    }

    private fun getAnnotationClassArg(decl: KSAnnotated, annotationFqn: String, argName: String): KSClassDeclaration? {
        for (ann in decl.annotations) {
            val annType = ann.annotationType.resolve().declaration.qualifiedName?.asString()
            if (annType == annotationFqn) {
                val value = ann.arguments.firstOrNull { it.name?.asString() == argName }?.value
                if (value is KSType) {
                    // Follow through typealiases to the underlying class declaration.
                    var declaration = value.declaration
                    while (declaration is KSTypeAlias) {
                        declaration = declaration.type.resolve().declaration
                    }
                    return declaration as? KSClassDeclaration
                }
            }
        }
        return null
    }

    private fun collectMethods(cls: KSClassDeclaration): List<MethodEntry> {
        return cls.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { fn ->
                fn.annotations.any { ann ->
                    ann.annotationType.resolve().declaration.qualifiedName?.asString() == ANN_METHOD
                }
            }
            .map { fn ->
                val methodName = getAnnotationStringArg(fn, ANN_METHOD, "name")
                    .let { if (it.isNullOrEmpty()) fn.simpleName.asString() else it }
                MethodEntry(methodName, fn)
            }
            .toList()
    }

    // ===== Code generation =====

    private fun generateLibraryCode(libraryName: String, entries: LibraryEntries) {
        val sourceFiles = buildList {
            entries.objects.forEach { addAll(listOfNotNull(it.cls.containingFile)) }
            entries.typeExtensions.forEach { addAll(listOfNotNull(it.cls.containingFile)) }
            entries.functions.forEach { addAll(listOfNotNull(it.fn.containingFile)) }
        }.distinct()

        val capitalizedName = libraryName.replaceFirstChar { it.uppercase() }
        val packageName = "io.peekandpoke.klang.script.generated"
        val fileName = "Generated${capitalizedName}Registration"

        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = true, *sourceFiles.toTypedArray()),
            packageName = packageName,
            fileName = fileName,
            extensionName = "kt",
        )

        file.bufferedWriter().use { writer ->
            writer.write(buildGeneratedCode(libraryName, capitalizedName, packageName, entries))
        }

        logger.info("KlangScriptProcessor: Generated $fileName for library '$libraryName'")
    }

    private fun buildGeneratedCode(
        libraryName: String,
        capitalizedName: String,
        packageName: String,
        entries: LibraryEntries,
    ): String = buildString {
        appendLine("// Generated by KlangScriptProcessor — DO NOT EDIT")
        appendLine("@file:Suppress(\"UNCHECKED_CAST\", \"UNUSED_PARAMETER\", \"UnusedImport\")")
        appendLine()
        appendLine("package $packageName")
        appendLine()
        appendLine("import io.peekandpoke.klang.script.KlangScriptLibrary")
        appendLine("import io.peekandpoke.klang.script.ast.CallInfo")
        appendLine("import io.peekandpoke.klang.script.builder.*")
        appendLine("import io.peekandpoke.klang.script.runtime.*")
        appendLine("import io.peekandpoke.klang.script.types.*")
        appendLine()

        // Collect imports for annotated classes and file-level functions
        val imports = mutableSetOf<String>()

        /**
         * Collect imports for every declaration referenced by a function's signature —
         * return type and parameter types, drilling into type arguments (e.g. generics
         * and function types like `(SprudelPatternEvent) -> Boolean`). This ensures
         * cast sites like `as ((SprudelPatternEvent) -> Boolean)` compile without
         * needing to list every type manually.
         */
        // Types that are always imported at the top — don't re-add them.
        val alreadyImported = setOf(
            "io.peekandpoke.klang.script.ast.CallInfo",
        )

        fun collectTypeImports(fn: KSFunctionDeclaration) {
            fun addFromType(type: KSType?) {
                if (type == null) return
                val decl = type.declaration
                // Skip kotlin.* built-ins; they don't need imports.
                val qn = decl.qualifiedName?.asString()
                if (qn != null &&
                    !qn.startsWith("kotlin.") &&
                    !qn.startsWith("java.lang.") &&
                    qn !in alreadyImported
                ) {
                    imports.add(qn)
                }
                // Recurse into type arguments (covers generics and function types).
                type.arguments.forEach { arg -> addFromType(arg.type?.resolve()) }
            }
            fn.returnType?.resolve()?.let { addFromType(it) }
            fn.extensionReceiver?.resolve()?.let { addFromType(it) }
            fn.parameters.forEach { p -> addFromType(p.type.resolve()) }
        }

        entries.objects.forEach { obj ->
            obj.cls.qualifiedName?.asString()?.let { imports.add(it) }
        }
        entries.typeExtensions.forEach { ext ->
            ext.cls.qualifiedName?.asString()?.let { imports.add(it) }
            ext.typeDecl.qualifiedName?.asString()?.let { imports.add(it) }
            // File-level functions routed as type extensions need their own import
            // (class-level methods are members and don't need separate imports).
            // Also import typealiases used as receiver types in casts.
            ext.methods.forEach { method ->
                if (method.fn.parentDeclaration !is KSClassDeclaration) {
                    method.fn.qualifiedName?.asString()?.let { imports.add(it) }
                    // Import typealiases from the extension receiver OR the first
                    // param so the generated `receiver as PatternMapperFn` cast compiles.
                    val extReceiverType = method.fn.extensionReceiver?.resolve()
                    if (extReceiverType?.declaration is KSTypeAlias) {
                        extReceiverType.declaration.qualifiedName?.asString()?.let { imports.add(it) }
                    }
                    val firstParamType = method.fn.parameters.firstOrNull()?.type?.resolve()
                    if (firstParamType?.declaration is KSTypeAlias) {
                        firstParamType.declaration.qualifiedName?.asString()?.let { imports.add(it) }
                    }
                }
                collectTypeImports(method.fn)
            }
        }
        entries.functions.forEach { fn ->
            fn.fn.qualifiedName?.asString()?.let { imports.add(it) }
            collectTypeImports(fn.fn)
        }
        imports.sorted().forEach { appendLine("import ${escapeQualifiedNameKeywords(it)}") }
        if (imports.isNotEmpty()) appendLine()

        // ── Duplicate (name, receiver) collision check ───────────────────────
        // Prevents silently overwriting a registration when two annotated
        // Kotlin overloads resolve to the same script-visible (name, receiver).
        data class RegKey(val scriptName: String, val receiver: String?)

        val seen = mutableMapOf<RegKey, String>()
        fun checkCollision(scriptName: String, receiver: String?, sourceDesc: String) {
            val key = RegKey(scriptName, receiver)
            val prior = seen.put(key, sourceDesc)
            if (prior != null) {
                logger.error(
                    "Duplicate KlangScript registration: '$scriptName' on receiver '${receiver ?: "<top-level>"}' " +
                            "is registered by both [$prior] and [$sourceDesc]. " +
                            "Only one @KlangScript.Method / @KlangScript.Function per (name, receiver) is allowed."
                )
            }
        }

        for (obj in entries.objects) {
            for (method in obj.methods) {
                checkCollision(
                    method.name,
                    obj.cls.simpleName.asString(),
                    "${obj.cls.simpleName.asString()}.${method.fn.simpleName.asString()}"
                )
            }
        }
        for (ext in entries.typeExtensions) {
            for (method in ext.methods) {
                checkCollision(
                    method.name,
                    ext.typeDecl.simpleName.asString(),
                    "${ext.cls.simpleName.asString()}.${method.fn.simpleName.asString()}"
                )
            }
        }
        for (fn in entries.functions) {
            checkCollision(fn.name, null, fn.fn.simpleName.asString())
        }

        // ── Pass 2: Build RegistrationItems and render ────────────────────
        //
        // The JVM has a 64KB limit per method. For large libraries (e.g. sprudel),
        // the full registration body overflows that limit when emitted as a single
        // function. Collect each logical block (object / type extension / top-level
        // function) as its own rendered snippet, then distribute snippets across
        // helper chunks so no individual helper method exceeds the limit.
        val registrationBlocks = mutableListOf<String>()

        // Objects
        for (obj in entries.objects) {
            val normalMethods = obj.methods.filter { !isRawArgsMethod(it.fn) }
            val rawArgsMethods = obj.methods.filter { isRawArgsMethod(it.fn) }

            val block = buildString {
                appendLine()
                appendLine("    // @Object(\"${obj.name}\") on ${obj.cls.simpleName.asString()}")
                appendLine("    registerObject(\"${obj.name}\", ${obj.cls.simpleName.asString()}) {")
                for (method in normalMethods) {
                    val item = buildMethodItem(method, obj.cls, isTypeExtension = false)
                    appendLine(item.renderRegistration().prependIndent("        "))
                }
                appendLine("    }")

                for (method in rawArgsMethods) {
                    val item = buildRawArgsItem(method, obj.cls)
                    appendLine(item.renderRegistration().prependIndent("    "))
                }
            }
            registrationBlocks.add(block)
        }

        // Type extensions
        for (ext in entries.typeExtensions) {
            val typeName = ext.typeDecl.simpleName.asString()
            val allFileLevelMethods = ext.methods.all { it.fn.parentDeclaration !is KSClassDeclaration }

            val block = buildString {
                appendLine()
                appendLine("    // @TypeExtensions($typeName::class) on ${ext.cls.simpleName.asString()}")

                if (allFileLevelMethods) {
                    for (method in ext.methods) {
                        val item = buildFileLevelExtItem(method, ext.typeDecl)
                        appendLine(item.renderRegistration().prependIndent("    "))
                    }
                } else {
                    appendLine("    registerType<$typeName> {")
                    for (method in ext.methods) {
                        val item = buildMethodItem(method, ext.cls, isTypeExtension = true)
                        appendLine(item.renderRegistration().prependIndent("        "))
                    }
                    appendLine("    }")
                }
            }
            registrationBlocks.add(block)
        }

        // Top-level functions
        for (fn in entries.functions) {
            val block = buildString {
                appendLine()
                appendLine("    // @Function on ${fn.fn.simpleName.asString()}")
                val item = buildTopLevelFunctionItem(fn)
                appendLine(item.renderRegistration().prependIndent("    "))
            }
            registrationBlocks.add(block)
        }

        // Distribute blocks across chunks, keeping each chunk's body under a conservative
        // byte budget that translates to well under the JVM 64KB method-code limit.
        // Source size is a loose proxy for bytecode size; 20_000 chars per chunk keeps
        // headroom for generics, when-dispatch, and indentation overhead.
        val chunkSizeCharsBudget = 20_000
        val chunks = mutableListOf<MutableList<String>>()
        var currentChunk = mutableListOf<String>()
        var currentSize = 0
        for (block in registrationBlocks) {
            if (currentSize + block.length > chunkSizeCharsBudget && currentChunk.isNotEmpty()) {
                chunks.add(currentChunk)
                currentChunk = mutableListOf()
                currentSize = 0
            }
            currentChunk.add(block)
            currentSize += block.length
        }
        if (currentChunk.isNotEmpty()) chunks.add(currentChunk)

        // Emit each chunk as a private helper function.
        chunks.forEachIndexed { idx, chunkBlocks ->
            appendLine("private fun KlangScriptExtensionBuilder.register${capitalizedName}GeneratedChunk$idx() {")
            chunkBlocks.forEach { append(it) }
            appendLine("}")
            appendLine()
        }

        // === Main registration function — delegates to chunk helpers ===
        appendLine("/**")
        appendLine(" * Registers all @KlangScript annotated symbols for the '$libraryName' library.")
        appendLine(" */")
        appendLine("fun KlangScriptExtensionBuilder.register${capitalizedName}Generated() {")
        chunks.forEachIndexed { idx, _ ->
            appendLine("    register${capitalizedName}GeneratedChunk$idx()")
        }

        // Auto-register docs when called from a library builder
        appendLine()
        appendLine("    // Auto-register documentation for code completion")
        appendLine("    (this as? KlangScriptLibrary.Builder)?.docs { registerAll(generated${capitalizedName}Docs) }")

        appendLine("}")

        // === Documentation ===
        appendLine()
        generateDocsCode(libraryName, capitalizedName, entries)
    }

    // ===== Pass 2: Build RegistrationItems from entries =====

    private fun buildMethodItem(
        method: MethodEntry,
        ownerCls: KSClassDeclaration,
        isTypeExtension: Boolean,
    ): RegistrationItem {
        val fn = method.fn
        val allParams = getScriptParams(fn)
        val hasCallInfo = hasCallInfoParam(fn)
        val ownerName = ownerCls.simpleName.asString()
        val isFileLevelFn = fn.parentDeclaration !is KSClassDeclaration
        val fnQualifier = if (isFileLevelFn) "" else "$ownerName."

        val scriptParams = if (isTypeExtension && allParams.isNotEmpty()) allParams.drop(1) else allParams
        val selfArg = if (isTypeExtension) {
            if (isFileLevelFn) "typedReceiver, " else "this, "
        } else ""

        val isVararg = scriptParams.any { it.isVararg }
        val specsExpr = paramSpecsListExpression(scriptParams)

        // Vararg → legacy helper
        if (isVararg) {
            val paramType = getVarargComponentType(scriptParams.first { it.isVararg })
            val returnType = resolveCastType(fn.returnType?.resolve())
            val fnCall =
                "$fnQualifier${escapeIdentifier(fn.simpleName.asString())}(${selfArg}*args.toTypedArray()${if (hasCallInfo) ", callInfo = callInfo" else ""})"
            return VarargItem(
                scriptName = method.name,
                specsExpr = specsExpr,
                paramType = paramType,
                returnType = returnType,
                fnCallWithArgs = fnCall,
                hasCallInfo = hasCallInfo,
                isTopLevel = false,
            )
        }

        val hasDefaults = scriptParams.any { it.hasDefault }

        // Use the spec-aware path when:
        //  - we have Kotlin defaults (need arity dispatch),
        //  - we need CallInfo (only spec-aware path threads `loc`),
        //  - or arity exceeds the inline overload set (only spec-aware path is unbounded).
        val needsSpecAware = hasDefaults || hasCallInfo || scriptParams.size > MAX_FIXED_PARAMS_METHOD
        if (needsSpecAware) {
            val receiverCast = if (isTypeExtension) {
                val receiverTypeName = fn.parameters.firstOrNull()?.type?.resolve()?.let { resolveKotlinType(it, followTypeAlias = false) }
                receiverTypeName?.let { ArityDispatchItem.ReceiverCast(it, useConvertToKotlin = false) }
            } else null

            return ArityDispatchItem(
                scriptName = method.name,
                specsExpr = specsExpr,
                fnCall = "$fnQualifier${escapeIdentifier(fn.simpleName.asString())}",
                selfArg = if (isTypeExtension) "typedReceiver, " else "",
                scriptParams = scriptParams.mapIndexed { i, p ->
                    ArityDispatchItem.ResolvedParam(
                        name = p.name?.asString() ?: "p$i",
                        kotlinType = resolveKotlinType(p.type.resolve()),
                        castType = resolveCastType(p.type.resolve()),
                        hasDefault = p.hasDefault,
                        isNullable = p.type.resolve().isMarkedNullable,
                        index = i,
                    )
                },
                receiverCast = receiverCast,
                isTopLevel = false,
                hasCallInfo = hasCallInfo,
            )
        }

        val params = scriptParams.map { p ->
            (p.name?.asString() ?: "p") to resolveCastType(p.type.resolve())
        }
        // Use named-arg syntax so Kotlin parameter order can differ from script
        // order (e.g. callInfo in the middle) without generated code caring.
        val callArgsStr = scriptParams.joinToString(", ") { p ->
            val name = p.name?.asString() ?: "p"
            "$name = $name"
        }
        val fullCallArgs = if (isTypeExtension) {
            if (callArgsStr.isEmpty()) "this" else "this, $callArgsStr"
        } else callArgsStr

        return FixedMethodItem(
            scriptName = method.name,
            specsExpr = specsExpr,
            fnCall = "$fnQualifier${escapeIdentifier(fn.simpleName.asString())}",
            params = params,
            returnType = resolveCastType(fn.returnType?.resolve()),
            callArgs = fullCallArgs,
            isTopLevel = false,
        )
    }

    private fun buildRawArgsItem(method: MethodEntry, ownerCls: KSClassDeclaration): RawArgsItem {
        val fn = method.fn
        val ownerName = ownerCls.simpleName.asString()
        val params = fn.parameters
        val hasLocation = params.size >= 2 &&
                params[1].type.resolve().declaration.simpleName.asString() == "SourceLocation"
        return RawArgsItem(
            scriptName = method.name,
            specsExpr = "emptyList()",
            ownerName = ownerName,
            fnName = escapeIdentifier(fn.simpleName.asString()),
            hasLocation = hasLocation,
        )
    }

    private fun buildFileLevelExtItem(method: MethodEntry, typeDecl: KSClassDeclaration): RegistrationItem {
        val fn = method.fn
        val allParams = getScriptParams(fn)
        val typeName = typeDecl.simpleName.asString()
        val fnName = escapeIdentifier(fn.simpleName.asString())
        val hasExtensionReceiver = fn.extensionReceiver != null

        val scriptParams = if (hasExtensionReceiver) allParams else {
            if (allParams.isNotEmpty()) allParams.drop(1) else allParams
        }

        val specsExpr = paramSpecsListExpression(scriptParams)
        val hasDefaults = scriptParams.any { it.hasDefault }
        val hasCallInfo = hasCallInfoParam(fn)
        val isVararg = scriptParams.any { it.isVararg }

        // Vararg file-level extension → legacy helper (same as non-file-level ext).
        // The arity-dispatch path below can't handle vararg params cleanly because
        // named-arg calls to varargs are prohibited unless using spread syntax.
        if (isVararg) {
            val paramType = getVarargComponentType(scriptParams.first { it.isVararg })
            val returnType = resolveCastType(fn.returnType?.resolve())
            // File-level ext has an explicit receiver param that we need to pass through.
            val selfArgForVararg = if (hasExtensionReceiver) "typedReceiver." else ""
            val fnCall = if (hasExtensionReceiver) {
                "${selfArgForVararg}$fnName(*args.toTypedArray()${if (hasCallInfo) ", callInfo = callInfo" else ""})"
            } else {
                "$fnName(typedReceiver, *args.toTypedArray()${if (hasCallInfo) ", callInfo = callInfo" else ""})"
            }
            // For the `cls =` argument of convertArgToKotlin we must pass a KClass,
            // but function types like `(SprudelPattern) -> SprudelPattern` can't use
            // `::class`. In that case pass `Function1::class` and let the unchecked
            // cast on the list give the structural type.
            val clsForConvert = varargClsLiteral(scriptParams.first { it.isVararg }.type.resolve())

            // Render inline as a raw block — VarargItem is top-level-shaped
            // (registerVarargFunction*) and doesn't support the file-level
            // receiver cast path.
            val rendered = buildString {
                appendLine("registerExtensionMethodWithSpecs(")
                appendLine("    receiver = $typeName::class,")
                appendLine("    name = \"${method.name}\",")
                appendLine("    paramSpecs = emptyList(),")
                appendLine(") { receiver, args, loc ->")
                appendLine("    @Suppress(\"UNCHECKED_CAST\")")
                val recvType = if (hasExtensionReceiver) {
                    fn.extensionReceiver?.resolve()?.let { resolveKotlinType(it, followTypeAlias = false) }
                } else {
                    fn.parameters.firstOrNull()?.type?.resolve()?.let { resolveKotlinType(it, followTypeAlias = false) }
                } ?: "Any"
                appendLine("    val typedReceiver = wrapAsRuntimeValue(receiver).convertToKotlin($recvType::class, loc)")
                if (hasCallInfo) {
                    appendLine("    val callInfo = CallInfo(")
                    appendLine("        callLocation = loc,")
                    appendLine("        receiverLocation = (receiver as? StringValue)?.location ?: (receiver as? NumberValue)?.location,")
                    appendLine("        paramLocations = args.map { arg -> (arg as? StringValue)?.location ?: (arg as? NumberValue)?.location },")
                    appendLine("    )")
                }
                appendLine("    val kotlinArgs = List(args.size) { index ->")
                appendLine("        convertArgToKotlin(fn = \"${method.name}\", args = args, index = index, cls = $clsForConvert, nullable = true, loc = loc)")
                appendLine("    } as List<$paramType>")
                val callExpr = if (hasExtensionReceiver) {
                    "typedReceiver.$fnName(*kotlinArgs.toTypedArray()${if (hasCallInfo) ", callInfo = callInfo" else ""})"
                } else {
                    "$fnName(typedReceiver, *kotlinArgs.toTypedArray()${if (hasCallInfo) ", callInfo = callInfo" else ""})"
                }
                appendLine("    wrapAsRuntimeValue($callExpr)")
                append("}")
            }
            return RawBlockItem(scriptName = method.name, specsExpr = "emptyList()", rendered = rendered)
        }

        val receiverTypeName = if (hasExtensionReceiver) {
            fn.extensionReceiver?.resolve()?.let { resolveKotlinType(it, followTypeAlias = false) }
        } else {
            fn.parameters.firstOrNull()?.type?.resolve()?.let { resolveKotlinType(it, followTypeAlias = false) }
        }

        val receiverCast = receiverTypeName?.let {
            ArityDispatchItem.ReceiverCast(it, useConvertToKotlin = hasExtensionReceiver)
        }

        val selfArg = if (receiverTypeName != null) {
            if (hasExtensionReceiver) "" else "typedReceiver, "
        } else ""
        val fnCallPrefix = if (hasExtensionReceiver && receiverTypeName != null) "typedReceiver." else ""

        return FileLevelExtItem(
            scriptName = method.name,
            specsExpr = specsExpr,
            receiverClassName = typeName,
            receiverCast = receiverCast,
            fnName = fnName,
            scriptParams = scriptParams.mapIndexed { i, p ->
                ArityDispatchItem.ResolvedParam(
                    name = p.name?.asString() ?: "p$i",
                    kotlinType = resolveKotlinType(p.type.resolve()),
                    castType = resolveCastType(p.type.resolve()),
                    hasDefault = p.hasDefault,
                    isNullable = p.type.resolve().isMarkedNullable,
                    index = i,
                )
            },
            hasExtensionReceiver = hasExtensionReceiver,
            hasDefaults = hasDefaults,
            hasCallInfo = hasCallInfoParam(fn),
            selfArg = selfArg,
            fnCallPrefix = fnCallPrefix,
        )
    }

    private fun buildTopLevelFunctionItem(entry: FunctionEntry): RegistrationItem {
        val fn = entry.fn
        val params = getScriptParams(fn)
        val hasCallInfo = hasCallInfoParam(fn)
        val isVararg = params.any { it.isVararg }
        val hasDefaults = params.any { it.hasDefault }
        val fnName = escapeIdentifier(fn.simpleName.asString())
        val specsExpr = paramSpecsListExpression(params)

        // Vararg → legacy
        if (isVararg) {
            val paramType = getVarargComponentType(params.first { it.isVararg })
            val returnType = resolveCastType(fn.returnType?.resolve())
            val fnCall = "$fnName(*args.toTypedArray()${if (hasCallInfo) ", callInfo = callInfo" else ""})"
            return VarargItem(
                scriptName = entry.name,
                specsExpr = specsExpr,
                paramType = paramType,
                returnType = returnType,
                fnCallWithArgs = fnCall,
                hasCallInfo = hasCallInfo,
                isTopLevel = true,
            )
        }

        // Use the spec-aware path when:
        //  - we have Kotlin defaults (need arity dispatch),
        //  - we need CallInfo (only spec-aware path threads `loc`),
        //  - or arity exceeds the inline overload set (only spec-aware path is unbounded).
        val needsSpecAware = hasDefaults || hasCallInfo || params.size > MAX_FIXED_PARAMS_FUNCTION
        if (needsSpecAware) {
            return ArityDispatchItem(
                scriptName = entry.name,
                specsExpr = specsExpr,
                fnCall = fnName,
                selfArg = "",
                scriptParams = params.mapIndexed { i, p ->
                    ArityDispatchItem.ResolvedParam(
                        name = p.name?.asString() ?: "p$i",
                        kotlinType = resolveKotlinType(p.type.resolve()),
                        castType = resolveCastType(p.type.resolve()),
                        hasDefault = p.hasDefault,
                        isNullable = p.type.resolve().isMarkedNullable,
                        index = i,
                    )
                },
                receiverCast = null,
                isTopLevel = true,
                hasCallInfo = hasCallInfo,
            )
        }

        val paramPairs = params.map { p ->
            (p.name?.asString() ?: "p") to resolveCastType(p.type.resolve())
        }
        // Use named-arg syntax so Kotlin parameter order can differ from script
        // order (e.g. callInfo in the middle) without generated code caring.
        val callArgs = params.joinToString(", ") { p ->
            val name = p.name?.asString() ?: "p"
            "$name = $name"
        }

        return FixedMethodItem(
            scriptName = entry.name,
            specsExpr = specsExpr,
            fnCall = fnName,
            params = paramPairs,
            returnType = resolveCastType(fn.returnType?.resolve()),
            callArgs = callArgs,
            isTopLevel = true,
        )
    }

    /**
     * Detect raw-args methods: first param is `List<RuntimeValue>`. These bypass the
     * spec/dispatch machinery — the function takes the args list directly.
     */
    private fun isRawArgsMethod(fn: KSFunctionDeclaration): Boolean {
        val params = fn.parameters
        if (params.isEmpty()) return false
        val firstType = params.first().type.resolve()
        if (firstType.declaration.simpleName.asString() != "List") return false
        val elementType = firstType.arguments.firstOrNull()?.type?.resolve()?.declaration?.simpleName?.asString()
        return elementType == "RuntimeValue"
    }

    // ===== Docs generation =====

    private data class DocItem(
        val scriptName: String,
        val receiver: String?,
        val fn: KSFunctionDeclaration,
        val isTypeExtension: Boolean = false,
        val isRawArgs: Boolean = false,
    )

    private fun StringBuilder.generateDocsCode(
        libraryName: String,
        capitalizedName: String,
        entries: LibraryEntries,
    ) {

        val docItems = mutableListOf<DocItem>()

        for (obj in entries.objects) {
            for (method in obj.methods) {
                docItems.add(DocItem(method.name, obj.name, method.fn, isRawArgs = isRawArgsMethod(method.fn)))
            }
        }
        for (ext in entries.typeExtensions) {
            val displayName = typeDisplayName(ext.typeDecl.simpleName.asString())
            for (method in ext.methods) {
                docItems.add(DocItem(method.name, displayName, method.fn, isTypeExtension = true))
            }
        }
        for (fn in entries.functions) {
            docItems.add(DocItem(fn.name, null, fn.fn))
        }

        if (docItems.isEmpty() && entries.objects.isEmpty()) return

        // Group by script name (handles overloads)
        val grouped = docItems.groupBy { it.scriptName }
        val allNames = grouped.keys.sorted()
        val chunks = allNames.chunked(8)

        chunks.forEachIndexed { chunkIdx, chunk ->
            appendLine("private fun generated${capitalizedName}DocsChunk$chunkIdx() = mapOf(")
            chunk.forEachIndexed { entryIdx, name ->
                val items = grouped[name]!!
                append(generateDocEntry(name, libraryName, items))
                if (entryIdx < chunk.size - 1) appendLine(",")
            }
            appendLine()
            appendLine(")")
            appendLine()
        }

        // Generate object-level symbol entries (e.g., "Osc", "Math", "Object")
        if (entries.objects.isNotEmpty()) {
            appendLine("private fun generated${capitalizedName}DocsObjects() = mapOf(")
            entries.objects.forEachIndexed { index, obj ->
                val kdoc = KDocParser.parse(obj.cls.docString)
                val description = kdoc.description.escapeForRawString()
                val category = kdoc.category ?: "object"
                val tagsString = kdoc.tags.joinToString(", ") { "\"$it\"" }
                appendLine()
                appendLine("    \"${obj.name}\" to KlangSymbol(")
                appendLine("        name = \"${obj.name}\",")
                appendLine("        category = \"$category\",")
                appendLine("        tags = listOf($tagsString),")
                appendLine("        aliases = listOf(),")
                appendLine("        library = \"$libraryName\",")
                appendLine("        variants = listOf(")
                appendLine("            KlangProperty(")
                appendLine("                name = \"${obj.name}\",")
                appendLine("                type = KlangType(simpleName = \"${obj.name}\"),")
                appendLine("                description = \"\"\"$description\"\"\",")
                appendLine("                library = \"$libraryName\",")
                appendLine("            )")
                appendLine("        )")
                if (index < entries.objects.size - 1) {
                    appendLine("    ),")
                } else {
                    appendLine("    )")
                }
            }
            appendLine(")")
            appendLine()
        }

        appendLine("/** Generated documentation for the '$libraryName' library. */")
        appendLine("val generated${capitalizedName}Docs: Map<String, KlangSymbol> = buildMap {")
        if (entries.objects.isNotEmpty()) {
            appendLine("    putAll(generated${capitalizedName}DocsObjects())")
        }
        chunks.forEachIndexed { chunkIdx, _ ->
            appendLine("    putAll(generated${capitalizedName}DocsChunk$chunkIdx())")
        }
        appendLine("}")
    }

    private fun generateDocEntry(
        name: String,
        libraryName: String,
        items: List<DocItem>,
    ): String {
        val parsedKDocs = items.map { KDocParser.parse(it.fn.docString) }

        val category = parsedKDocs.mapNotNull { it.category }.firstOrNull() ?: "uncategorized"
        val allTags = parsedKDocs.flatMap { it.tags }.distinct()
        val allAliases = parsedKDocs.flatMap { it.aliases }.distinct()

        val tagsString = allTags.joinToString(", ") { "\"$it\"" }
        val aliasesString = allAliases.joinToString(", ") { "\"$it\"" }

        val variants = items.zip(parsedKDocs).map { (item, kdoc) ->
            generateCallableDoc(item, kdoc, libraryName)
        }

        return buildString {
            appendLine()
            appendLine("    \"$name\" to KlangSymbol(")
            appendLine("        name = \"$name\",")
            appendLine("        category = \"$category\",")
            appendLine("        tags = listOf($tagsString),")
            appendLine("        aliases = listOf($aliasesString),")
            appendLine("        library = \"$libraryName\",")
            appendLine("        variants = listOf(")
            variants.forEachIndexed { index, variant ->
                append(variant)
                if (index < variants.size - 1) appendLine(",")
            }
            appendLine()
            appendLine("        )")
            append("    )")
        }
    }

    private fun generateCallableDoc(item: DocItem, kdoc: ParsedKDoc, libraryName: String = ""): String {
        val fn = item.fn
        val allParams = getScriptParams(fn)
        // Raw-args methods have internal params (List<RuntimeValue>, SourceLocation?) — exclude all from docs
        // For type extensions with explicit self param (no Kotlin extension receiver),
        // the first param is the receiver — exclude from docs.
        // For extension functions (fn.extensionReceiver != null), the receiver is `this`
        // and ALL params are script-visible — don't strip.
        val hasExtensionReceiver = fn.extensionReceiver != null
        val params = when {
            item.isRawArgs -> emptyList()
            item.isTypeExtension && !hasExtensionReceiver && allParams.isNotEmpty() -> allParams.drop(1)
            else -> allParams
        }
        val returnType = fn.returnType?.resolve()
        val description = kdoc.description.escapeForRawString()
        val returnDoc = kdoc.returnDoc.replace("\n", " ").escapeForRawString()

        return buildString {
            appendLine("            KlangCallable(")
            appendLine("                name = \"${item.scriptName}\",")
            if (item.receiver != null) {
                appendLine("                receiver = KlangType(simpleName = \"${item.receiver}\"),")
            }
            if (params.isNotEmpty()) {
                appendLine("                params = listOf(")
                for (param in params) {
                    val paramName = param.name?.asString() ?: continue
                    val paramType = param.type.resolve()
                    val paramDesc = (kdoc.params[paramName] ?: "").replace("\n", " ").escapeForRawString()
                    val paramUiTools = kdoc.paramTools[paramName] ?: emptyList()
                    val paramSubFields = kdoc.paramSubs[paramName]

                    append("                    KlangParam(")
                    append("name = \"$paramName\", ")
                    append("type = ${generateKlangType(paramType)}")
                    if (param.isVararg) append(", isVararg = true")
                    if (param.hasDefault) append(", isOptional = true")
                    val defaultDoc = DefaultValueExtractor.extract(param)
                    if (defaultDoc != null) {
                        append(", defaultDoc = \"\"\"${defaultDoc.escapeForRawString()}\"\"\"")
                    }
                    if (paramDesc.isNotEmpty()) append(", description = \"\"\"$paramDesc\"\"\"")
                    if (paramUiTools.isNotEmpty()) {
                        append(", uitools = listOf(${paramUiTools.joinToString(", ") { "\"$it\"" }})")
                    }
                    if (paramSubFields != null && paramSubFields.isNotEmpty()) {
                        append(", subFields = mapOf(${paramSubFields.entries.joinToString(", ") { "\"${it.key}\" to \"\"\"${it.value}\"\"\"" }})")
                    }
                    appendLine("),")
                }
                appendLine("                ),")
            } else {
                appendLine("                params = emptyList(),")
            }
            if (returnType != null) {
                appendLine("                returnType = ${generateKlangType(returnType)},")
            }
            appendLine("                description = \"\"\"$description\"\"\",")
            appendLine("                returnDoc = \"\"\"$returnDoc\"\"\",")
            if (kdoc.samples.isNotEmpty()) {
                appendLine("                samples = listOf(")
                kdoc.samples.forEachIndexed { idx, sample ->
                    val comma = if (idx < kdoc.samples.lastIndex) "," else ""
                    appendLine("                    KlangCodeSample(code = \"\"\"${sample.code.escapeForRawString()}\"\"\", type = KlangCodeSampleType.${sample.type.name})$comma")
                }
                appendLine("                ),")
            } else {
                appendLine("                samples = emptyList(),")
            }
            appendLine("                library = \"$libraryName\"")
            append("            )")
        }
    }

    // ===== Receiver type auto-mapping =====

    /**
     * Maps Kotlin platform types to their KlangScript runtime value types.
     * Allows writing `fun String.gain(...)` instead of `@Function(receiver = StringValue::class)`.
     */
    private val RECEIVER_TYPE_MAP = mapOf(
        "kotlin.String" to "io.peekandpoke.klang.script.runtime.StringValue",
        "kotlin.Double" to "io.peekandpoke.klang.script.runtime.NumberValue",
        "kotlin.Int" to "io.peekandpoke.klang.script.runtime.NumberValue",
        "kotlin.Float" to "io.peekandpoke.klang.script.runtime.NumberValue",
        "kotlin.Boolean" to "io.peekandpoke.klang.script.runtime.BooleanValue",
    )

    private fun mapReceiverType(resolver: Resolver, cls: KSClassDeclaration): KSClassDeclaration? {
        val fqn = cls.qualifiedName?.asString() ?: return null
        val mappedFqn = RECEIVER_TYPE_MAP[fqn] ?: return null
        return resolver.getClassDeclarationByName(resolver.getKSNameFromString(mappedFqn))
    }

    // ===== Type helpers =====

    /** Get function parameters excluding CallInfo (which is auto-injected). Warns on Long usage. */
    private fun getScriptParams(fn: KSFunctionDeclaration): List<KSValueParameter> {
        val params = fn.parameters
        if (params.isEmpty()) return params

        // Warn on Long parameters — Long boxes in Kotlin/JS, use Int or Double instead
        for (param in params) {
            val typeName = param.type.resolve().declaration.simpleName.asString()
            if (typeName == "Long") {
                logger.warn(
                    "Parameter '${param.name?.asString()}' in '${fn.simpleName.asString()}' uses Long which boxes in Kotlin/JS. Use Int or Double instead.",
                    fn
                )
            }
        }

        // CallInfo can appear anywhere in the parameter list. It is always passed by
        // the generated registration code via named-arg syntax, so its position is
        // flexible. This is useful for putting a trailing lambda last while keeping
        // callInfo optional in the middle.
        return params.filter { it.type.resolve().declaration.simpleName.asString() != CALL_INFO_TYPE }
    }

    private fun hasCallInfoParam(fn: KSFunctionDeclaration): Boolean {
        val params = fn.parameters
        if (params.isEmpty()) return false
        return params.any { it.type.resolve().declaration.simpleName.asString() == CALL_INFO_TYPE }
    }

    private fun getVarargComponentType(param: KSValueParameter): String {
        val type = param.type.resolve()
        return resolveCastType(type)
    }

    /**
     * Build the `cls = ...` literal for convertArgToKotlin when a vararg param
     * carries a function type (e.g. PatternMapperFn = (SprudelPattern) ->
     * SprudelPattern). `::class` isn't valid on a function type, so we fall back
     * to `FunctionN::class` and the downstream list cast gives the real structure.
     */
    private fun varargClsLiteral(type: KSType): String {
        val effective = type.resolveAlias()
        val simpleName = effective.declaration.simpleName.asString()
        val isFunctionType = simpleName.startsWith("Function") &&
                simpleName.removePrefix("Function").toIntOrNull() != null
        return if (isFunctionType) "$simpleName::class" else "$simpleName::class"
    }

    /**
     * Resolve a type to its simple Kotlin name.
     *
     * @param followTypeAlias When true (default), follows typealiases to
     *   the underlying type. When false, keeps the alias name — useful for
     *   cast sites where `PatternMapperFn` needs to stay as the alias
     *   rather than being expanded to `Function1<SprudelPattern, SprudelPattern>`.
     */
    private fun resolveKotlinType(type: KSType?, followTypeAlias: Boolean = true): String {
        if (type == null) return "Any"
        val effective = if (followTypeAlias && type.declaration is KSTypeAlias) {
            (type.declaration as KSTypeAlias).type.resolve()
        } else {
            type
        }
        val name = effective.declaration.simpleName.asString()
        val nullable = if (effective.nullability == Nullability.NULLABLE) "?" else ""
        return "$name$nullable"
    }

    /**
     * Resolves the cast/declaration type for a parameter.
     *
     * Most types use the alias-followed underlying type. Function types
     * (FunctionN<P1, ..., R>) — including aliases like
     * `PatternMapperFn = (SprudelPattern) -> SprudelPattern` — get expanded
     * to their structural form `(P1, ..., PN) -> R`. This matters because:
     *  - `as Function1` is a raw-type cast that Kotlin rejects.
     *  - `as PatternMapperFn` would require importing the alias.
     *  - `as (SprudelPattern) -> SprudelPattern` is unambiguous and only
     *    requires the component types to be in scope (they typically already are).
     */
    /** Walk through chained typealiases to the underlying type. */
    private fun KSType.resolveAlias(): KSType {
        var t = this
        while (t.declaration is KSTypeAlias) {
            t = (t.declaration as KSTypeAlias).type.resolve()
        }
        return t
    }

    private fun resolveCastType(type: KSType?): String {
        if (type == null) return "Any"

        val effective = type.resolveAlias()
        val effectiveName = effective.declaration.simpleName.asString()
        val isFunctionType = effectiveName.startsWith("Function") &&
                effectiveName.removePrefix("Function").toIntOrNull() != null

        if (isFunctionType && effective.arguments.isNotEmpty()) {
            // FunctionN<P1, ..., PN, R> — last arg is return, rest are params.
            val typeArgs = effective.arguments
            val paramTypes = typeArgs.dropLast(1).map { arg ->
                arg.type?.resolve()?.let { resolveKotlinType(it) } ?: "Any"
            }
            val returnType = typeArgs.last().type?.resolve()?.let { resolveKotlinType(it) } ?: "Any"
            val funcType = "(${paramTypes.joinToString(", ")}) -> $returnType"
            // Always parenthesize so the type is unambiguous in lambda parameter
            // declarations like `{ transform: ((P) -> R) -> body }`.
            val isNullable = type.nullability == Nullability.NULLABLE
            return if (isNullable) "(($funcType)?)" else "($funcType)"
        }

        return resolveKotlinType(type)
    }

    private fun generateKlangType(type: KSType): String {
        val declaration = type.declaration
        val simpleName = declaration.simpleName.asString()
        val displayName = typeDisplayName(simpleName)
        val isTypeAlias = declaration is KSTypeAlias
        val isNullable = type.nullability == Nullability.NULLABLE

        return buildString {
            append("KlangType(simpleName = \"$displayName\"")
            if (isTypeAlias) append(", isTypeAlias = true")
            if (isNullable) append(", isNullable = true")
            append(")")
        }
    }

    private fun typeDisplayName(kotlinName: String): String {
        return TYPE_DISPLAY_NAMES[kotlinName] ?: kotlinName
    }

    /**
     * Wraps any segment of a qualified name that is a Kotlin hard keyword in backticks.
     * E.g. `io.peekandpoke.klang.sprudel.lang.when` → `io.peekandpoke.klang.sprudel.lang.\`when\``.
     */
    private fun escapeQualifiedNameKeywords(qualifiedName: String): String {
        return qualifiedName.split('.').joinToString(".") { segment ->
            if (segment in KOTLIN_HARD_KEYWORDS) "`$segment`" else segment
        }
    }

    /**
     * Wraps a Kotlin identifier in backticks if it is a hard keyword.
     */
    private fun escapeIdentifier(name: String): String {
        return if (name in KOTLIN_HARD_KEYWORDS) "`$name`" else name
    }

    // ===== ParamSpec emission =====

    /**
     * Build the Kotlin source for a `List<ParamSpec>` covering [scriptParams].
     *
     * For optional params, attempts to extract the Kotlin default expression via
     * [DefaultValueExtractor] and pastes it into a thunk if the text looks safe
     * to embed (no `this`/`super` references). Unsafe defaults leave `default = null`;
     * the runtime then rejects named-call omissions for those slots.
     *
     * Note on type aliases: [resolveKotlinType] follows aliases through to the
     * underlying type, so a parameter declared as e.g. `IgnitorDslLike` (a typealias
     * for `Any`) ends up with `kotlinType = Any::class` in the emitted spec. This is
     * enough to drive named-arg binding (we only need the name), but it loses the
     * original alias for runtime type-checking and intellisense — those rely on the
     * separate KlangParam doc model which preserves the alias text. Functions that
     * want strict type checking should declare concrete (non-alias) parameter types.
     */
    private fun paramSpecsListExpression(scriptParams: List<KSValueParameter>, indent: String = "    "): String {
        if (scriptParams.isEmpty()) return "emptyList()"
        val specs = scriptParams.map { p ->
            val name = p.name?.asString() ?: "p"
            val resolvedType = p.type.resolve()
            // Strip the nullable suffix so kotlinType is a bare KClass; track nullability separately.
            val typeNoNull = resolveKotlinType(resolvedType).removeSuffix("?")
            val isNullable = resolvedType.nullability == Nullability.NULLABLE
            val parts = mutableListOf<String>()
            parts.add("name = \"$name\"")
            parts.add("kotlinType = $typeNoNull::class")
            if (p.isVararg) parts.add("isVararg = true")
            if (isNullable) parts.add("isNullable = true")
            if (p.hasDefault) {
                parts.add("isOptional = true")
                val thunk = safeDefaultThunk(p)
                if (thunk != null) parts.add("default = $thunk")
            }
            "${indent}    ParamSpec(${parts.joinToString(", ")})"
        }
        return buildString {
            appendLine("listOf(")
            appendLine(specs.joinToString(",\n"))
            append("${indent})")
        }
    }

    /**
     * Returns a Kotlin source expression for a thunk that produces the
     * extracted default value, or null if extraction failed or the text isn't
     * provably safe to paste verbatim into the generated file.
     *
     * "Provably safe" = a Kotlin literal that requires no enclosing-scope
     * symbols to compile:
     *   - number literals (Int/Long/Float/Double, with optional sign and suffix)
     *   - string literals ("…" or """…""")
     *   - char literals ('…')
     *   - boolean literals (true/false)
     *   - null literal
     *
     * Anything else (qualified references, function calls, expressions) goes
     * to `defaultDoc` for display only; the runtime falls back to Kotlin's
     * own arity-dispatch when the user calls the function positionally, and
     * to a "use positional" error when the caller omits the slot in a named
     * call. This is the conservative choice: a paste failure here would
     * break the build of [GeneratedStdlibRegistration]; a missing thunk
     * just degrades to slightly-less-flexible named-arg ergonomics.
     */
    private fun safeDefaultThunk(param: KSValueParameter): String? {
        val text = DefaultValueExtractor.extract(param) ?: return null
        if (!SafeDefaultLiteral.isSafe(text)) return null
        return "{ wrapAsRuntimeValue($text) }"
    }

    /** Escapes content for safe embedding in Kotlin raw strings ("""..."""). */
    private fun String.escapeForRawString(): String {
        // In raw strings, backslash escapes don't work. Use interpolation instead.
        // 1. Escape $ to prevent unintended string interpolation
        // 2. Escape """ to prevent premature raw string termination
        return this
            .replace("\$", "\${'$'}")
            .replace("\"\"\"", "\${'\"'}\${'\"'}\${'\"'}")
    }
}
