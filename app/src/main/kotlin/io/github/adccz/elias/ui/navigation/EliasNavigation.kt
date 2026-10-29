package io.github.adccz.elias.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.adccz.elias.ui.data.DataRoute
import io.github.adccz.elias.ui.home.HomeRoute
import io.github.adccz.elias.ui.journal.JournalRoute
import io.github.adccz.elias.ui.plans.PlansRoute
import io.github.adccz.elias.ui.preview.PreviewRoute
import io.github.adccz.elias.ui.schedule.ScheduleRoute
import io.github.adccz.elias.ui.status.StatusRoute
import kotlinx.serialization.Serializable

// Screens are identified by keys. They are @Serializable so the back stack survives process death.

@Serializable
data object HomeKey : NavKey

@Serializable
data object DataKey : NavKey

@Serializable
data object PreviewKey : NavKey

@Serializable
data object ScheduleKey : NavKey

@Serializable
data object StatusKey : NavKey

@Serializable
data object PlansKey : NavKey

@Serializable
data object JournalKey : NavKey

/**
 * The app's navigation with Navigation 3. The back stack is a plain list we own:
 * opening a screen adds its key, going back removes the last one.
 * With [openJournal] the app starts on the journal, and going back leads home.
 */
@Composable
fun EliasNavigation(openJournal: Boolean = false) {
    val backStack = if (openJournal) rememberNavBackStack(HomeKey, JournalKey) else rememberNavBackStack(HomeKey)
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            // Keeps each screen's saved UI state (scroll position...) while it is on the back stack.
            rememberSaveableStateHolderNavEntryDecorator(),
            // Gives each screen its own ViewModels, cleared when the screen leaves the back stack.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeKey> {
                HomeRoute(
                    onOpenData = { backStack.add(DataKey) },
                    onOpenPreview = { backStack.add(PreviewKey) },
                    onOpenSchedule = { backStack.add(ScheduleKey) },
                    onOpenStatus = { backStack.add(StatusKey) },
                    onOpenPlans = { backStack.add(PlansKey) },
                    onOpenJournal = { backStack.add(JournalKey) },
                )
            }
            entry<DataKey> { DataRoute(onBack = { backStack.removeLastOrNull() }) }
            entry<PreviewKey> { PreviewRoute(onBack = { backStack.removeLastOrNull() }) }
            entry<ScheduleKey> { ScheduleRoute(onBack = { backStack.removeLastOrNull() }) }
            entry<StatusKey> { StatusRoute(onBack = { backStack.removeLastOrNull() }) }
            entry<PlansKey> { PlansRoute(onBack = { backStack.removeLastOrNull() }) }
            entry<JournalKey> { JournalRoute(onBack = { backStack.removeLastOrNull() }) }
        },
    )
}
