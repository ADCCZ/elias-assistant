package io.github.adccz.elias.core.coroutines

import javax.inject.Qualifier

/**
 * Marks the coroutine scope that lives as long as the app process. Work that must not stop with a screen, such as
 * planning the next alarms, runs in it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
