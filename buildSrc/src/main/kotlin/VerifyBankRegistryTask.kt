import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class VerifyBankRegistryTask : DefaultTask() {
    @get:InputFile
    abstract val registrySource: RegularFileProperty

    @get:InputDirectory
    abstract val logosDirectory: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val source = registrySource.get().asFile.readText()
        val entryLines = source.lineSequence().map { it.trim() }
            .filter { it.startsWith("verified(\"") || it.startsWith("unsupported(\"") }
            .toList()
        val entryRegex = Regex("(?:verified|unsupported)\\(\"([^\"]+)\"")
        val keys = entryLines.flatMap { line ->
            entryRegex.findAll(line).map { it.groupValues[1] }.toList()
        }
        val duplicates = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val logoKeys = logosDirectory.get().asFile.listFiles()
            .orEmpty().filter { it.isDirectory }.map { it.name }.toSet()
        val missingRegistry = logoKeys - keys.toSet()
        val missingLogos = keys.toSet() - logoKeys
        val verifiedCount = entryLines.sumOf { line -> Regex("verified\\(\"").findAll(line).count() }

        val errors = buildList {
            if (duplicates.isNotEmpty()) add("Duplicate keys: ${duplicates.sorted()}")
            if (missingRegistry.isNotEmpty()) add("Logo directories without registry entries: ${missingRegistry.sorted()}")
            if (missingLogos.isNotEmpty()) add("Registry entries without logo directories: ${missingLogos.sorted()}")
            if (verifiedCount < 10) add("Only $verifiedCount actionable banks; at least 10 required")
        }
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                "Registry entries: ${keys.size}\nVerified actionable banks: $verifiedCount\n" +
                    if (errors.isEmpty()) "Status: PASS\n" else "Status: FAIL\n${errors.joinToString("\n")}\n"
            )
        }
        if (errors.isNotEmpty()) throw GradleException(errors.joinToString("\n"))
    }
}
