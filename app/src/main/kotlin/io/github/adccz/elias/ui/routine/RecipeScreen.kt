package io.github.adccz.elias.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.domain.routine.DayRecipes
import io.github.adccz.elias.domain.routine.Recipe
import io.github.adccz.elias.domain.routine.RecipeMeat
import io.github.adccz.elias.ui.theme.EliasTheme
import java.time.LocalDate

@Composable
fun RecipeRoute(onBack: () -> Unit, viewModel: RecipeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecipeScreen(uiState = uiState, onBack = onBack)
}

/** Today's recipe: time, servings, ingredients and steps, then tomorrow's name for shopping. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(uiState: RecipeUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipe_title)) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val recipes = uiState.recipes
            when {
                !uiState.isLoaded -> item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }

                recipes == null -> item {
                    Text(stringResource(R.string.recipe_failed), color = MaterialTheme.colorScheme.error)
                }

                else -> recipe(recipes)
            }
        }
    }
}

private fun LazyListScope.recipe(recipes: DayRecipes) {
    val recipe = recipes.today
    item {
        Text(recipe.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            facts(recipe),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item {
        Text(
            stringResource(R.string.recipe_ingredients),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            recipe.ingredients.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
        }
    }
    item {
        Text(
            stringResource(R.string.recipe_steps),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    itemsIndexed(recipe.steps) { index, step ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${index + 1}.",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(step, style = MaterialTheme.typography.bodyLarge)
        }
    }
    item {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.recipe_tomorrow, recipes.tomorrow.name),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.recipe_tomorrow_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "25 min · 2 porce · bez masa". */
@Composable
private fun facts(recipe: Recipe): String = listOfNotNull(
    stringResource(R.string.recipe_minutes, recipe.minutes),
    pluralStringResource(R.plurals.recipe_servings, recipe.servings, recipe.servings),
    stringResource(R.string.recipe_meatless).takeIf { recipe.meat == RecipeMeat.NONE },
).joinToString(" · ")

@Preview(showBackground = true, locale = "cs")
@Composable
private fun RecipeScreenPreview() {
    val today = Recipe(
        id = "a",
        name = "Čočkový salát",
        minutes = 20,
        servings = 2,
        meat = RecipeMeat.NONE,
        ingredients = listOf("1 plechovka čočky", "2 rajčata", "sůl"),
        steps = listOf("Čočku sceď.", "Nakrájej rajčata.", "Promíchej a osol."),
    )
    EliasTheme {
        RecipeScreen(
            uiState = RecipeUiState(
                day = LocalDate.of(2026, 10, 29),
                isLoaded = true,
                recipes = DayRecipes(today, today.copy(id = "b", name = "Kuřecí kari s rýží")),
            ),
            onBack = {},
        )
    }
}
