package io.github.adccz.elias.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.ui.theme.EliasTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Connects the screen to its ViewModel. Kept apart so [HomeScreen] stays easy to preview. */
@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(uiState = uiState)
}

/** Stateless home screen: it only draws [uiState]. */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.home_today, uiState.today.format(czechDateFormatter)),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/** For example "úterý 27. října 2026". */
private val czechDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d. MMMM yyyy", Locale.forLanguageTag("cs-CZ"))

@Preview(showBackground = true, locale = "cs")
@Composable
private fun HomeScreenPreview() {
    EliasTheme {
        HomeScreen(uiState = HomeUiState(today = LocalDate.of(2026, 10, 27)))
    }
}
