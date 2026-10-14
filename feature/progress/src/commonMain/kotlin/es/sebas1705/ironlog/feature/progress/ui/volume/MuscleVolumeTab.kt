package es.sebas1705.ironlog.feature.progress.ui.volume

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.core.units.WeightUnit
import es.sebas1705.ironlog.feature.progress.model.MuscleVolume
import es.sebas1705.ironlog.feature.progress.resources.Res
import es.sebas1705.ironlog.feature.progress.resources.progress_volume_empty
import es.sebas1705.ironlog.feature.progress.resources.progress_volume_intro
import es.sebas1705.ironlog.feature.progress.resources.progress_volume_week
import es.sebas1705.ironlog.feature.progress.ui.components.formatDate
import es.sebas1705.ironlog.feature.progress.ui.components.formatVolume
import es.sebas1705.ironlog.feature.progress.ui.components.muscleLabel
import es.sebas1705.ironlog.feature.progress.ui.exercise.EmptyMessage
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Volume tab: per primary muscle, one bar per week (oldest first), scaled to the busiest week. */
@Composable
internal fun MuscleVolumeTab(viewModel: MuscleVolumeViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when {
        state.loading -> LoadingScreen()
        state.muscles.isEmpty() -> EmptyMessage(stringResource(Res.string.progress_volume_empty))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(Res.string.progress_volume_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.muscles, key = { it.muscle.name }) { muscle ->
                MuscleCard(muscle, state.weekStarts, state.maxWeekVolume, state.unit)
            }
        }
    }
}

@Composable
private fun MuscleCard(volume: MuscleVolume, weekStarts: List<LocalDate>, max: Long, unit: WeightUnit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(muscleLabel(volume.muscle), style = MaterialTheme.typography.titleMedium)
            weekStarts.forEachIndexed { index, weekStart ->
                WeekBar(
                    label = formatDate(weekStart),
                    weekLabel = stringResource(Res.string.progress_volume_week, formatDate(weekStart)),
                    value = volume.weeks.getOrElse(index) { 0 },
                    max = max,
                    unit = unit,
                    highlighted = index == weekStarts.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun WeekBar(label: String, weekLabel: String, value: Long, max: Long, unit: WeightUnit, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    val valueText = formatVolume(value, unit)
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$weekLabel: $valueText" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(colors.surfaceVariant)) {
            val fraction = if (max > 0) value.toFloat() / max else 0f
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (highlighted) colors.primary else colors.primary.copy(alpha = 0.55f)),
                )
            }
        }
        Text(
            valueText,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp).width(88.dp),
        )
    }
}
