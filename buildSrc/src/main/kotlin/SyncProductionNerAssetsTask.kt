import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

@CacheableTask
abstract class SyncProductionNerAssetsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val modelFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val tokenizerFile: RegularFileProperty

    @get:Input
    abstract val expectedModelSha256: Property<String>

    @get:Input
    abstract val expectedTokenizerSha256: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun sync() {
        val model = modelFile.get().asFile
        val tokenizer = tokenizerFile.get().asFile
        verify(model, expectedModelSha256.get())
        verify(tokenizer, expectedTokenizerSha256.get())

        val output = outputDirectory.get().asFile.resolve(ASSET_ROOT)
        output.parentFile.deleteRecursively()
        output.mkdirs()

        val copiedModel = model.copyTo(output.resolve(MODEL_OUTPUT_NAME), overwrite = true)
        val copiedTokenizer = tokenizer.copyTo(output.resolve(TOKENIZER_OUTPUT_NAME), overwrite = true)
        output.resolve(MANIFEST_NAME).writeText(
            listOf(copiedModel, copiedTokenizer).joinToString("\n", postfix = "\n") { file ->
                "${file.sha256()}  ${file.name}"
            }
        )
    }

    private fun verify(file: File, expectedSha256: String) {
        if (!file.isFile) {
            throw GradleException("Missing production NER asset: ${file.absolutePath}")
        }
        val actual = file.sha256()
        if (actual != expectedSha256) {
            throw GradleException(
                "SHA-256 mismatch for production NER asset ${file.name}. " +
                    "Expected $expectedSha256 but found $actual."
            )
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MODEL_OUTPUT_NAME = "model.onnx"
        const val TOKENIZER_OUTPUT_NAME = "tokenizer.json"
        const val MANIFEST_NAME = "manifest.sha256"
        const val ASSET_ROOT = "ner_mobilebert"
    }
}
