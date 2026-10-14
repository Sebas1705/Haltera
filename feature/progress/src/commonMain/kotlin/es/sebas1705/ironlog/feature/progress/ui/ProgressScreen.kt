package es.sebas1705.ironlog.feature.progress.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.feature.progress.model.SessionSummary
import es.sebas1705.ironlog.feature.progress.resources.Res
import es.sebas1705.ironlog.feature.progress.resources.progress_tab_body_weight
import es.sebas1705.ironlog.feature.progress.resources.progress_tab_calendar
import es.sebas1705.ironlog.feature.progress.resources.progress_tab_exercises
import es.sebas1705.ironlog.feature.progress.resources.progress_tab_volume
import es.sebas1705.ironlog.feature.progress.ui.bodyweight.BodyWeightTab
import es.sebas1705.ironlog.feature.progress.ui.calendar.CalendarTab
import es.sebas1705.ironlog.feature.progress.ui.exercise.ExerciseProgressTab
import es.sebas1705.ironlog.feature.progress.ui.volume.MuscleVolumeTab
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class ProgressTab(val label: StringResource) {
    CALENDAR(Res.string.progress_tab_calendar),
    EXERCISES(Res.string.progress_tab_exercises),
    BODY_WEIGHT(Res.string.progress_tab_body_weight),
    VOLUME(Res.string.progress_tab_volume),
}

/** Root of the Progress tab (the shell draws the top bar): calendar, exercises, body weight and volume. */
@Composable
internal fun ProgressScreen(
    onOpenSession: (SessionSummary) -> Unit,
    onOpenSessionId: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
            ProgressTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = index == selected,
                    onClick = { selected = index },
                    text = { Text(stringResource(tab.label)) },
                )
            }
        }
        when (ProgressTab.entries[selected]) {
            ProgressTab.CALENDAR -> CalendarTab(onOpenSession, onLogged = onOpenSessionId)
            ProgressTab.EXERCISES -> ExerciseProgressTab()
            ProgressTab.BODY_WEIGHT -> BodyWeightTab()
            ProgressTab.VOLUME -> MuscleVolumeTab()
        }
    }
}
