package es.sebas1705.haltera.share

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A routine opened from outside the app (a `.haltera` file or the share sheet) waiting for the
 * Routines tab to show its import preview. The platform entry points [offer] it; the routines
 * feature reads it through `RoutineShareBridge.incoming`.
 */
class IncomingRoutineChannel {
    private val pending = MutableStateFlow<String?>(null)
    val value: StateFlow<String?> = pending.asStateFlow()

    fun offer(text: String) {
        pending.value = text
    }

    fun consume() {
        pending.value = null
    }

    companion object {
        /** Larger files are not routines; they are ignored instead of being read into memory. */
        const val MAX_BYTES = 1_000_000
    }
}
