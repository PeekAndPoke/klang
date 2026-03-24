package io.peekandpoke.klang.script.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
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
            "Long" to "Number",
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
        appendLine()
        appendLine("package $packageName")
        appendLine()
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
        appendLine("actual fun KlangScriptExtensionBuilder.register${capitalizedName}Generated() {")

        // Objects
        for (obj in entries.objects) {
            appendLine()
            appendLine("    // @Object(\"${obj.name}\") on ${obj.cls.simpleName.asString()}")
            appendLine("    registerObject(\"${obj.name}\", ${obj.cls.simpleName.asString()}) {")
            for (method in obj.methods) {
                appendLine("        ${generateMethodRegistration(method, obj.cls)}")
            }
            appendLine("    }")
        }

        // Type extensions
        for (ext in entries.typeExtensions) {
            val typeName = ext.typeDecl.simpleName.asString()
            appendLine()
            appendLine("    // @TypeExtensions($typeName::class) on ${ext.cls.simpleName.asString()}")
            appendLine("    registerType<$typeName> {")
            for (method in ext.methods) {
                appendLine("        ${generateMethodRegistration(method, ext.cls)}")
            }
            appendLine("    }")
        }

        // Top-level functions
        for (fn in entries.functions) {
            appendLine()
            appendLine("    // @Function on ${fn.fn.simpleName.asString()}")
            appendLine("    ${generateFunctionRegistration(fn)}")
        }

        appendLine("}")

        // === Documentation ===
        appendLine()
        generateDocsCode(libraryName, capitalizedName, entries)
    }

    private fun generateMethodRegistration(method: MethodEntry, ownerCls: KSClassDeclaration): String {
        val fn = method.fn
        val params = getScriptParams(fn)
        val hasCallInfo = hasCallInfoParam(fn)
        val isVararg = params.any { it.isVararg }
        val ownerName = ownerCls.simpleName.asString()

        return when {
            isVararg && hasCallInfo -> {
                val paramType = getVarargComponentType(params.first { it.isVararg })
                "registerVarargMethodWithCallInfo<$paramType, Any>(\"${method.name}\") { args, callInfo -> $ownerName.${fn.simpleName.asString()}(args, callInfo) }"
            }

            isVararg -> {
                val paramType = getVarargComponentType(params.first { it.isVararg })
                "registerVarargMethod<$paramType, Any>(\"${method.name}\") { args -> $ownerName.${fn.simpleName.asString()}(args) }"
            }

            else -> {
                val paramNames = params.map { p ->
                    val name = p.name?.asString() ?: "p"
                    "$name: ${resolveKotlinType(p.type.resolve())}"
                }
                val callArgs = params.map { it.name?.asString() ?: "p" }
                val callArgsStr = callArgs.joinToString(", ")

                when (params.size) {
                    0 -> "registerMethod(\"${method.name}\") { $ownerName.${fn.simpleName.asString()}() }"
                    else -> {
                        val paramDecl = paramNames.joinToString(", ")
                        "registerMethod(\"${method.name}\") { $paramDecl -> $ownerName.${fn.simpleName.asString()}($callArgsStr) }"
                    }
                }
            }
        }
    }

    private fun generateFunctionRegistration(entry: FunctionEntry): String {
        val fn = entry.fn
        val params = getScriptParams(fn)
        val hasCallInfo = hasCallInfoParam(fn)
        val isVararg = params.any { it.isVararg }
        val fnName = fn.simpleName.asString()

        return when {
            isVararg && hasCallInfo -> {
                val paramType = getVarargComponentType(params.first { it.isVararg })
                val returnType = resolveKotlinType(fn.returnType?.resolve())
                "registerVarargFunctionWithCallInfo<$paramType, $returnType>(\"${entry.name}\") { args, callInfo -> $fnName(args, callInfo) }"
            }

            isVararg -> {
                val paramType = getVarargComponentType(params.first { it.isVararg })
                val returnType = resolveKotlinType(fn.returnType?.resolve())
                "registerVarargFunction<$paramType, $returnType>(\"${entry.name}\") { args -> $fnName(args) }"
            }

            else -> {
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

    private data class DocItem(val scriptName: String, val receiver: String?, val fn: KSFunctionDeclaration)

    private fun StringBuilder.generateDocsCode(
        libraryName: String,
        capitalizedName: String,
        entries: LibraryEntries,
    ) {

        val docItems = mutableListOf<DocItem>()

        for (obj in entries.objects) {
            for (method in obj.methods) {
                docItems.add(DocItem(method.name, obj.name, method.fn))
            }
        }
        for (ext in entries.typeExtensions) {
            val displayName = typeDisplayName(ext.typeDecl.simpleName.asString())
            for (method in ext.methods) {
                docItems.add(DocItem(method.name, displayName, method.fn))
            }
        }
        for (fn in entries.functions) {
            docItems.add(DocItem(fn.name, null, fn.fn))
        }

        if (docItems.isEmpty()) return

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

        appendLine("/** Generated documentation for the '$libraryName' library. */")
        appendLine("actual val generated${capitalizedName}Docs: Map<String, KlangSymbol> = buildMap {")
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
            generateCallableDoc(item, kdoc)
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

    private fun generateCallableDoc(item: DocItem, kdoc: ParsedKDoc): String {
        val fn = item.fn
        val params = getScriptParams(fn)
        val returnType = fn.returnType?.resolve()
        val description = kdoc.description.escapeTripleQuote()
        val returnDoc = kdoc.returnDoc.replace("\n", " ").escapeTripleQuote()

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
                    val paramDesc = (kdoc.params[paramName] ?: "").replace("\n", " ").escapeTripleQuote()
                    val paramUiTools = kdoc.paramTools[paramName] ?: emptyList()
                    val paramSubFields = kdoc.paramSubs[paramName]

                    append("                    KlangParam(")
                    append("name = \"$paramName\", ")
                    append("type = ${generateKlangType(paramType)}")
                    if (param.isVararg) append(", isVararg = true")
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
                    appendLine("                    \"\"\"${sample.escapeTripleQuote()}\"\"\"")
                }
                appendLine("                )")
            } else {
                appendLine("                samples = emptyList()")
            }
            append("            )")
        }
    }

    // ===== Type helpers =====

    /** Get function parameters excluding CallInfo (which is auto-injected). */
    private fun getScriptParams(fn: KSFunctionDeclaration): List<KSValueParameter> {
        val params = fn.parameters
        if (params.isEmpty()) return params
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
        val name = type.declaration.simpleName.asString()
        val nullable = if (type.nullability == Nullability.NULLABLE) "?" else ""
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

    private fun String.escapeTripleQuote(): String {
        return this.replace("\"\"\"", "\\\"\\\"\\\"")
    }
}
