package io.github.adccz.elias

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point. [HiltAndroidApp] creates the app-wide dependency container. */
@HiltAndroidApp
class EliasApp : Application()
