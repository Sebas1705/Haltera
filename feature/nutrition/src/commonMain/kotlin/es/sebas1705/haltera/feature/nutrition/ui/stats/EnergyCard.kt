package es.sebas1705.haltera.feature.nutrition.ui.stats

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import es.sebas1705.haltera.core.ui.components.HealthDisclaimer
import es.sebas1705.haltera.core.ui.components.HealthDisclaimerKind
import es.sebas1705.haltera.core.ui.theme.tabularFigures
import es.sebas1705.haltera.feature.nutrition.model.Amounts
import es.sebas1705.haltera.feature.nutrition.resources.Res
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_energy
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_energy_average
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_energy_balance
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_energy_burned
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_energy_day
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_energy_eaten
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_energy_info
import es.sebas1705.haltera.feature.nutrition.resources.nutrition_stats_energy_summary
import es.sebas1705.haltera.feature.nutrition.ui.components.SectionCard
import es.sebas1705.haltera.feature.nutrition.ui.components.shortDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val DAYS_SHOWN = 7

/**
 * Eaten vs burned: calories logged in Nutrition against the total calories burned that Health Connect reports,
 * for the days that have both. Shown only when Health Connect calories are connected. Interprets logged data, so
 * it carries the statistics HealthDisclaimer.
 */
@Composable
internal fun EnergyCard(state: StatsState) {
    val summary = state.energySummary ?: return
    SectionCard(
        stringResource(Res.string.nutrition_stats_energy),
        info = stringResource(Res.string.nutrition_stats_energy_info)
    ) {
        Text(
            pluralStringResource(
                Res.plurals.nutrition_stats_energy_summary,
                summary.days,
                summary.days,
                kcal(summary.averageEaten),
                kcal(summary.averageBurned),
                signedKcal(summary.averageBalance),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        val header = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        Cells(
            listOf(
                stringResource(Res.string.nutrition_stats_energy_day),
                stringResource(Res.string.nutrition_stats_energy_eaten),
                stringResource(Res.string.nutrition_stats_energy_burned),
                stringResource(Res.string.nutrition_stats_energy_balance),
            ),
            header,
        )
        HorizontalDivider()
        val body = MaterialTheme.typography.bodySmall.tabularFigures()
        state.energy.takeLast(DAYS_SHOWN).asReversed().forEach { day ->
            Cells(listOf(shortDate(day.date), kcal(day.eatenKcal), kcal(day.burnedKcal), signedKcal(day.balance)), body)
        }
        HorizontalDivider()
        Cells(
            listOf(
                stringResource(Res.string.nutrition_stats_energy_average),
                kcal(summary.averageEaten),
                kcal(summary.averageBurned),
                signedKcal(summary.averageBalance),
            ),
            body,
        )
        HealthDisclaimer(HealthDisclaimerKind.STATISTICS)
    }
}

private fun kcal(value: Double): String = Amounts.format(value, 0)

private fun signedKcal(value: Double): String {
    val rounded = Amounts.format(kotlin.math.abs(value), 0)
    return when {
        rounded == Amounts.format(0.0, 0) -> rounded
        value > 0 -> "+$rounded"
        else -> "−$rounded"
    }
}

@Composable
private fun Cells(texts: List<String>, style: TextStyle) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        texts.forEachIndexed { i, text ->
            Text(
                text,
                style = style,
                modifier = Modifier.weight(1f),
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End
            )
        }
    }
}
