package io.github.adccz.elias.data.voice

import io.github.adccz.elias.domain.briefing.BriefingKind
import io.github.adccz.elias.domain.briefing.BriefingSection
import io.github.adccz.elias.domain.briefing.BriefingText
import io.github.adccz.elias.domain.briefing.SectionKind
import io.github.adccz.elias.domain.voice.BriefingRequest
import io.github.adccz.elias.domain.voice.PlaybackTrigger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Turns a [BriefingRequest] into text for an Intent extra and back, so the app and an alarm can hand it to the
 * playback service. Pure Kotlin, so the round trip is unit-tested. The text never leaves the app.
 */
object BriefingRequestCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(request: BriefingRequest): String = json.encodeToString(
        StoredRequest.serializer(),
        StoredRequest(
            kind = request.kind.name,
            trigger = request.trigger.name,
            sections = (request as? BriefingRequest.Written)?.briefing?.sections?.map {
                StoredSection(it.kind.name, it.text)
            },
        ),
    )

    /** The request, or null when [text] is missing, broken or from an app version with other names. */
    fun decode(text: String?): BriefingRequest? {
        if (text == null) return null
        val stored = runCatching { json.decodeFromString(StoredRequest.serializer(), text) }.getOrNull() ?: return null
        val kind = enumOrNull<BriefingKind>(stored.kind) ?: return null
        val trigger = enumOrNull<PlaybackTrigger>(stored.trigger) ?: return null
        if (kind == BriefingKind.EXERCISES) return BriefingRequest.Exercises
        val sections = stored.sections ?: return BriefingRequest.FromToday(kind, trigger)
        return BriefingRequest.Written(
            BriefingText(
                kind,
                sections.map { section -> BriefingSection(enumOrNull(section.kind) ?: return null, section.text) },
            ),
        )
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }

    @Serializable
    private class StoredRequest(val kind: String, val trigger: String, val sections: List<StoredSection>? = null)

    @Serializable
    private class StoredSection(val kind: String, val text: String)
}
