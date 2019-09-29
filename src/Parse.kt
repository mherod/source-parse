import java.io.File

fun main() {

    val rootDir = File(".")

    rootDir
        .walk()
        .iterator()
        .asSequence()
        .filter { "/src/" in it.absolutePath }
        .filter { it.name.endsWith(".kt") || it.name.endsWith(".java") }
        .map { it to it.readText() }
        .flatMap { (file, sourceContent) ->
            indexFile(sourceContent, file, rootDir)
        }
        .map { sourceClass ->
            hydrateImports(sourceClass, rootDir)
        }
        .map { sourceClass ->
            hydrateProperties(sourceClass, rootDir)
        }
        .forEach {
            println(it)
        }
}

private fun hydrateImports(sourceClass: SourceClass, rootDir: File): SourceClass {
    return when {
        sourceClass.file.name.endsWith(".kt") -> {
            val readText = File(rootDir, sourceClass.file.path).readText()
            sourceClass.copy(imports = extractImports(readText))
        }
        else -> sourceClass
    }
}

private fun hydrateProperties(sourceClass: SourceClass, rootDir: File): SourceClass {
    return when {
        sourceClass.file.name.endsWith(".kt") -> {
            val readText = File(rootDir, sourceClass.file.path).readText()
            sourceClass.copy(properties = extractSourceProperties(readText))
        }
        else -> sourceClass
    }
}

fun extractImports(readText: String): Set<String> {
    return "import (\\S+)"
        .toRegex()
        .findAll(readText)
        .flatMap { it.groupValues.drop(1).asSequence() }
        .toSet()
}

private fun extractSourceProperties(readText: String): Set<SourceProperty> {
    return "va[lr] ((\\w+)\\s*:?\\s*(\\w+)?)"
        .toRegex()
        .findAll(readText)
        .flatMap {
            it.groupValues
                .drop(1)
                .chunked(3)
                .map { list -> list.drop(1) }
                .asSequence()
        }
        .asSequence()
        .mapNotNull { chunk ->
            chunk.getOrNull(0)?.let { name ->
                SourceProperty(name = name, type = chunk.getOrNull(1))
            }
        }
        .toSet()
}

private fun indexFile(
    sourceContent: String,
    file: File,
    rootDir: File
): Sequence<SourceClass> {

    val sPackage = extractPackage(sourceContent)

    return "class (\\w+)".toRegex()
        .find(sourceContent)
        ?.groupValues
        ?.asSequence()
        ?.drop(1)
        ?.map {
            SourceClass(
                file = file.relativeTo(rootDir),
                sPackage = sPackage,
                sClassName = it
            )
        }.orEmpty()
}

private fun extractPackage(sourceContent: String): String? {

    return "package (\\S+)"
        .toRegex()
        .find(sourceContent)
        ?.groupValues
        ?.asSequence()
        ?.map { it.trim { c -> c == ';' } }
        ?.lastOrNull()
}

data class SourceProperty(
    val type: String? = null,
    val name: String
)

data class SourceClass(
    val file: File,
    val sPackage: String? = null,
    val sClassName: String,
    val imports: Set<String> = emptySet(),
    val properties: Set<SourceProperty> = emptySet()
)