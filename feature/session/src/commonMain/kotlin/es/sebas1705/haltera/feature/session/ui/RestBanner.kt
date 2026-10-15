package es.sebas1705.ironlog.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.ui.theme.IronlogTheme
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.feature.session.resources.Res
import es.sebas1705.ironlog.feature.session.resources.session_get_ready
import es.sebas1705.ironlog.feature.session.resources.session_rest
import es.sebas1705.ironlog.feature.session.resources.session_rest_minus
import es.sebas1705.ironlog.feature.session.resources.session_rest_plus
import es.sebas1705.ironlog.feature.session.resources.session_skip
import es.sebas1705.ironlog.feature.session.resources.session_stop
import es.sebas1705.ironlog.feature.session.resources.session_work
import es.sebas1705.ironlog.feature.session.timer.TimerMath
import es.sebas1705.ironlog.feature.session.timer.TimerPhase
import es.sebas1705.ironlog.feature.session.timer.TimerSnapshot
import org.jetbrains.compose.resources.stringResource

private const val REST_STEP_S = 15

/** Rest / timed-set banner rendered from absolute instants: `endsAt − now`. Hidden when idle. */
@Composable
internal fun RestBanner(
    timer: TimerSnapshot,
    now: Long,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = TimerMath.phase(timer, now)
    if (phase == TimerPhase.IDLE) return
    val remaining = TimerMath.formatSeconds(TimerMath.remainingSeconds(timer, now))
    // Brand accent: iron orange with graphite content, in light and dark.
    val accent = IronlogTheme.colors.accent
    val onAccent = IronlogTheme.colors.onAccent
    val tonal = ButtonDefaults.filledTonalButtonColors(
        containerColor = onAccent.copy(alpha = 0.14f),
        contentColor = onAccent
    )
    val solid = ButtonDefaults.buttonColors(containerColor = onAccent, contentColor = accent)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = accent,
        contentColor = onAccent,
        tonalElevation = 3.dp
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (phase == TimerPhase.REST) {
                LinearProgressIndicator(
                    progress = { TimerMath.restFraction(timer, now) },
                    modifier = Modifier.fillMaxWidth(),
                    color = onAccent,
                    trackColor = onAccent.copy(alpha = 0.2f),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                    val title = when (phase) {
                        TimerPhase.COUNTDOWN -> Res.string.session_get_ready
                        TimerPhase.WORK -> Res.string.session_work
                        else -> Res.string.session_rest
                    }
                    Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
                    Text(remaining, style = MaterialTheme.typography.headlineMedium.tabularFigures())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (phase) {
                        TimerPhase.REST -> {
                            FilledTonalButton(
                                onClick = { onAdjust(-REST_STEP_S) },
                                colors = tonal,
                            ) { Text(stringResource(Res.string.session_rest_minus)) }
                            FilledTonalButton(
                                onClick = { onAdjust(REST_STEP_S) },
                                colors = tonal,
                            ) { Text(stringResource(Res.string.session_rest_plus)) }
                            Button(onClick = onSkip, colors = solid) { Text(stringResource(Res.string.session_skip)) }
                        }
                        else -> Button(
                            onClick = onStop,
                            colors = solid
                        ) { Text(stringResource(Res.string.session_stop)) }
                    }
                }
            }
        }
    }
}
