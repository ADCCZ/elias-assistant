package io.github.adccz.elias.domain.schedule

/**
 * How the evening briefing starts when its alarm fires (the user's choice in M4: light up the screen and speak).
 *
 * Android 17 always lets a visible app play sound, so the briefing screen is the reliable way. A background service
 * on the alarm stream works today, but the emulator already logs that Android 17 would mute it (D33).
 */
enum class EveningStart {
    /** The briefing screen lights up over the lock screen and starts speaking. */
    SCREEN,

    /** The playback service starts right away, without a screen. */
    SOUND,
    ;

    companion object {
        /**
         * The screen when the app may show it ([canShowFullScreen]) and the phone lies unused. While the user is on
         * the phone, Android shows a full-screen notification only as a banner, so the briefing starts as sound.
         */
        fun choose(canShowFullScreen: Boolean, phoneInUse: Boolean): EveningStart =
            if (canShowFullScreen && !phoneInUse) SCREEN else SOUND
    }
}
