package io.github.adccz.elias.core.coroutines

import javax.inject.Qualifier

/**
 * Marks the dispatcher for blocking work such as reading files or querying the calendar.
 * It is injected rather than hard-coded, so tests can replace it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
