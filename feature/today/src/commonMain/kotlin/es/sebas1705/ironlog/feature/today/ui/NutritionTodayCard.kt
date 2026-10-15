package es.sebas1705.ironlog.feature.today.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.common.mvi.MVIBaseIntent
import es.sebas1705.ironlog.core.common.mvi.MVIBaseState
import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.ui.theme.IronlogTheme
import es.sebas1705.ironlog.core.ui.theme.tabularFigures
import es.sebas1705.ironlog.core.units.Numbers
import es.sebas1705.ironlog.data.models.Nutrients
import es.sebas1705.ironlog.data.models.NutritionGoals
import es.sebas1705.ironlog.data.repository.NutritionRepository
import es.sebas1705.ironlog.feature.today.resources.Res
import es.sebas1705.ironlog.feature.today.resources.today_nutrition_carbs
import es.sebas1705.ironlog.feature.today.resources.today_nutrition_fat
import es.sebas1705.ironlog.feature.today.resources.today_nutrition_grams
import es.sebas1705.ironlog.feature.today.resources.today_nutrition_kcal
import es.sebas1705.ironlog.feature.today.resources.today_nutrition_kcal_of
import es.sebas1705.ironlog.feature.today.resources.today_nutrition_protein
import es.sebas1705.ironlog.feature.today.resources.today_nutrition_title
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal data class TodayNutritionState(
    val totals: Nutrients = Nutrients.ZERO,
    val logged: Boolean = false,
    val goals: NutritionGoals = NutritionGoals(),
) : MVIBaseState {
    /** The card shows only for people who use nutrition: something logged today, or goals set. */
    val visible: Boolean get() = logged || goals.isSet
}

internal sealed interface TodayNutritionIntent : MVIBaseIntent

/** Today's food totals against the goals, for the optional card on the Today tab (nutrition design §4). */
@OptIn(ExperimentalTime::class)
internal class TodayNutritionViewModel(repository: NutritionRepository) :
    MVIBaseViewModel<TodayNutritionState, TodayNutritionIntent>(TodayNutritionState()) {
    init {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        execute {
            combine(repository.observeDay(today), repository.observeGoals()) { day, goals ->
                TodayNutritionState(day.totals, day.entries.isNotEmpty(), goals)
            }.collect { s -> updateState { s } }
        }
    }

    override fun intentHandler(intent: TodayNutritionIntent): Job = execute { }
}

/** Today's nutrition: kcal against the goal and the macro bars; tapping it opens the Nutrition tab. */
@Composable
internal fun TodayNutritionCard(onOpen: () -> Unit, viewModel: TodayNutritionViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.visible) return
    val t = state.totals
    val g = state.goals
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.today_nutrition_title), style = MaterialTheme.typography.titleMedium)
            val kcal = Numbers.format(t.kcal, 0)
            Text(
                g.kcal?.let { stringResource(Res.string.today_nutrition_kcal_of, kcal, Numbers.format(it.toLong())) }
                    ?: stringResource(Res.string.today_nutrition_kcal, kcal),
                style = MaterialTheme.typography.headlineSmall.tabularFigures(),
            )
            MacroLine(
                stringResource(Res.string.today_nutrition_protein),
                t.protein,
                g.proteinG,
                MaterialTheme.colorScheme.primary
            )
            MacroLine(stringResource(Res.string.today_nutrition_carbs), t.carbs, g.carbsG, IronlogTheme.colors.prGold)
            MacroLine(
                stringResource(Res.string.today_nutrition_fat),
                t.fat,
                g.fatG,
                MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun MacroLine(label: String, grams: Double, goal: Int?, color: Color) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                stringResource(Res.string.today_nutrition_grams, Numbers.format(grams, 0)) +
                    (goal?.let { " / " + stringResource(Res.string.today_nutrition_grams, Numbers.format(it.toLong())) } ?: ""),
                style = MaterialTheme.typography.bodyMedium.tabularFigures(),
            )
        }
        val fraction = if (goal == null || goal <= 0) 0f else (grams / goal).toFloat().coerceIn(0f, 1f)
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
}
