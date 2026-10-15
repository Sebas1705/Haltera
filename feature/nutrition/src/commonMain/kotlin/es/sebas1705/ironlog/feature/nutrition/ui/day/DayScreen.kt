package es.sebas1705.ironlog.feature.nutrition.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.data.models.Meal
import es.sebas1705.ironlog.data.models.MealEntry
import es.sebas1705.ironlog.feature.nutrition.resources.Res
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_add_food
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_cancel
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_carbs
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_copied
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_copy_day
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_copy_from_title
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_copy_meal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_copy_meal_title
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_day_info
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_day_next
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_day_pick
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_day_previous
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_day_summary
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_error
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_fat
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_goal_of
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_goals
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_meal_empty
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_meal_more
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_no_goal
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_ok
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_over
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_protein
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_remaining
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_set_goals
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_stats
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_today
import es.sebas1705.ironlog.feature.nutrition.resources.nutrition_unknown_food
import es.sebas1705.ironlog.feature.nutrition.ui.components.EnergyRing
import es.sebas1705.ironlog.feature.nutrition.ui.components.InfoButton
import es.sebas1705.ironlog.feature.nutrition.ui.components.MacroBar
import es.sebas1705.ironlog.feature.nutrition.ui.components.MacroColors
import es.sebas1705.ironlog.feature.nutrition.ui.components.amountText
import es.sebas1705.ironlog.feature.nutrition.ui.components.displayName
import es.sebas1705.ironlog.feature.nutrition.ui.components.formatDate
import es.sebas1705.ironlog.feature.nutrition.ui.components.kcalText
import es.sebas1705.ironlog.feature.nutrition.ui.components.label
import es.sebas1705.ironlog.data.models.FoodUnit
import es.sebas1705.ironlog.feature.nutrition.model.Amounts
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Width from which the day shows its summary and its meals side by side. */
private val TWO_COLUMNS = 720.dp

/** Nutrition tab root: date switcher, totals against the goals and the four meals. The shell draws the top bar. */
@Composable
internal fun DayScreen(
    onAddFood: (LocalDate, Meal) -> Unit,
    onOpenEntry: (MealEntry) -> Unit,
    onOpenGoals: () -> Unit,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DayViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val onEvent: (DayIntent) -> Unit = { viewModel.onEvent(it) }
    val snackbar = remember { SnackbarHostState() }
    val copiedText = state.copiedCount?.let { stringResource(Res.string.nutrition_copied, it) }
    val errorText = stringResource(Res.string.nutrition_error)
    LaunchedEffect(copiedText) {
        if (copiedText != null) {
            snackbar.showSnackbar(copiedText)
            onEvent(DayIntent.CopiedShown)
        }
    }
    LaunchedEffect(state.error) {
        if (state.error) {
            snackbar.showSnackbar(errorText)
            onEvent(DayIntent.ErrorShown)
        }
    }

    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= TWO_COLUMNS
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                DateBar(state, onEvent)
                Spacer(Modifier.padding(4.dp))
                ActionsRow(onOpenGoals, onOpenStats, onCopyDay = { onEvent(DayIntent.StartCopy(CopyTarget.WholeDay)) })
                Spacer(Modifier.padding(4.dp))
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) { SummaryCard(state, onOpenGoals) }
                        Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Meals(state, onEvent, onAddFood, onOpenEntry)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryCard(state, onOpenGoals)
                        Meals(state, onEvent, onAddFood, onOpenEntry)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    state.copying?.let { target -> CopyFromDialog(state, target, onEvent) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
private fun DateBar(state: DayState, onEvent: (DayIntent) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onEvent(DayIntent.PreviousDay) }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(Res.string.nutrition_day_previous)
            )
        }
        TextButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
            Text(
                if (state.date == state.today) stringResource(Res.string.nutrition_today) else formatDate(state.date),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { onEvent(DayIntent.NextDay) }) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(Res.string.nutrition_day_next)
            )
        }
    }
    if (state.date != state.today) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            AssistChip(
                onClick = { onEvent(DayIntent.GoToToday) },
                label = { Text(stringResource(Res.string.nutrition_today)) }
            )
        }
    }
    if (picking) {
        DatePickerFor(
            title = stringResource(Res.string.nutrition_day_pick),
            initial = state.date,
            onPick = {
                picking = false
                onEvent(DayIntent.SetDate(it))
            },
            onDismiss = { picking = false },
        )
    }
}

/** Material date picker on a [LocalDate] (the picker works in UTC midnight millis). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
internal fun DatePickerFor(title: String, initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = pickerState.selectedDateMillis
                if (millis == null) {
                    onDismiss()
                } else {
                    onPick(
                        Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                    )
                }
            }) { Text(stringResource(Res.string.nutrition_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.nutrition_cancel)) } },
    ) {
        DatePicker(
            state = pickerState,
            title = { Text(title, modifier = Modifier.padding(start = 24.dp, top = 16.dp)) }
        )
    }
}

@Composable
private fun ActionsRow(onOpenGoals: () -> Unit, onOpenStats: () -> Unit, onCopyDay: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onOpenGoals,
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(Res.string.nutrition_goals), maxLines = 1) }
        OutlinedButton(
            onClick = onOpenStats,
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(Res.string.nutrition_stats), maxLines = 1) }
        OutlinedButton(
            onClick = onCopyDay,
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(Res.string.nutrition_copy_day), maxLines = 1) }
    }
}

@Composable
private fun SummaryCard(state: DayState, onOpenGoals: () -> Unit) {
    val totals = state.day.totals
    val goals = state.goals
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.nutrition_day_summary),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                InfoButton(
                    stringResource(Res.string.nutrition_day_summary),
                    stringResource(Res.string.nutrition_day_info)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                EnergyRing(totals.kcal, goals.kcal) {
                    Text(
                        Amounts.format(totals.kcal, 0),
                        style = MaterialTheme.typography.headlineMedium.tabularFigures()
                    )
                    Text(
                        goals.kcal?.let { stringResource(Res.string.nutrition_goal_of, it) }
                            ?: stringResource(Res.string.nutrition_no_goal),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    val goal = goals.kcal
                    if (goal != null) {
                        val left = goal - totals.kcal
                        Text(
                            if (left >= 0) {
                                stringResource(
                                    Res.string.nutrition_remaining
                                )
                            } else {
                                stringResource(Res.string.nutrition_over)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(kcalText(abs(left)), style = MaterialTheme.typography.titleLarge.tabularFigures())
                    } else {
                        TextButton(onClick = onOpenGoals) { Text(stringResource(Res.string.nutrition_set_goals)) }
                    }
                }
            }
            MacroBar(stringResource(Res.string.nutrition_protein), totals.protein, goals.proteinG, MacroColors.protein)
            MacroBar(stringResource(Res.string.nutrition_carbs), totals.carbs, goals.carbsG, MacroColors.carbs)
            MacroBar(stringResource(Res.string.nutrition_fat), totals.fat, goals.fatG, MacroColors.fat)
        }
    }
}

@Composable
private fun Meals(
    state: DayState,
    onEvent: (DayIntent) -> Unit,
    onAddFood: (LocalDate, Meal) -> Unit,
    onOpenEntry: (MealEntry) -> Unit,
) {
    Meal.entries.forEach { meal ->
        MealCard(
            meal = meal,
            entries = state.day.meal(meal),
            kcal = state.day.mealTotals(meal).kcal,
            onAdd = { onAddFood(state.date, meal) },
            onOpen = onOpenEntry,
            onCopyFrom = { onEvent(DayIntent.StartCopy(CopyTarget.OneMeal(meal))) },
        )
    }
}

@Composable
private fun MealCard(
    meal: Meal,
    entries: List<MealEntry>,
    kcal: Double,
    onAdd: () -> Unit,
    onOpen: (MealEntry) -> Unit,
    onCopyFrom: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(meal.label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    kcalText(kcal),
                    style = MaterialTheme.typography.titleSmall.tabularFigures(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(
                                Res.string.nutrition_meal_more,
                                stringResource(meal.label)
                            )
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.nutrition_copy_meal)) },
                            onClick = {
                                menu = false
                                onCopyFrom()
                            }
                        )
                    }
                }
            }
            if (entries.isEmpty()) {
                Text(
                    stringResource(Res.string.nutrition_meal_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                EntryRow(entry, onClick = { onOpen(entry) })
            }
            TextButton(onClick = onAdd, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.nutrition_add_food))
            }
        }
    }
}

@Composable
private fun EntryRow(entry: MealEntry, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.food?.displayName() ?: stringResource(Res.string.nutrition_unknown_food),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                amountText(entry.grams, entry.food?.unit ?: FoodUnit.G),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(kcalText(entry.nutrients.kcal), style = MaterialTheme.typography.bodyMedium.tabularFigures())
    }
}

@Composable
private fun CopyFromDialog(state: DayState, target: CopyTarget, onEvent: (DayIntent) -> Unit) {
    val title = when (target) {
        CopyTarget.WholeDay -> stringResource(Res.string.nutrition_copy_from_title)
        is CopyTarget.OneMeal -> stringResource(Res.string.nutrition_copy_meal_title, stringResource(target.meal.label))
    }
    DatePickerFor(
        title = title,
        initial = state.date.minus(DatePeriod(days = 1)),
        onPick = { onEvent(DayIntent.CopyFrom(it)) },
        onDismiss = { onEvent(DayIntent.CancelCopy) },
    )
}
