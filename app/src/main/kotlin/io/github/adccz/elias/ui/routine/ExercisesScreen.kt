package io.github.adccz.elias.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.domain.routine.Exercise
import io.github.adccz.elias.domain.routine.ExerciseAmount
import io.github.adccz.elias.domain.routine.ExerciseArea
import io.github.adccz.elias.domain.routine.Exercises
import io.github.adccz.elias.ui.theme.EliasTheme
import io.github.adccz.elias.ui.voice.SpeechStatusText
import io.github.adccz.elias.ui.voice.SpeechUiState
import java.time.LocalDate

@Composable
fun ExercisesRoute(onBack: () -> Unit, viewModel: ExercisesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExercisesScreen(
        uiState = uiState,
        onBack = onBack,
        onExerciseWithMe = viewModel::exerciseWithMe,
        onStop = viewModel::stop,
    )
}

/** Today's four exercises, each with its area, what to do and how long, and Cvič se mnou. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    uiState: ExercisesUiState,
    onBack: () -> Unit,
    onExerciseWithMe: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercises_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.exercises_today), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.exercises_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                !uiState.isLoaded -> item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

                uiState.failed -> item {
                    Text(stringResource(R.string.exercises_failed), color = MaterialTheme.colorScheme.error)
                }

                else -> {
                    item { Text(Exercises.SAFETY, style = MaterialTheme.typography.bodyMedium) }
                    item { ExerciseWithMe(uiState.speech, uiState.exercises.isNotEmpty(), onExerciseWithMe, onStop) }
                    items(uiState.exercises, key = { it.id }) { ExerciseCard(it) }
                }
            }
        }
    }
}

/** Cvič se mnou with what Elias is doing, and Zastavit while it prepares or speaks. */
@Composable
private fun ExerciseWithMe(speech: SpeechUiState, enabled: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val busy = speech is SpeechUiState.Preparing || speech is SpeechUiState.Speaking
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onStart, enabled = enabled && speech !is SpeechUiState.Preparing) {
                Text(stringResource(R.string.exercises_with_me))
            }
            if (busy) {
                OutlinedButton(onClick = onStop) { Text(stringResource(R.string.speech_stop)) }
            }
        }
        SpeechStatusText(speech)
        Text(
            stringResource(R.string.exercises_with_me_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExerciseCard(exercise: Exercise) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                exercise.area.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            Text(exercise.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                amountText(exercise),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "10×", "20 s · na každou stranu". */
@Composable
private fun amountText(exercise: Exercise): String {
    val amount = when (val amount = exercise.amount) {
        is ExerciseAmount.Times -> stringResource(R.string.exercises_times, amount.times)
        is ExerciseAmount.Seconds -> stringResource(R.string.exercises_seconds, amount.seconds)
    }
    return if (exercise.bothSides) "$amount · ${stringResource(R.string.exercises_both_sides)}" else amount
}

@Preview(showBackground = true, locale = "cs")
@Composable
private fun ExercisesScreenPreview() {
    EliasTheme {
        ExercisesScreen(
            uiState = ExercisesUiState(
                day = LocalDate.of(2026, 10, 29),
                isLoaded = true,
                exercises = listOf(
                    Exercise(
                        "a",
                        ExerciseArea.NECK_AND_SHOULDERS,
                        "Kroužení rameny",
                        "Krouž rameny pomalu dozadu.",
                        ExerciseAmount.Times(10),
                        bothSides = false,
                    ),
                    Exercise(
                        "b",
                        ExerciseArea.BACK_AND_CHEST,
                        "Rotace trupu vsedě",
                        "Otoč trup na jednu stranu.",
                        ExerciseAmount.Seconds(20),
                        bothSides = true,
                    ),
                ),
            ),
            onBack = {},
            onExerciseWithMe = {},
            onStop = {},
        )
    }
}
