package io.github.adccz.elias

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.adccz.elias.ui.navigation.EliasNavigation
import io.github.adccz.elias.ui.theme.EliasTheme

/** The app's main activity. Everything on screen is drawn by Compose. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val openJournal = intent?.getStringExtra(EXTRA_OPEN) == OPEN_JOURNAL
        setContent {
            EliasTheme {
                EliasNavigation(openJournal = openJournal)
            }
        }
    }

    companion object {
        private const val EXTRA_OPEN = "io.github.adccz.elias.OPEN"
        private const val OPEN_JOURNAL = "journal"

        /** Opens the app on the journal, with the home screen behind it. Used by the evening briefing screen. */
        fun journalIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN, OPEN_JOURNAL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
