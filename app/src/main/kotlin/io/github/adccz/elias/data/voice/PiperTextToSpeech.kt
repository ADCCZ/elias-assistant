package io.github.adccz.elias.data.voice

import android.content.Context
import android.os.Build
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adccz.elias.R
import io.github.adccz.elias.core.coroutines.IoDispatcher
import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.voice.BuiltInVoice
import io.github.adccz.elias.domain.voice.SpeechClip
import io.github.adccz.elias.domain.voice.SpeechRate
import io.github.adccz.elias.domain.voice.SynthesisResult
import io.github.adccz.elias.domain.voice.TtsEngine
import io.github.adccz.elias.domain.voice.VoiceStatus
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The app's own voice, "Oldřich 3.0": the Piper voice "Jirka" (male, Czech, CC0 data) run on the phone by
 * sherpa-onnx with ONNX Runtime. Nothing leaves the phone. The model is an app asset (D38); a build without it
 * (CI) has no built-in voice, and Elias then speaks with the phone's voice.
 *
 * The Android part stays thin like [AndroidTextToSpeech]: load the model, write one WAV clip per section, release it.
 * Choosing between this voice and the phone's lives in the domain (SelectedVoice).
 */
@Singleton
class PiperTextToSpeech @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val files: VoiceFiles,
) : BuiltInVoice {
    /** One synthesis at a time: the model takes a few hundred MB of memory while loaded. */
    private val mutex = Mutex()

    /**
     * A crash in native code ends the whole app, so no try/catch can catch it. This file exists only while the voice
     * speaks. Found at the next start, it means the voice crashed: it stays off until the app is installed again.
     */
    private val crashGuard: File get() = File(context.noBackupFilesDir, "voice/speaking-since-install-$installTime")

    private val installTime: Long by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    }

    override val crashedBefore: Boolean by lazy { crashGuard.exists() }

    override val status: VoiceStatus? by lazy {
        if (hasModel() && hasSupportedProcessor() && !crashedBefore) {
            VoiceStatus.Ready(
                TtsEngine(context.packageName, context.getString(R.string.voice_builtin_name)),
                VOICE_NAME,
            )
        } else {
            null
        }
    }

    override suspend fun synthesize(sections: List<BriefingSection>, rate: SpeechRate): SynthesisResult {
        val ready = status as? VoiceStatus.Ready ?: return SynthesisResult.VoiceNotReady(VoiceStatus.EngineUnavailable)
        return mutex.withLock {
            withContext(io) {
                crashGuard.parentFile?.mkdirs()
                crashGuard.createNewFile()
                try {
                    speakClips(sections, rate)
                } catch (e: Throwable) {
                    // Native code can also fail with an error Kotlin sees (a missing library, too little memory).
                    // The caller then falls back to the phone's voice.
                    SynthesisResult.Failure("${ready.engine.label}: $e")
                } finally {
                    crashGuard.delete()
                }
            }
        }
    }

    private fun speakClips(sections: List<BriefingSection>, rate: SpeechRate): SynthesisResult {
        val tts = OfflineTts(assetManager = context.assets, config = config(pronunciationData()))
        val folder = files.newBriefingFolder()
        try {
            val clips = sections.mapIndexed { index, section ->
                val audio = tts.generate(section.text, sid = 0, speed = rate.multiplier)
                val file = files.clip(folder, index)
                if (audio.samples.isEmpty() || !audio.save(file.path)) {
                    folder.deleteRecursively()
                    return SynthesisResult.Failure("No audio for ${section.kind}")
                }
                SpeechClip(section.kind, file.path)
            }
            return SynthesisResult.Success(clips)
        } finally {
            tts.release()
        }
    }

    private fun config(dataDir: File) = OfflineTtsConfig(
        model = OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(
                model = "$ASSET_DIR/$MODEL_FILE",
                tokens = "$ASSET_DIR/$TOKENS_FILE",
                dataDir = dataDir.path,
            ),
            numThreads = THREADS,
            debug = false,
            provider = "cpu",
        ),
    )

    /**
     * espeak-ng, which turns Czech text into phonemes, reads its data from files, not from the APK. The data (under
     * 1 MB) is copied out once per app process, so an app update always brings its own copy.
     */
    private val copiedData: File by lazy {
        val target = File(context.noBackupFilesDir, "voice/$DATA_DIR")
        target.deleteRecursively()
        copyAssetDir("$ASSET_DIR/$DATA_DIR", target)
        target
    }

    private fun pronunciationData(): File = copiedData

    private fun copyAssetDir(assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> target.outputStream().use { input.copyTo(it) } }
        } else {
            target.mkdirs()
            children.forEach { copyAssetDir("$assetPath/$it", File(target, it)) }
        }
    }

    private fun hasModel(): Boolean {
        val names = context.assets.list(ASSET_DIR).orEmpty().toSet()
        return MODEL_FILE in names && TOKENS_FILE in names && DATA_DIR in names
    }

    /** The app ships the voice's native libraries for 64-bit ARM and x86_64 only. */
    private fun hasSupportedProcessor(): Boolean = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }

    private companion object {
        const val ASSET_DIR = "voice/jirka"
        const val MODEL_FILE = "cs_CZ-jirka-medium.onnx"
        const val TOKENS_FILE = "tokens.txt"
        const val DATA_DIR = "espeak-ng-data"
        const val VOICE_NAME = "Jirka"

        /** Half of the A54's eight cores: fast enough, and the phone stays responsive. */
        const val THREADS = 4
    }
}
