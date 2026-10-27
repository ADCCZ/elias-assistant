package io.github.adccz.elias.ui.data

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.adccz.elias.R
import io.github.adccz.elias.ui.common.CzechFormat
import io.github.adccz.elias.ui.theme.EliasTheme

/** Connects the Data screen to its ViewModel and to Android (permission dialog, system settings). */
@Composable
fun DataRoute(onBack: () -> Unit, viewModel: DataViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }
    // Coming back from system settings: reload if calendar access was missing.
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        onPauseOrDispose { }
    }

    DataScreen(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRequestCalendarPermission = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
        onOpenAppSettings = {
            val intent =
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            context.startActivity(intent)
        },
    )
}

/** Everything Elias would know today, one card per source. Pull down to reload. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    uiState: DataUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRequestCalendarPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.data_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onRefresh, enabled = uiState is DataUiState.Loaded && !uiState.isRefreshing) {
                        Text(stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { innerPadding ->
        when (uiState) {
            DataUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is DataUiState.Loaded -> PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                val overview = uiState.overview
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(
                                R.string.data_loaded_for,
                                overview.loadedAt.format(CzechFormat.fullDate),
                                overview.loadedAt.format(CzechFormat.time),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item { DaySection(overview) }
                    item { CalendarSection(overview, onRequestCalendarPermission, onOpenAppSettings) }
                    item { WeatherSection(overview) }
                    item { PlanSection(overview) }
                    item { NewsSection(overview) }
                }
            }
        }
    }
}

@Preview(showBackground = true, locale = "cs", heightDp = 1600)
@Composable
private fun DataScreenPreview() {
    EliasTheme {
        DataScreen(
            uiState = DataUiState.Loaded(DataPreviewData.overview(), isRefreshing = false),
            onBack = {},
            onRefresh = {},
            onRequestCalendarPermission = {},
            onOpenAppSettings = {},
        )
    }
}
