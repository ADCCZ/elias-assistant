package io.github.adccz.elias.data.plan

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adccz.elias.core.coroutines.IoDispatcher
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.plan.LoadedPlan
import io.github.adccz.elias.domain.plan.PlanRepository
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Loads the plan from the app's assets.
 *
 * The personal `plan.json` is git-ignored. When it is missing (for example in CI),
 * the fictional `plan.example.json` is used and marked as a sample.
 */
class AssetPlanRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val parser: PlanParser,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlanRepository {
    override suspend fun loadPlan(): SourceResult<LoadedPlan> = withContext(ioDispatcher) {
        val hasPersonalPlan = PERSONAL_PLAN in context.assets.list("").orEmpty()
        val fileName = if (hasPersonalPlan) PERSONAL_PLAN else SAMPLE_PLAN
        try {
            val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
            SourceResult.Success(LoadedPlan(parser.parse(text), isSample = !hasPersonalPlan), clock.instant())
        } catch (e: InvalidPlanException) {
            SourceResult.Failure(FailureReason.INVALID_DATA, "$fileName: ${e.message}")
        } catch (e: IOException) {
            SourceResult.Failure(FailureReason.UNKNOWN, "$fileName: ${e.message}")
        }
    }

    private companion object {
        const val PERSONAL_PLAN = "plan.json"
        const val SAMPLE_PLAN = "plan.example.json"
    }
}
