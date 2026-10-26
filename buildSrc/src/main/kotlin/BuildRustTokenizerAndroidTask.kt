import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "Cargo maintains its own build cache")
abstract class BuildRustTokenizerAndroidTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val rustDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    abstract val cargoTargetDirectory: DirectoryProperty

    @get:Input
    abstract val abis: ListProperty<String>

    @get:Input
    abstract val ndkDirectory: Property<String>

    @get:Input
    abstract val cargoExecutable: Property<String>

    @TaskAction
    fun build() {
        val cargo = File(cargoExecutable.get())
        if (!cargo.isFile) {
            throw GradleException("Cargo executable not found: ${cargo.absolutePath}")
        }
        val ndk = File(ndkDirectory.get())
        if (!ndk.isDirectory) {
            throw GradleException("Android NDK directory not found: ${ndk.absolutePath}")
        }

        execOperations.exec { spec ->
            spec.workingDir(rustDirectory.get().asFile)
            spec.environment("ANDROID_NDK_HOME", ndk.absolutePath)
            spec.environment("CARGO_TARGET_DIR", cargoTargetDirectory.get().asFile.absolutePath)
            spec.commandLine(
                listOf(cargo.absolutePath, "ndk") +
                    abis.get().flatMap { listOf("-t", it) } +
                    listOf("-o", outputDirectory.get().asFile.absolutePath, "build", "--release", "--locked")
            )
        }
        abis.get().forEach { abi ->
            val library = outputDirectory.file("$abi/libhf_tokenizer_jni.so").get().asFile
            if (!library.isFile || library.length() == 0L) {
                throw GradleException("Missing tokenizer JNI library for $abi: ${library.absolutePath}")
            }
        }
    }
}
