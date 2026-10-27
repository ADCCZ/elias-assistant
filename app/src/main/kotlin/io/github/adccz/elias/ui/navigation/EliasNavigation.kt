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
import kotlinx.serialization.Serializable

// Screens are identified by keys. They are @Serializable so the back stack survives process death.

@Serializable
data object HomeKey : NavKey

@Serializable
data object DataKey : NavKey

/**
 * The app's navigation with Navigation 3. The back stack is a plain list we own:
 * opening a screen adds its key, going back removes the last one.
 */
@Composable
fun EliasNavigation() {
    val backStack = rememberNavBackStack(HomeKey)
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
            entry<HomeKey> { HomeRoute(onOpenData = { backStack.add(DataKey) }) }
            entry<DataKey> { DataRoute(onBack = { backStack.removeLastOrNull() }) }
        },
    )
}
