package io.github.adccz.elias.domain.journal

import io.github.adccz.elias.domain.briefing.AiFailureReason
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.plan.LoadedPlan
import io.github.adccz.elias.domain.plan.Plan
import io.github.adccz.elias.domain.plan.PlanRepository
import io.github.adccz.elias.domain.plan.WeeklyTheme
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StepSuggestionTest {
    private val thursday = LocalDate.of(2026, 10, 29)

    private class RecordingSuggester(val answer: StepResult = StepResult.Success("Zeptej se na jednu věc.")) :
        StepSuggester {
        val requests = mutableListOf<StepRequest>()

        override suspend fun suggest(request: StepRequest): StepResult {
            requests += request
            return answer
        }
    }

    private fun plans(isSample: Boolean = false) = object : PlanRepository {
        override suspend fun loadPlan() = SourceResult.Success(
            LoadedPlan(
                Plan(
                    name = "Test",
                    validFrom = thursday.minusDays(10),
                    validTo = thursday.plusDays(10),
                    phases = emptyList(),
                    dailyMinimum = emptyList(),
                    rules = emptyList(),
                    weeklyThemes = listOf(WeeklyTheme(LocalDate.of(2026, 10, 26), "Vděčnost", "Zapsat tři dobré věci")),
                    morningReadings = emptyList(),
                    saturdayStudies = emptyList(),
                    recurringItems = emptyList(),
                    oneTimeItems = emptyList(),
                    deadlines = emptyList(),
                ),
                isSample = isSample,
            ),
            Instant.EPOCH,
        )
    }

    @Test
    fun `sends only what to improve and the weekly theme`() = runTest {
        val suggester = RecordingSuggester()

        val result = SuggestStep(suggester, plans())(thursday, "  Méně odkládat úkoly \n")

        assertEquals(StepResult.Success("Zeptej se na jednu věc."), result)
        assertEquals(
            listOf(StepRequest("Méně odkládat úkoly", "Vděčnost", "Zapsat tři dobré věci")),
            suggester.requests,
        )
    }

    @Test
    fun `leaves out the theme of the fictional sample plan`() = runTest {
        val suggester = RecordingSuggester()

        SuggestStep(suggester, plans(isSample = true))(thursday, "Číst víc")

        assertEquals(StepRequest("Číst víc", null, null), suggester.requests.single())
    }

    @Test
    fun `works without a plan`() = runTest {
        val suggester = RecordingSuggester()
        val noPlan = object : PlanRepository {
            override suspend fun loadPlan() = SourceResult.Failure(FailureReason.INVALID_DATA)
        }

        SuggestStep(suggester, noPlan)(thursday, "Číst víc")

        assertEquals(StepRequest("Číst víc", null, null), suggester.requests.single())
    }

    @Test
    fun `asks nothing when nothing to improve was written`() = runTest {
        val suggester = RecordingSuggester()

        val result = SuggestStep(suggester, plans())(thursday, "   ")

        assertEquals(AiFailureReason.DISABLED, (result as StepResult.Failure).reason)
        assertEquals(emptyList<StepRequest>(), suggester.requests)
    }

    @Test
    fun `a suggester that throws becomes a failure`() = runTest {
        val broken = object : StepSuggester {
            override suspend fun suggest(request: StepRequest): StepResult = error("bug")
        }

        val result = SuggestStep(broken, plans())(thursday, "Číst víc")

        assertEquals(AiFailureReason.UNKNOWN, (result as StepResult.Failure).reason)
    }

    @Test
    fun `a step must be one short line without markup`() {
        assertNull(StepValidator.problem("Zítra začni nejtěžším úkolem hned ráno a dokonči ho do oběda."))
        assertNotNull(StepValidator.problem(" "))
        assertNotNull(StepValidator.problem("Krok jedna.\nKrok dva."))
        assertNotNull(StepValidator.problem("**Zeptej se**"))
        assertNotNull(StepValidator.problem(List(StepValidator.MAX_WORDS + 1) { "slovo" }.joinToString(" ")))
    }
}
