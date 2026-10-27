package io.github.adccz.elias.domain.plan

import io.github.adccz.elias.domain.model.SourceResult

/** Loads the personal plan. Implemented in the data layer. */
interface PlanRepository {
    suspend fun loadPlan(): SourceResult<LoadedPlan>
}
