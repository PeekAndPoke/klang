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

            libraryEntries.getOrPut(library) { LibraryEntries() }
                .functions.add(FunctionEntry(functionName, fn))
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
                    return value.declaration as? KSClassDeclaration
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
        appendLine("@file:Suppress(\"UNCHECKED_CAST\")")
        appendLine()
        appendLine("package $packageName")
        appendLine()
        appendLine("import io.peekandpoke.klang.script.KlangScriptLibrary")
        appendLine("import io.peekandpoke.klang.script.builder.*")
        appendLine("import io.peekandpoke.klang.script.runtime.*")
        appendLine("import io.peekandpoke.klang.script.types.*")
        appendLine()

        // Collect imports for annotated classes
        val imports = mutableSetOf<String>()
        entries.objects.forEach { obj ->
            obj.cls.qualifiedName?.asString()?.let { imports.add(it) }
        }
        entries.typeExtensions.forEach { ext ->
            ext.cls.qualifiedName?.asString()?.let { imports.add(it) }
            ext.typeDecl.qualifiedName?.asString()?.let { imports.add(it) }
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

        // Objects
        for (obj in entries.objects) {
            val normalMethods = obj.methods.filter { !isRawArgsMethod(it.fn) }
            val rawArgsMethods = obj.methods.filter { isRawArgsMethod(it.fn) }

            appendLine()
            appendLine("    // @Object(\"${obj.name}\") on ${obj.cls.simpleName.asString()}")
            appendLine("    registerObject(\"${obj.name}\", ${obj.cls.simpleName.asString()}) {")
            for (method in normalMethods) {
                appendLine("        ${generateMethodRegistration(method, obj.cls)}")
            }
            appendLine("    }")

            // Raw-args extension methods are registered at the builder level, outside registerObject
            for (method in rawArgsMethods) {
                appendLine("    ${generateRawArgsExtensionMethod(method, obj.cls)}")
            }
        }

        // Type extensions
        for (ext in entries.typeExtensions) {
            val typeName = ext.typeDecl.simpleName.asString()
            appendLine()
            appendLine("    // @TypeExtensions($typeName::class) on ${ext.cls.simpleName.asString()}")
            appendLine("    registerType<$typeName> {")
            for (method in ext.methods) {
                appendLine("        ${generateMethodRegistration(method, ext.cls, isTypeExtension = true)}")
            }
            appendLine("    }")
        }

        // Top-level functions
        for (fn in entries.functions) {
            appendLine()
            appendLine("    // @Function on ${fn.fn.simpleName.asString()}")
            appendLine("    ${generateFunctionRegistration(fn)}")
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

    /**
     * Generates a registerMethod call for a single method.
     *
     * For @Object methods: `registerMethod("name") { OwnerName.fn(args) }`
     * For @TypeExtensions methods: the first param is the receiver (`self`), passed as `this` from the lambda.
     *   Remaining params are the KlangScript-visible params.
     *   Generated: `registerMethod("name") { scriptArgs -> OwnerName.fn(this, scriptArgs) }`
     * For raw-args methods (first param is List<RuntimeValue>):
     *   Generated: `registerExtensionMethod(OwnerClass::class, "name") { _, args, loc -> Owner.fn(args, loc) }`
     */
    private fun generateMethodRegistration(
        method: MethodEntry,
        ownerCls: KSClassDeclaration,
        isTypeExtension: Boolean = false,
    ): String {
        val fn = method.fn
        val allParams = getScriptParams(fn)
        val hasCallInfo = hasCallInfoParam(fn)
        val ownerName = ownerCls.simpleName.asString()

        // For type extensions, first param is the receiver — strip it from script-visible params
        val scriptParams = if (isTypeExtension && allParams.isNotEmpty()) allParams.drop(1) else allParams
        val selfArg = if (isTypeExtension) "this, " else ""

        val isVararg = scriptParams.any { it.isVararg }

        return when {
            isVararg && hasCallInfo -> {
                val paramType = getVarargComponentType(scriptParams.first { it.isVararg })
                val returnType = resolveKotlinType(fn.returnType?.resolve())
                "registerVarargMethodWithCallInfo<$paramType, $returnType>(\"${method.name}\") { args, callInfo -> $ownerName.${fn.simpleName.asString()}(${selfArg}*args.toTypedArray(), callInfo) }"
            }

            isVararg -> {
                val paramType = getVarargComponentType(scriptParams.first { it.isVararg })
                val returnType = resolveKotlinType(fn.returnType?.resolve())
                "registerVarargMethod<$paramType, $returnType>(\"${method.name}\") { args -> $ownerName.${fn.simpleName.asString()}(${selfArg}*args.toTypedArray()) }"
            }

            else -> {
                val hasDefaults = scriptParams.any { it.hasDefault }

                if (hasDefaults) {
                    // Generate raw registerExtensionMethod with arity dispatch
                    // so Kotlin default parameters work when fewer args are provided
                    generateMethodWithDefaults(method, ownerCls, isTypeExtension, scriptParams)
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
                        0 -> "registerMethod(\"${method.name}\") { $ownerName.${fn.simpleName.asString()}($fullCallArgs) }"
                        else -> {
                            val paramDecl = paramNames.joinToString(", ")
                            "registerMethod(\"${method.name}\") { $paramDecl -> $ownerName.${fn.simpleName.asString()}($fullCallArgs) }"
                        }
                    }
                }
            }
        }
    }

    /**
     * Generates a raw registerExtensionMethod for methods with default parameters.
     * Dispatches by args.size so Kotlin's own default mechanism fills in missing values.
     */
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
            appendLine("$indent        $fnCall($selfArg$callArgs)")
        }

        // Final else: only required params
        val requiredArgs = scriptParams.filter { !it.hasDefault }.map { it.name?.asString() ?: "p" }.joinToString(", ")
        appendLine("$indent    } else {")
        appendLine("$indent        $fnCall($selfArg$requiredArgs)")
        appendLine("$indent    }")
        appendLine("$indent)")
    }

    private fun generateMethodWithDefaults(
        method: MethodEntry,
        ownerCls: KSClassDeclaration,
        isTypeExtension: Boolean,
        scriptParams: List<KSValueParameter>,
    ): String {
        val ownerName = ownerCls.simpleName.asString()
        val fnName = method.fn.simpleName.asString()
        val selfArg = if (isTypeExtension) "receiver, " else ""

        return buildString {
            appendLine("builder.registerExtensionMethod(cls, \"${method.name}\") { receiver, args, loc ->")
            generateArityDispatchBody(
                name = method.name,
                fnCall = "$ownerName.$fnName",
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

    private fun generateFunctionRegistration(entry: FunctionEntry): String {
        val fn = entry.fn
        val params = getScriptParams(fn)
        val hasCallInfo = hasCallInfoParam(fn)
        val isVararg = params.any { it.isVararg }
        val hasDefaults = params.any { it.hasDefault }
        val fnName = fn.simpleName.asString()

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
                // Use arity-dispatching raw registration so Kotlin defaults work
                buildString {
                    appendLine("registerFunctionRaw(\"${entry.name}\") { args, loc ->")
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
                    0 -> "registerFunction(\"${entry.name}\") { $fnName() }"
                    else -> "registerFunction(\"${entry.name}\") { ${paramDecls.joinToString(", ")} -> $fnName($callArgs) }"
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
        // For type extensions, first param is the receiver — exclude from docs
        val params = when {
            item.isRawArgs -> emptyList()
            item.isTypeExtension && allParams.isNotEmpty() -> allParams.drop(1)
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
                kdoc.samples.forEach { sample ->
                    appendLine("                    KlangCodeSample(code = \"\"\"${sample.code.escapeForRawString()}\"\"\", type = KlangCodeSampleType.${sample.type.name})")
                }
                appendLine("                ),")
            } else {
                appendLine("                samples = emptyList(),")
            }
            appendLine("                library = \"$libraryName\"")
            append("            )")
        }
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

    private fun resolveKotlinType(type: KSType?): String {
        if (type == null) return "Any"
        // Resolve type aliases to their underlying type
        val resolved = if (type.declaration is KSTypeAlias) {
            (type.declaration as KSTypeAlias).type.resolve()
        } else {
            type
        }
        val name = resolved.declaration.simpleName.asString()
        val nullable = if (resolved.nullability == Nullability.NULLABLE) "?" else ""
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
