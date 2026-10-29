import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.room3)
}

// local.properties is git-ignored. Besides sdk.dir it holds our secrets, such as ANTHROPIC_API_KEY.
// Reading it through `providers` keeps the build compatible with Gradle's configuration cache.
val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull
        ?.let { load(it.reader()) }
}

/** Turns a value into a Java string literal for a BuildConfig field. */
fun String.asJavaStringLiteral(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "io.github.adccz.elias"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.adccz.elias"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // Empty when the key is missing, so the app (and CI) still builds. Elias then uses its template.
        val anthropicApiKey = localProperties.getProperty("ANTHROPIC_API_KEY", "")
        buildConfigField("String", "ANTHROPIC_API_KEY", anthropicApiKey.asJavaStringLiteral())
    }

    // The voice's native libraries are big, so there is one APK per processor type: 64-bit ARM for phones and x86_64
    // for the emulator. installDebug picks the one that fits the connected device.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        jniLibs {
            // sherpa-onnx's C and C++ APIs are for native apps. The JNI library needs only ONNX Runtime.
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
        }
    }

    androidResources {
        // The voice model is read straight from the APK; compressing it would only cost time.
        noCompress += "onnx"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.framework)
    ksp(libs.androidx.room3.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.sherpa.onnx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.ktor.client.mock)
}

// Local unit tests run on the JUnit Platform (JUnit 6), see docs/DECISIONS.md, D5.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

ktlint {
    version.set(libs.versions.ktlint)
}

// The database schema of every version is kept in git, so changes are reviewed and migrations can be tested.
room3 {
    schemaDirectory("$projectDir/schemas")
}

/**
 * Downloads the Piper voice "Jirka" prepared for sherpa-onnx, checks its SHA-256 and unpacks it as app assets
 * (`assets/voice/jirka/`). The model is about 60 MB, so it is not in git (D38). The archive is kept in the Gradle
 * user home, so a clean build does not download it again. Only the Czech pronunciation data is kept.
 */
abstract class PrepareVoiceModel : DefaultTask() {
    @get:Input abstract val url: Property<String>

    @get:Input abstract val sha256: Property<String>

    @get:Internal abstract val downloadCache: DirectoryProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @get:Inject abstract val archives: ArchiveOperations

    @get:Inject abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun prepare() {
        val archive = downloadCache.file(url.get().substringAfterLast('/')).get().asFile
        if (!archive.isFile || archive.sha256() != sha256.get()) {
            archive.parentFile.mkdirs()
            val partial = File(archive.path + ".part")
            URI(url.get()).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            val actual = partial.sha256()
            check(actual == sha256.get()) { "The voice model has SHA-256 $actual, expected ${sha256.get()}" }
            check(partial.renameTo(archive) || (archive.delete() && partial.renameTo(archive))) {
                "Could not store $archive"
            }
        }
        fileSystem.delete { delete(outputDir) }
        fileSystem.copy {
            from(archives.tarTree(archives.bzip2(archive)))
            into(outputDir)
            eachFile { path = path.replaceFirst(Regex("^[^/]+/"), "voice/jirka/") }
            // Dictionaries of other languages: about 15 MB the Czech voice never reads. espeak-ng also needs the
            // English one: without it the native code crashed on the first sentence.
            exclude { it.name.endsWith("_dict") && it.name != "cs_dict" && it.name != "en_dict" }
            includeEmptyDirs = false
        }
    }

    private fun File.sha256(): String = MessageDigest.getInstance("SHA-256").let { digest ->
        inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}

// CI builds without the voice (-Pelias.voiceModel=skip): the tests do not need it, and the app falls back to the
// phone's voice when the model is missing.
if (providers.gradleProperty("elias.voiceModel").orNull != "skip") {
    val prepareVoiceModel = tasks.register<PrepareVoiceModel>("prepareVoiceModel") {
        url.set(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-cs_CZ-jirka-medium.tar.bz2",
        )
        // The digest GitHub publishes for this release asset.
        sha256.set("321aafd83dd89b3d5b2c36c5898e0710d4f472f3c191cb7f73901a89b7f8539f")
        downloadCache.set(File(gradle.gradleUserHomeDir, "caches/elias/voice"))
        outputDir.set(layout.buildDirectory.dir("generated/voiceModel"))
    }
    androidComponents {
        onVariants { variant ->
            variant.sources.assets?.addGeneratedSourceDirectory(prepareVoiceModel, PrepareVoiceModel::outputDir)
        }
    }
}
