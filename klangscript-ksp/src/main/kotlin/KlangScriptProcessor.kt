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

        /** Maximum number of fixed parameters supported by registerMethod/registerFunction overloads. */
        private const val MAX_FIXED_PARAMS = 5
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
        appendLine("@file:Suppress(\"UNCHECKED_CAST\", \"UNUSED_PARAMETER\")")
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
            }
        }
        entries.functions.forEach { fn ->
            fn.fn.qualifiedName?.asString()?.let { imports.add(it) }
        }
        imports.sorted().forEach { appendLine("import $it") }
        if (imports.isNotEmpty()) appendLine()

        // === Registration function ===
        appendLine("/**")
        appendLine(" * Registers all @KlangScript annotated symbols for the '$libraryName' library.")
        appendLine(" */")
        appendLine("fun KlangScriptExtensionBuilder.register${capitalizedName}Generated() {")

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

        // Objects
        for (obj in entries.objects) {
            val normalMethods = obj.methods.filter { !isRawArgsMethod(it.fn) }
            val rawArgsMethods = obj.methods.filter { isRawArgsMethod(it.fn) }

            appendLine()
            appendLine("    // @Object(\"${obj.name}\") on ${obj.cls.simpleName.asString()}")
            appendLine("    registerObject(\"${obj.name}\", ${obj.cls.simpleName.asString()}) {")
            for (method in normalMethods) {
                val item = buildMethodItem(method, obj.cls, isTypeExtension = false)
                appendLine("        ${item.renderRegistration()}")
            }
            appendLine("    }")

            for (method in rawArgsMethods) {
                val item = buildRawArgsItem(method, obj.cls)
                appendLine("    ${item.renderRegistration()}")
            }
        }

        // Type extensions
        for (ext in entries.typeExtensions) {
            val typeName = ext.typeDecl.simpleName.asString()
            val allFileLevelMethods = ext.methods.all { it.fn.parentDeclaration !is KSClassDeclaration }

            appendLine()
            appendLine("    // @TypeExtensions($typeName::class) on ${ext.cls.simpleName.asString()}")

            if (allFileLevelMethods) {
                for (method in ext.methods) {
                    val item = buildFileLevelExtItem(method, ext.typeDecl)
                    appendLine("    ${item.renderRegistration()}")
                }
            } else {
                appendLine("    registerType<$typeName> {")
                for (method in ext.methods) {
                    val item = buildMethodItem(method, ext.cls, isTypeExtension = true)
                    appendLine("        ${item.renderRegistration()}")
                }
                appendLine("    }")
            }
        }

        // Top-level functions
        for (fn in entries.functions) {
            appendLine()
            appendLine("    // @Function on ${fn.fn.simpleName.asString()}")
            val item = buildTopLevelFunctionItem(fn)
            appendLine("    ${item.renderRegistration()}")
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
            val returnType = resolveKotlinType(fn.returnType?.resolve())
            val fnCall = "$fnQualifier${fn.simpleName.asString()}(${selfArg}*args.toTypedArray()${if (hasCallInfo) ", callInfo" else ""})"
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

        // With defaults → arity dispatch
        if (hasDefaults) {
            val receiverCast = if (isTypeExtension) {
                val receiverTypeName = fn.parameters.firstOrNull()?.type?.resolve()?.let { resolveKotlinType(it, followTypeAlias = false) }
                receiverTypeName?.let { ArityDispatchItem.ReceiverCast(it, useConvertToKotlin = false) }
            } else null

            return ArityDispatchItem(
                scriptName = method.name,
                specsExpr = specsExpr,
                fnCall = "$fnQualifier${fn.simpleName.asString()}",
                selfArg = if (isTypeExtension) "typedReceiver, " else "",
                scriptParams = scriptParams.mapIndexed { i, p ->
                    ArityDispatchItem.ResolvedParam(
                        name = p.name?.asString() ?: "p$i",
                        kotlinType = resolveKotlinType(p.type.resolve()),
                        hasDefault = p.hasDefault,
                        index = i,
                    )
                },
                receiverCast = receiverCast,
                receiverClassName = null,
                isTopLevel = false,
            )
        }

        // Fixed arity — simple one-liner
        if (scriptParams.size > MAX_FIXED_PARAMS) {
            logger.error("@Method '${method.name}' has ${scriptParams.size} parameters (max $MAX_FIXED_PARAMS)", fn)
        }

        val params = scriptParams.map { p ->
            (p.name?.asString() ?: "p") to resolveKotlinType(p.type.resolve())
        }
        val callArgsStr = scriptParams.map { it.name?.asString() ?: "p" }.joinToString(", ")
        val fullCallArgs = if (isTypeExtension) {
            if (callArgsStr.isEmpty()) "this" else "this, $callArgsStr"
        } else callArgsStr

        return FixedMethodItem(
            scriptName = method.name,
            specsExpr = specsExpr,
            fnCall = "$fnQualifier${fn.simpleName.asString()}",
            params = params,
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
            fnName = fn.simpleName.asString(),
            hasLocation = hasLocation,
        )
    }

    private fun buildFileLevelExtItem(method: MethodEntry, typeDecl: KSClassDeclaration): FileLevelExtItem {
        val fn = method.fn
        val allParams = getScriptParams(fn)
        val typeName = typeDecl.simpleName.asString()
        val fnName = fn.simpleName.asString()
        val hasExtensionReceiver = fn.extensionReceiver != null

        val scriptParams = if (hasExtensionReceiver) allParams else {
            if (allParams.isNotEmpty()) allParams.drop(1) else allParams
        }

        val specsExpr = paramSpecsListExpression(scriptParams)
        val hasDefaults = scriptParams.any { it.hasDefault }

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
                    hasDefault = p.hasDefault,
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
        val fnName = fn.simpleName.asString()
        val specsExpr = paramSpecsListExpression(params)

        // Vararg → legacy
        if (isVararg) {
            val paramType = getVarargComponentType(params.first { it.isVararg })
            val returnType = resolveKotlinType(fn.returnType?.resolve())
            val fnCall = "$fnName(*args.toTypedArray()${if (hasCallInfo) ", callInfo" else ""})"
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

        // With defaults → arity dispatch
        if (hasDefaults) {
            return ArityDispatchItem(
                scriptName = entry.name,
                specsExpr = specsExpr,
                fnCall = fnName,
                selfArg = "",
                scriptParams = params.mapIndexed { i, p ->
                    ArityDispatchItem.ResolvedParam(
                        name = p.name?.asString() ?: "p$i",
                        kotlinType = resolveKotlinType(p.type.resolve()),
                        hasDefault = p.hasDefault,
                        index = i,
                    )
                },
                receiverCast = null,
                receiverClassName = null,
                isTopLevel = true,
            )
        }

        // Fixed arity
        if (params.size > MAX_FIXED_PARAMS) {
            logger.error("@Function '${entry.name}' has ${params.size} parameters (max $MAX_FIXED_PARAMS)", fn)
        }

        val paramPairs = params.map { p ->
            (p.name?.asString() ?: "p") to resolveKotlinType(p.type.resolve())
        }
        val callArgs = params.map { it.name?.asString() ?: "p" }.joinToString(", ")

        return FixedMethodItem(
            scriptName = entry.name,
            specsExpr = specsExpr,
            fnCall = fnName,
            params = paramPairs,
            callArgs = callArgs,
            isTopLevel = true,
        )
    }

    // ===== DEAD CODE — old emission methods, replaced by build*Item() + RegistrationItem.renderRegistration() =====
    // TODO: Delete everything from here to "===== Docs generation =====" once verified.

    @Suppress("unused")
    private fun DEAD_generateMethodRegistration(
        method: MethodEntry,
        ownerCls: KSClassDeclaration,
        isTypeExtension: Boolean = false,
    ): String {
        val fn = method.fn
        val allParams = getScriptParams(fn)
        val hasCallInfo = hasCallInfoParam(fn)

        // File-level functions routed through @Function(receiver = ...) don't
        // have a parent class — the function is called by its imported name,
        // not qualified as OwnerClass.fn.
        val isFileLevelFunction = fn.parentDeclaration !is KSClassDeclaration
        val ownerName = if (isFileLevelFunction) "" else ownerCls.simpleName.asString()
        val fnQualifier = if (ownerName.isEmpty()) "" else "$ownerName."

        // For type extensions, first param is the receiver — strip it from script-visible params
        val scriptParams = if (isTypeExtension && allParams.isNotEmpty()) allParams.drop(1) else allParams
        val selfArg = if (isTypeExtension) {
            if (isFileLevelFunction) "typedReceiver, " else "this, "
        } else ""

        val isVararg = scriptParams.any { it.isVararg }
        val specsExpr = paramSpecsListExpression(scriptParams)

        return when {
            isVararg && hasCallInfo -> {
                // Vararg + CallInfo helpers don't yet thread paramSpecs through. Named-arg
                // calls against these will get the transitional "named args not supported"
                // error. Phase 6 wires this up.
                val paramType = getVarargComponentType(scriptParams.first { it.isVararg })
                val returnType = resolveKotlinType(fn.returnType?.resolve())
                "registerVarargMethodWithCallInfo<$paramType, $returnType>(\"${method.name}\") { args, callInfo -> $fnQualifier${fn.simpleName.asString()}(${selfArg}*args.toTypedArray(), callInfo) }"
            }

            isVararg -> {
                val paramType = getVarargComponentType(scriptParams.first { it.isVararg })
                val returnType = resolveKotlinType(fn.returnType?.resolve())
                "registerVarargMethod<$paramType, $returnType>(\"${method.name}\") { args -> $fnQualifier${fn.simpleName.asString()}(${selfArg}*args.toTypedArray()) }"
            }

            else -> {
                val hasDefaults = scriptParams.any { it.hasDefault }

                if (hasDefaults) {
                    // Generate raw registerExtensionMethod with arity dispatch
                    // so Kotlin default parameters work when fewer args are provided
                    generateMethodWithDefaults(method, ownerCls, isTypeExtension, scriptParams, specsExpr)
                } else {
                    if (scriptParams.size > MAX_FIXED_PARAMS) {
                        logger.error(
                            "@Method '${method.name}' has ${scriptParams.size} parameters (max $MAX_FIXED_PARAMS for fixed-arity registration)",
                            fn
                        )
                    }
                    val paramNames = scriptParams.map { p ->
                        val name = p.name?.asString() ?: "p"
                        "$name: ${resolveKotlinType(p.type.resolve())}"
                    }
                    val callArgs = scriptParams.map { it.name?.asString() ?: "p" }
                    val callArgsStr = if (callArgs.isEmpty()) "" else callArgs.joinToString(", ")
                    val fullCallArgs = if (isTypeExtension) {
                        if (callArgsStr.isEmpty()) "this" else "this, $callArgsStr"
                    } else {
                        callArgsStr
                    }

                    when (scriptParams.size) {
                        0 -> "registerMethod(\"${method.name}\", $specsExpr) { $fnQualifier${fn.simpleName.asString()}($fullCallArgs) }"
                        else -> {
                            val paramDecl = paramNames.joinToString(", ")
                            "registerMethod(\"${method.name}\", $specsExpr) { $paramDecl -> $fnQualifier${fn.simpleName.asString()}($fullCallArgs) }"
                        }
                    }
                }
            }
        }
    }

    /**
     * Generates the arity-dispatching body for a function/method with default parameters.
     * Shared between method and top-level function registration.
     *
     * @param name script-visible name (for error messages)
     * @param fnCall how to call the Kotlin function, e.g. "OwnerName.fnName"
     * @param selfArg prefix for the call args, e.g. "receiver, " for type extensions, "" for functions
     * @param scriptParams the script-visible parameters (receiver already stripped for type extensions)
     * @param indent base indentation for the generated code
     */
    private fun StringBuilder.generateArityDispatchBody(
        name: String,
        fnCall: String,
        selfArg: String,
        scriptParams: List<KSValueParameter>,
        indent: String,
    ) {
        val requiredCount = scriptParams.count { !it.hasDefault }
        val firstOptionalIdx = scriptParams.indexOfFirst { it.hasDefault }

        appendLine("${indent}checkArgsSize(fn = \"$name\", args = args, expected = $requiredCount, location = loc)")

        // Required params — always convert
        scriptParams.forEachIndexed { i, param ->
            if (!param.hasDefault) {
                val paramName = param.name?.asString() ?: "p$i"
                val paramType = resolveKotlinType(param.type.resolve())
                appendLine("${indent}val $paramName = convertArgToKotlin(fn = \"$name\", args = args, index = $i, cls = $paramType::class, nullable = false, loc = loc) as $paramType")
            }
        }

        appendLine("${indent}wrapAsRuntimeValue(")

        // Generate if/else chain for each arity level
        for (level in scriptParams.size downTo firstOptionalIdx + 1) {
            val argsForLevel = scriptParams.take(level)
            val prefix = if (level == scriptParams.size) "if" else "} else if"
            appendLine("$indent    $prefix (args.size >= $level) {")

            argsForLevel.forEachIndexed { i, param ->
                if (param.hasDefault) {
                    val paramName = param.name?.asString() ?: "p$i"
                    val paramType = resolveKotlinType(param.type.resolve())
                    appendLine("$indent        val $paramName = convertArgToKotlin(fn = \"$name\", args = args, index = $i, cls = $paramType::class, nullable = false, loc = loc) as $paramType")
                }
            }

            val callArgs = argsForLevel.map { it.name?.asString() ?: "p" }.joinToString(", ")
            appendLine("$indent        $fnCall(${joinCallArgs(selfArg, callArgs)})")
        }

        // Final else: only required params
        val requiredArgs = scriptParams.filter { !it.hasDefault }.map { it.name?.asString() ?: "p" }.joinToString(", ")
        appendLine("$indent    } else {")
        appendLine("$indent        $fnCall(${joinCallArgs(selfArg, requiredArgs)})")
        appendLine("$indent    }")
        appendLine("$indent)")
    }

    /**
     * Join the receiver/self prefix and the comma-separated script args, handling the
     * empty-args case so we never emit a trailing comma like `Foo.method(self, )`.
     */
    private fun joinCallArgs(selfArg: String, args: String): String = when {
        selfArg.isEmpty() -> args
        args.isEmpty() -> selfArg.trimEnd(' ', ',')
        else -> "$selfArg$args"
    }

    private fun generateMethodWithDefaults(
        method: MethodEntry,
        ownerCls: KSClassDeclaration,
        isTypeExtension: Boolean,
        scriptParams: List<KSValueParameter>,
        specsExpr: String,
    ): String {
        val fnName = method.fn.simpleName.asString()
        val isFileLevelFn = method.fn.parentDeclaration !is KSClassDeclaration
        val fnQual = if (isFileLevelFn) "" else "${ownerCls.simpleName.asString()}."

        // For type extensions, the Kotlin function takes a typed receiver as its first
        // parameter. The bridge's `receiver: Any` needs to be cast to that type before
        // being passed through the arity dispatch.
        val receiverTypeName = if (isTypeExtension) {
            method.fn.parameters.firstOrNull()?.type?.resolve()?.let { resolveKotlinType(it, followTypeAlias = false) }
        } else null

        val selfArg = if (isTypeExtension) "typedReceiver, " else ""

        return buildString {
            appendLine("builder.registerExtensionMethodWithSpecs(cls, \"${method.name}\", $specsExpr) { receiver, args, loc ->")
            if (receiverTypeName != null) {
                appendLine("                @Suppress(\"UNCHECKED_CAST\")")
                appendLine("                val typedReceiver = receiver as $receiverTypeName")
            }
            generateArityDispatchBody(
                name = method.name,
                fnCall = "$fnQual$fnName",
                selfArg = selfArg,
                scriptParams = scriptParams,
                indent = "                ",
            )
            append("            }")
        }
    }

    /**
     * Detects if a method uses the raw-args pattern: first param is List<RuntimeValue>.
     * Optional second param is SourceLocation?.
     */
    private fun isRawArgsMethod(fn: KSFunctionDeclaration): Boolean {
        val params = fn.parameters
        if (params.isEmpty()) return false
        val firstParam = params.first()
        val firstType = firstParam.type.resolve()
        val firstTypeName = firstType.declaration.simpleName.asString()
        if (firstTypeName != "List") return false
        // Check the type argument is RuntimeValue
        val typeArgs = firstType.arguments
        if (typeArgs.isEmpty()) return false
        val elementType = typeArgs.first().type?.resolve()?.declaration?.simpleName?.asString()
        return elementType == "RuntimeValue"
    }

    /**
     * Generates a registerExtensionMethod call string for raw-args methods.
     * Called at the builder level, outside registerObject/registerType blocks.
     *
     * Raw-args methods receive a `List<RuntimeValue>` directly — no script-
     * visible parameter names are declared, so paramSpecs stays empty and
     * named-arg calls are rejected (transitional). Callers must use
     * positional syntax.
     */
    private fun generateRawArgsExtensionMethod(method: MethodEntry, ownerCls: KSClassDeclaration): String {
        val fn = method.fn
        val ownerName = ownerCls.simpleName.asString()
        val params = fn.parameters
        val hasLocation = params.size >= 2 &&
                params[1].type.resolve().declaration.simpleName.asString() == "SourceLocation"
        val callArgs = if (hasLocation) "args, loc" else "args, null"

        return "registerExtensionMethod($ownerName::class, \"${method.name}\") { _, args, loc -> $ownerName.${fn.simpleName.asString()}($callArgs) }"
    }

    /**
     * Generates a top-level `registerExtensionMethodWithSpecs(...)` call for a
     * file-level @Function(receiver = ...) method. Avoids the `registerType<T>`
     * / `registerMethod` wrapper that accesses @PublishedApi internal fields.
     */
    private fun generateFileLevelExtensionMethod(method: MethodEntry, typeDecl: KSClassDeclaration): String {
        val fn = method.fn
        val allParams = getScriptParams(fn)
        val typeName = typeDecl.simpleName.asString()
        val fnName = fn.simpleName.asString()

        // Pattern A: Kotlin extension function (fun Foo.bar(amount)) — receiver
        // is `this`, not an explicit first param. Script-visible params = ALL params.
        // Bridge calls as extension: typedReceiver.fnName(args).
        //
        // Pattern B: Explicit self param (fun _klangBar(self: Foo, amount)) —
        // first param IS the receiver. Script-visible params = params minus first.
        // Bridge calls as standalone: fnName(typedReceiver, args).
        val hasExtensionReceiver = fn.extensionReceiver != null

        val scriptParams = if (hasExtensionReceiver) {
            allParams  // Pattern A: all params are script-visible
        } else {
            if (allParams.isNotEmpty()) allParams.drop(1) else allParams  // Pattern B: strip self
        }

        val specsExpr = paramSpecsListExpression(scriptParams)
        val hasDefaults = scriptParams.any { it.hasDefault }

        // Receiver type for the cast. For Pattern A, we use convertToKotlin()
        // so that StringValue → String, NumberValue → Double etc. are handled
        // correctly (the Kotlin extension receiver type may differ from the
        // KlangScript runtime value type). For Pattern B, the first param IS
        // the runtime type, so a direct cast works.
        val receiverTypeName = if (hasExtensionReceiver) {
            fn.extensionReceiver?.resolve()?.let { resolveKotlinType(it, followTypeAlias = false) }
        } else {
            fn.parameters.firstOrNull()?.type?.resolve()?.let { resolveKotlinType(it, followTypeAlias = false) }
        }

        return buildString {
            appendLine("registerExtensionMethodWithSpecs($typeName::class, \"${method.name}\", $specsExpr) { receiver, args, loc ->")

            if (receiverTypeName != null) {
                if (hasExtensionReceiver) {
                    // Pattern A: use convertToKotlin for type-safe unwrapping
                    // (handles StringValue→String, NativeObjectValue→T, etc.)
                    appendLine("        @Suppress(\"UNCHECKED_CAST\")")
                    appendLine("        val typedReceiver = wrapAsRuntimeValue(receiver).convertToKotlin($receiverTypeName::class, loc)")
                } else {
                    // Pattern B: direct cast (self param IS the runtime type)
                    appendLine("        @Suppress(\"UNCHECKED_CAST\")")
                    appendLine("        val typedReceiver = receiver as $receiverTypeName")
                }
            }

            if (hasDefaults) {
                // For Pattern A, the selfArg is "typedReceiver." (extension call prefix);
                // for Pattern B, it's "typedReceiver, " (standalone call arg).
                val selfArg = if (receiverTypeName != null) {
                    if (hasExtensionReceiver) "" else "typedReceiver, "
                } else ""
                val fnCallPrefix = if (hasExtensionReceiver && receiverTypeName != null) "typedReceiver." else ""

                generateArityDispatchBody(
                    name = method.name,
                    fnCall = "$fnCallPrefix$fnName",
                    selfArg = selfArg,
                    scriptParams = scriptParams,
                    indent = "        ",
                )
            } else {
                appendLine("        checkArgsSize(fn = \"${method.name}\", args = args, expected = ${scriptParams.size}, location = loc)")
                scriptParams.forEachIndexed { i, param ->
                    val paramName = param.name?.asString() ?: "p$i"
                    val paramType = resolveKotlinType(param.type.resolve())
                    appendLine("        val $paramName = convertArgToKotlin(fn = \"${method.name}\", args = args, index = $i, cls = $paramType::class, nullable = false, loc = loc) as $paramType")
                }
                val callArgs = scriptParams.map { it.name?.asString() ?: "p" }.joinToString(", ")

                if (hasExtensionReceiver && receiverTypeName != null) {
                    appendLine("        wrapAsRuntimeValue(typedReceiver.$fnName($callArgs))")
                } else {
                    val selfArg = if (receiverTypeName != null) "typedReceiver, " else ""
                    appendLine("        wrapAsRuntimeValue($fnName(${joinCallArgs(selfArg, callArgs)}))")
                }
            }

            append("    }")
        }
    }

    private fun generateFunctionRegistration(entry: FunctionEntry): String {
        val fn = entry.fn
        val params = getScriptParams(fn)
        val hasCallInfo = hasCallInfoParam(fn)
        val isVararg = params.any { it.isVararg }
        val hasDefaults = params.any { it.hasDefault }
        val fnName = fn.simpleName.asString()
        val specsExpr = paramSpecsListExpression(params)

        return when {
            isVararg && hasCallInfo -> {
                val paramType = getVarargComponentType(params.first { it.isVararg })
                val returnType = resolveKotlinType(fn.returnType?.resolve())
                "registerVarargFunctionWithCallInfo<$paramType, $returnType>(\"${entry.name}\") { args, callInfo -> $fnName(*args.toTypedArray(), callInfo) }"
            }

            isVararg -> {
                val paramType = getVarargComponentType(params.first { it.isVararg })
                val returnType = resolveKotlinType(fn.returnType?.resolve())
                "registerVarargFunction<$paramType, $returnType>(\"${entry.name}\") { args -> $fnName(*args.toTypedArray()) }"
            }

            hasDefaults -> {
                // Use arity-dispatching spec-aware registration so Kotlin defaults
                // work for positional calls and safe-thunk defaults work for named.
                buildString {
                    appendLine("registerFunctionWithSpecs(\"${entry.name}\", $specsExpr) { args, loc ->")
                    generateArityDispatchBody(
                        name = entry.name,
                        fnCall = fnName,
                        selfArg = "",
                        scriptParams = params,
                        indent = "        ",
                    )
                    append("    }")
                }
            }

            else -> {
                if (params.size > MAX_FIXED_PARAMS) {
                    logger.error(
                        "@Function '${entry.name}' has ${params.size} parameters (max $MAX_FIXED_PARAMS for fixed-arity registration)",
                        fn
                    )
                }
                val paramDecls = params.map { p ->
                    val name = p.name?.asString() ?: "p"
                    "$name: ${resolveKotlinType(p.type.resolve())}"
                }
                val callArgs = params.map { it.name?.asString() ?: "p" }.joinToString(", ")

                when (params.size) {
                    0 -> "registerFunction(\"${entry.name}\", $specsExpr) { $fnName() }"
                    else -> "registerFunction(\"${entry.name}\", $specsExpr) { ${paramDecls.joinToString(", ")} -> $fnName($callArgs) }"
                }
            }
        }
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

    /** Get function parameters excluding CallInfo (which is auto-injected). */
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

        val lastParam = params.last()
        val lastType = lastParam.type.resolve().declaration.simpleName.asString()
        return if (lastType == CALL_INFO_TYPE) params.dropLast(1) else params
    }

    private fun hasCallInfoParam(fn: KSFunctionDeclaration): Boolean {
        val params = fn.parameters
        if (params.isEmpty()) return false

        // Validate: CallInfo must be last
        params.forEachIndexed { index, param ->
            val typeName = param.type.resolve().declaration.simpleName.asString()
            if (typeName == CALL_INFO_TYPE && index != params.lastIndex) {
                logger.error("CallInfo must be the last parameter", fn)
            }
        }

        return params.last().type.resolve().declaration.simpleName.asString() == CALL_INFO_TYPE
    }

    private fun getVarargComponentType(param: KSValueParameter): String {
        val type = param.type.resolve()
        return resolveKotlinType(type)
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

    // ===== ParamSpec emission (Phase 5b) =====

    /**
     * Build the Kotlin source for a `List<ParamSpec>` covering [scriptParams].
     *
     * For optional params, attempts to extract the Kotlin default expression
     * via [DefaultValueExtractor] and pastes it into a thunk if the text
     * looks safe to embed (no `this`/`super` references). Unsafe defaults
     * leave `default = null`; the runtime then rejects named-call omissions
     * for those slots.
     */
    /**
     * Build the Kotlin source for a `List<ParamSpec>` covering [scriptParams].
     *
     * Note on type aliases: [resolveKotlinType] follows aliases through to the
     * underlying type, so a parameter declared as e.g. `IgnitorDslLike` (a
     * typealias for `Any`) ends up with `kotlinType = Any::class` in the
     * emitted spec. This is enough to drive named-arg binding (we only need
     * the name), but it loses the original alias for runtime type-checking
     * and intellisense — those rely on the separate KlangParam doc model
     * which preserves the alias text. Functions that want strict type
     * checking should declare concrete (non-alias) parameter types.
     */
    private fun paramSpecsListExpression(scriptParams: List<KSValueParameter>): String {
        if (scriptParams.isEmpty()) return "emptyList()"
        return scriptParams.joinToString(
            prefix = "listOf(",
            postfix = ")",
            separator = ", ",
        ) { p ->
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
            "ParamSpec(${parts.joinToString(", ")})"
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
