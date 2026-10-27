package io.github.adccz.elias

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.adccz.elias.ui.navigation.EliasNavigation
import io.github.adccz.elias.ui.theme.EliasTheme

/** The only activity. Everything on screen is drawn by Compose. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            EliasTheme {
                EliasNavigation()
            }
        }
    }
}
