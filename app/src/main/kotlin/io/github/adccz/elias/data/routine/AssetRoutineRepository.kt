package io.github.adccz.elias.data.routine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.adccz.elias.core.coroutines.IoDispatcher
import io.github.adccz.elias.domain.model.FailureReason
import io.github.adccz.elias.domain.model.SourceResult
import io.github.adccz.elias.domain.routine.Exercise
import io.github.adccz.elias.domain.routine.ExerciseRepository
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The morning routine's content shipped in the app's assets (spec, M5). Changing it means editing the JSON and building
 * the app again; editing in the app may come with the settings (M8).
 */
class AssetRoutineRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val parser: RoutineParser,
    private val clock: Clock,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ExerciseRepository {
    override suspend fun exercises(): SourceResult<List<Exercise>> = load(EXERCISES, parser::parseExercises)

    private suspend fun <T> load(fileName: String, parse: (String) -> T): SourceResult<T> = withContext(ioDispatcher) {
        try {
            val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
            SourceResult.Success(parse(text), clock.instant())
        } catch (e: InvalidRoutineException) {
            SourceResult.Failure(FailureReason.INVALID_DATA, "$fileName: ${e.message}")
        } catch (e: IOException) {
            SourceResult.Failure(FailureReason.UNKNOWN, "$fileName: ${e.message}")
        }
    }

    private companion object {
        const val EXERCISES = "exercises.json"
    }
}
