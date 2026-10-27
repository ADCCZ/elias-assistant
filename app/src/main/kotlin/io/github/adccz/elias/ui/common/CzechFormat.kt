package io.github.adccz.elias.ui.common

import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Czech formatting for the screens. Spoken text gets its own formatting in the briefing (M2). */
object CzechFormat {
    private val czech: Locale = Locale.forLanguageTag("cs-CZ")

    /** "7:05", "16:48". */
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", czech)

    /** "úterý 27. října 2026". */
    val fullDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d. MMMM yyyy", czech)

    /** "pátek 30. 10.". */
    val dayAndDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d. M.", czech)

    /** "30. 11. 2026". */
    val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d. M. yyyy", czech)

    /** "7 °C", "−2 °C". Whole degrees are enough for a briefing. */
    fun temperature(celsius: Double): String = "${celsius.roundToInt()} °C".replace('-', '−')

    /** "1,8" with a Czech decimal comma. */
    fun oneDecimal(value: Double): String = String.format(czech, "%.1f", value)
}
