package es.sebas1705.ironlog.feature.session.platform

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import es.sebas1705.ironlog.feature.session.resources.Res
import es.sebas1705.ironlog.feature.session.resources.session_exact_alarm_not_now
import es.sebas1705.ironlog.feature.session.resources.session_exact_alarm_open
import es.sebas1705.ironlog.feature.session.resources.session_exact_alarm_text
import es.sebas1705.ironlog.feature.session.resources.session_exact_alarm_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** What the platform says about exact alarms; Android 12+ may deny them by default, iOS has no such thing. */
interface ExactAlarmAccess {
    /** True only where the platform asks for a grant that is currently missing. */
    fun isMissing(): Boolean

    /** Opens the system screen where the user can grant it. */
    fun openSettings()
}

/** The remembered answer: once true the explanation never comes back. */
interface ExactAlarmPromptStore {
    var handled: Boolean
}

/**
 * The one-time, dismissible explanation for the exact alarm permission (the rest timer works without it,
 * only less punctually when the phone sleeps). Never nags: it is shown at most once in the app's life.
 */
class ExactAlarmPrompt(private val access: ExactAlarmAccess, private val store: ExactAlarmPromptStore) {

    /** True when the explanation should be shown now. Already granted (or not applicable) counts as handled. */
    fun shouldShow(): Boolean {
        if (store.handled) return false
        if (!access.isMissing()) {
            store.handled = true
            return false
        }
        return true
    }

    /** "Not now": remembered, never asked again. */
    fun notNow() {
        store.handled = true
    }

    /** Opens the system screen; counts as the one time, whatever the user decides there. */
    fun openSettings() {
        store.handled = true
        access.openSettings()
    }
}

/** Where there is no exact alarm permission (iOS): nothing to ask. */
internal object NoExactAlarmAccess : ExactAlarmAccess {
    override fun isMissing(): Boolean = false

    override fun openSettings() = Unit
}

internal class InMemoryExactAlarmPromptStore : ExactAlarmPromptStore {
    override var handled: Boolean = false
}

/** Dialog shown over a live session, at most once ever. */
@Composable
internal fun ExactAlarmExplanation(prompt: ExactAlarmPrompt = koinInject()) {
    var visible by remember { mutableStateOf(prompt.shouldShow()) }
    if (!visible) return
    AlertDialog(
        onDismissRequest = {
            prompt.notNow()
            visible = false
        },
        title = { Text(stringResource(Res.string.session_exact_alarm_title)) },
        text = { Text(stringResource(Res.string.session_exact_alarm_text)) },
        confirmButton = {
            TextButton(onClick = {
                prompt.openSettings()
                visible = false
            }) { Text(stringResource(Res.string.session_exact_alarm_open)) }
        },
        dismissButton = {
            TextButton(onClick = {
                prompt.notNow()
                visible = false
            }) { Text(stringResource(Res.string.session_exact_alarm_not_now)) }
        },
    )
}
