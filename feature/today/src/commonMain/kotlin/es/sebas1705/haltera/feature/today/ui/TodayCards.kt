package es.sebas1705.haltera.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.core.units.Weights
import es.sebas1705.haltera.data.models.BodyWeightEntry
import es.sebas1705.haltera.feature.today.resources.Res
import es.sebas1705.haltera.feature.today.resources.today_body_weight_none
import es.sebas1705.haltera.feature.today.resources.today_body_weight_title
import es.sebas1705.haltera.feature.today.resources.today_day_of
import es.sebas1705.haltera.feature.today.resources.today_flexible
import es.sebas1705.haltera.feature.today.resources.today_free_session
import es.sebas1705.haltera.feature.today.resources.today_free_session_name
import es.sebas1705.haltera.feature.today.resources.today_log_weight
import es.sebas1705.haltera.feature.today.resources.today_no_exercises
import es.sebas1705.haltera.feature.today.resources.today_no_routine_message
import es.sebas1705.haltera.feature.today.resources.today_no_routine_title
import es.sebas1705.haltera.feature.today.resources.today_no_training_days
import es.sebas1705.haltera.feature.today.resources.today_rest_hint
import es.sebas1705.haltera.feature.today.resources.today_resume_action
import es.sebas1705.haltera.feature.today.resources.today_resume_elapsed
import es.sebas1705.haltera.feature.today.resources.today_resume_title
import es.sebas1705.haltera.feature.today.resources.today_start
import es.sebas1705.haltera.feature.today.resources.today_train_other_day
import es.sebas1705.haltera.feature.today.resources.today_trained
import es.sebas1705.haltera.feature.today.resources.today_unknown_exercise
import es.sebas1705.haltera.feature.today.resources.today_week_sessions
import es.sebas1705.haltera.feature.today.resources.today_week_title
import es.sebas1705.haltera.feature.today.resources.today_week_sets
import es.sebas1705.haltera.feature.today.resources.today_weight_value
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val ELAPSED_TICK_MS = 1_000L

@OptIn(ExperimentalTime::class)
@Composable
internal fun ResumeCard(session: InProgressUi, onResume: () -> Unit, modifier: Modifier = Modifier) {
    val now by produceState(Clock.System.now().toEpochMilliseconds(), session.startedAt) {
        while (true) {
            value = Clock.System.now().toEpochMilliseconds()
            delay(ELAPSED_TICK_MS)
        }
    }
    Card(
        onClick = onResume,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.today_resume_title), style = MaterialTheme.typography.labelLarge)
            Text(
                session.dayName ?: stringResource(Res.string.today_free_session_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(Res.string.today_resume_elapsed, formatElapsed(now - session.startedAt)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.today_resume_action))
            }
        }
    }
}

@Composable
internal fun PlanCard(
    plan: PlanUi,
    startEnabled: Boolean,
    onIntent: (TodayIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                plan.routineName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            val next = plan.next
            if (next == null) {
                Text(stringResource(Res.string.today_no_training_days), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    stringResource(Res.string.today_day_of, next.dayIndex + 1, next.dayCount, next.dayName),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (next.planSaysRest) RestHint()
                // The primary action comes before the exercise list so it stays above the fold on
                // small phones with long days.
                Button(
                    onClick = { onIntent(TodayIntent.StartNextDay) },
                    enabled = startEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.today_start))
                }
                if (next.exercises.isEmpty()) {
                    Text(stringResource(Res.string.today_no_exercises), style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column {
                        next.exercises.forEachIndexed { index, slot ->
                            if (index > 0) HorizontalDivider()
                            SlotRow(slot)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (plan.days.any { !it.isRest }) {
                    TextButton(onClick = { onIntent(TodayIntent.OpenDayPicker) }) {
                        Icon(Icons.Filled.DateRange, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.today_train_other_day))
                    }
                }
                TextButton(onClick = { onIntent(TodayIntent.StartFree) }, enabled = startEnabled) {
                    Text(stringResource(Res.string.today_free_session))
                }
            }
        }
    }
}

@Composable
private fun RestHint() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(Res.string.today_rest_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SlotRow(slot: SlotUi) {
    val language = currentLanguage()
    val detail = when {
        slot.flexible -> stringResource(Res.string.today_flexible)
        else -> slot.variantName?.get(language) ?: slot.equipment?.let { equipmentLabel(it) }?.let { stringResource(it) }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                slot.name?.get(language) ?: stringResource(Res.string.today_unknown_exercise),
                style = MaterialTheme.typography.bodyLarge,
            )
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(prescriptionText(slot.sets, slot.prescription), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
internal fun NoRoutineCard(startEnabled: Boolean, onFreeSession: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.today_no_routine_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(Res.string.today_no_routine_message), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onFreeSession, enabled = startEnabled, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.today_free_session))
            }
        }
    }
}

@Composable
internal fun WeekCard(week: WeekUi, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.today_week_title), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.days.forEach { day -> WeekDayCell(day) }
            }
            val sessions = pluralStringResource(Res.plurals.today_week_sessions, week.sessionCount, week.sessionCount)
            val sets = pluralStringResource(Res.plurals.today_week_sets, week.setCount, week.setCount)
            Text("$sessions · $sets", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun WeekDayCell(day: WeekDayUi) {
    val trained = stringResource(Res.string.today_trained)
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = if (day.trained) Modifier.semantics { contentDescription = trained } else Modifier,
    ) {
        Text(
            stringResource(weekdayLabel(day.isoDayOfWeek)),
            style = MaterialTheme.typography.labelMedium,
            color = if (day.isToday) colors.primary else colors.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (day.trained) colors.primary else colors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (day.trained) colors.onPrimary else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun BodyWeightCard(
    latest: BodyWeightEntry?,
    unit: WeightUnit,
    onLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.today_body_weight_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    latest?.let {
                        stringResource(Res.string.today_weight_value, Weights.format(it.weightGrams, unit, decimals = 1), unitLabel(unit))
                    } ?: stringResource(Res.string.today_body_weight_none),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            OutlinedButton(onClick = onLog) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.today_log_weight))
            }
        }
    }
}
