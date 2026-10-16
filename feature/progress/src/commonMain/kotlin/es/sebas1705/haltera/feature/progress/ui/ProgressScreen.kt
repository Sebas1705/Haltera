package es.sebas1705.haltera.feature.progress.ui

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
import es.sebas1705.haltera.feature.progress.model.SessionSummary
import es.sebas1705.haltera.feature.progress.resources.Res
import es.sebas1705.haltera.feature.progress.resources.progress_tab_activity
import es.sebas1705.haltera.feature.progress.resources.progress_tab_body_weight
import es.sebas1705.haltera.feature.progress.resources.progress_tab_calendar
import es.sebas1705.haltera.feature.progress.resources.progress_tab_exercises
import es.sebas1705.haltera.feature.progress.resources.progress_tab_muscles
import es.sebas1705.haltera.feature.progress.resources.progress_tab_overview
import es.sebas1705.haltera.feature.progress.ui.activity.ActivityTab
import es.sebas1705.haltera.feature.progress.ui.bodyweight.BodyWeightTab
import es.sebas1705.haltera.feature.progress.ui.calendar.CalendarTab
import es.sebas1705.haltera.feature.progress.ui.exercise.ExerciseProgressTab
import es.sebas1705.haltera.feature.progress.ui.muscles.MusclesTab
import es.sebas1705.haltera.feature.progress.ui.overview.OverviewTab
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class ProgressTab(val label: StringResource) {
    OVERVIEW(Res.string.progress_tab_overview),
    CALENDAR(Res.string.progress_tab_calendar),
    EXERCISES(Res.string.progress_tab_exercises),
    MUSCLES(Res.string.progress_tab_muscles),
    BODY_WEIGHT(Res.string.progress_tab_body_weight),
    ACTIVITY(Res.string.progress_tab_activity),
}

/** Root of the Progress tab (the shell draws the top bar): overview, calendar, exercises, muscles, body weight and
 * activity (Health Connect). */
@Composable
internal fun ProgressScreen(
    onOpenSession: (SessionSummary) -> Unit,
    onOpenSessionId: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenExerciseDetail: (exerciseId: String) -> Unit = {},
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
            ProgressTab.OVERVIEW -> OverviewTab()
            ProgressTab.CALENDAR -> CalendarTab(onOpenSession, onLogged = onOpenSessionId)
            ProgressTab.EXERCISES -> ExerciseProgressTab(onOpenExerciseDetail)
            ProgressTab.MUSCLES -> MusclesTab()
            ProgressTab.BODY_WEIGHT -> BodyWeightTab()
            ProgressTab.ACTIVITY -> ActivityTab()
        }
    }
}
