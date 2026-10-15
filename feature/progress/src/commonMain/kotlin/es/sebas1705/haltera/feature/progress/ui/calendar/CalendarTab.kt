package es.sebas1705.ironlog.feature.progress.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.adaptive.LocalWindowSize
import es.sebas1705.ironlog.core.ui.adaptive.centeredContent
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.feature.progress.model.MonthKey
import es.sebas1705.ironlog.feature.progress.model.SessionSummary
import es.sebas1705.ironlog.feature.progress.resources.Res
import es.sebas1705.ironlog.feature.progress.resources.progress_add_session
import es.sebas1705.ironlog.feature.progress.resources.progress_exercise_count
import es.sebas1705.ironlog.feature.progress.resources.progress_free_session
import es.sebas1705.ironlog.feature.progress.resources.progress_in_progress
import es.sebas1705.ironlog.feature.progress.resources.progress_log_failed
import es.sebas1705.ironlog.feature.progress.resources.progress_month_title
import es.sebas1705.ironlog.feature.progress.resources.progress_months
import es.sebas1705.ironlog.feature.progress.resources.progress_next_month
import es.sebas1705.ironlog.feature.progress.resources.progress_no_sessions_day
import es.sebas1705.ironlog.feature.progress.resources.progress_previous_month
import es.sebas1705.ironlog.feature.progress.resources.progress_select_day
import es.sebas1705.ironlog.feature.progress.resources.progress_session_count
import es.sebas1705.ironlog.feature.progress.resources.progress_session_volume
import es.sebas1705.ironlog.feature.progress.resources.progress_weekdays_short
import es.sebas1705.ironlog.feature.progress.ui.components.LogSessionDialog
import es.sebas1705.ironlog.feature.progress.ui.components.formatDate
import es.sebas1705.ironlog.feature.progress.ui.components.formatDuration
import es.sebas1705.ironlog.feature.progress.ui.components.formatVolume
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val DAYS_PER_WEEK = 7

// Max width of each pane of the two-pane calendar on expanded windows.
private val CalendarPaneMaxWidth = 560.dp

// Cap on a day cell so six weeks fit in a landscape viewport.
private val DayCellMaxHeight = 72.dp

/** Calendar tab: the month grid and the sessions of the selected day. */
@Composable
internal fun CalendarTab(
    onOpenSession: (SessionSummary) -> Unit,
    onLogged: (sessionId: String) -> Unit,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val logged by rememberUpdatedState(onLogged)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CalendarEffect.OpenSession -> logged(effect.sessionId)
            }
        }
    }
    val month: @Composable () -> Unit = {
        MonthHeader(
            month = state.month,
            onPrevious = { viewModel.onEvent(CalendarIntent.PreviousMonth) },
            onNext = { viewModel.onEvent(CalendarIntent.NextMonth) },
        )
    }
    val grid: @Composable () -> Unit = {
        MonthGrid(
            month = state.month,
            today = state.today,
            selected = state.selectedDay,
            sessionsByDay = state.sessionsByDay,
            onSelect = { viewModel.onEvent(CalendarIntent.SelectDay(it)) },
        )
    }
    val dayItems: LazyListScope.() -> Unit = {
        item { DayHeader(state) }
        addSessionItems(state, onAdd = { viewModel.onEvent(CalendarIntent.OpenLogPast) })
        items(state.selectedSessions, key = { it.id }) { session ->
            SessionCard(session, state.unit, onClick = { onOpenSession(session) })
        }
    }
    CalendarLayout(month, grid, dayItems)
    state.logPast?.let { dialog ->
        LogSessionDialog(
            initial = dialog.initial,
            options = dialog.options,
            maxDateUtcMillis = dialog.maxDateUtcMillis,
            onConfirm = { viewModel.onEvent(CalendarIntent.ConfirmLogPast(it)) },
            onDismiss = { viewModel.onEvent(CalendarIntent.DismissLogPast) },
        )
    }
}

@Composable
private fun MonthHeader(month: MonthKey, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(Res.string.progress_previous_month)
            )
        }
        Text(
            text = stringResource(
                Res.string.progress_month_title,
                stringArrayResource(Res.array.progress_months)[month.month - 1],
                month.year.toString(),
            ),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.progress_next_month)
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: MonthKey,
    today: LocalDate,
    selected: LocalDate?,
    sessionsByDay: Map<LocalDate, List<SessionSummary>>,
    onSelect: (LocalDate) -> Unit,
) {
    val weekdays = stringArrayResource(Res.array.progress_weekdays_short)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val cells = List(month.leadingBlanks) { null } + (1..month.length).map { month.day(it) }
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(
                        Modifier.weight(1f).heightIn(max = DayCellMaxHeight).aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                sessions = sessionsByDay[date]?.size ?: 0,
                                isToday = date == today,
                                isSelected = date == selected,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
                repeat(DAYS_PER_WEEK - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, sessions: Int, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val trained = sessions > 0
    val background = when {
        isSelected -> colors.primary
        trained -> colors.primaryContainer
        else -> colors.surface
    }
    val content = when {
        isSelected -> colors.onPrimary
        trained -> colors.onPrimaryContainer
        else -> colors.onSurface
    }
    val sessionCount = if (trained) pluralStringResource(Res.plurals.progress_session_count, sessions, sessions) else ""
    var cell = Modifier
        .padding(3.dp)
        .fillMaxSize()
        .clip(CircleShape)
        .background(background)
    if (isToday && !isSelected) cell = cell.border(2.dp, colors.primary, CircleShape)
    Box(
        modifier = cell
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (trained) stateDescription = sessionCount },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.day.toString(),
            color = content,
            fontWeight = if (trained || isToday) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (trained) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(content),
            )
        }
    }
}

@Composable
private fun DayHeader(state: CalendarState) {
    val day = state.selectedDay
    val text = when {
        day == null -> stringResource(Res.string.progress_select_day)
        state.loading -> formatDate(day, withYear = true)
        state.selectedSessions.isEmpty() -> "${formatDate(
            day,
            withYear = true
        )} · ${stringResource(Res.string.progress_no_sessions_day)}"
        else -> formatDate(day, withYear = true)
    }
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SessionCard(session: SessionSummary, unit: WeightUnit, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val title = listOfNotNull(session.routineName, session.dayName).joinToString(" · ")
                .ifEmpty { stringResource(Res.string.progress_free_session) }
            Text(title, style = MaterialTheme.typography.titleMedium)
            val details = buildList {
                session.durationMillis?.let { add(formatDuration(it)) }
                add(
                    pluralStringResource(
                        Res.plurals.progress_exercise_count,
                        session.exerciseCount,
                        session.exerciseCount
                    )
                )
                add(stringResource(Res.string.progress_session_volume, formatVolume(session.volumeGrams, unit)))
            }
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session.inProgress) {
                AssistChip(onClick = onClick, label = { Text(stringResource(Res.string.progress_in_progress)) })
            }
        }
    }
}

/** "Add session on this day" for today or any past day, and the error of the last attempt. */
private fun LazyListScope.addSessionItems(state: CalendarState, onAdd: () -> Unit) {
    if (state.logFailed) {
        item {
            Text(
                stringResource(Res.string.progress_log_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    if (state.canAddOnSelectedDay) {
        item {
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.progress_add_session))
            }
        }
    }
}

/** The month above the day's sessions; side by side on wide windows (N2) so the day stays in view. */
@Composable
private fun CalendarLayout(
    month: @Composable () -> Unit,
    grid: @Composable () -> Unit,
    dayItems: LazyListScope.() -> Unit,
) {
    if (LocalWindowSize.current.isExpanded) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).widthIn(max = CalendarPaneMaxWidth),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { month() }
                item { grid() }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).widthIn(max = CalendarPaneMaxWidth),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = dayItems,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().centeredContent(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { month() }
            item { grid() }
            dayItems()
        }
    }
}
