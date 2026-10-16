@file:Suppress("MatchingDeclarationName") // The card composable and its callbacks live together.

package es.sebas1705.haltera.feature.session.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.sebas1705.haltera.core.datastore.UserSettings
import es.sebas1705.haltera.core.ui.theme.HalteraTheme
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.SetKind
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.domain.usecases.progression.IncreaseReason
import es.sebas1705.haltera.domain.usecases.progression.Suggestion
import es.sebas1705.haltera.feature.session.load.isMachinePlate
import es.sebas1705.haltera.feature.session.resources.Res
import es.sebas1705.haltera.feature.session.resources.session_add_set
import es.sebas1705.haltera.feature.session.resources.session_increase
import es.sebas1705.haltera.feature.session.resources.session_increase_marker
import es.sebas1705.haltera.feature.session.resources.session_exercise_description
import es.sebas1705.haltera.feature.session.resources.session_exercise_statistics
import es.sebas1705.haltera.feature.session.resources.session_more
import es.sebas1705.haltera.feature.session.resources.session_move_down
import es.sebas1705.haltera.feature.session.resources.session_move_up
import es.sebas1705.haltera.feature.session.resources.session_prescription_distance
import es.sebas1705.haltera.feature.session.resources.session_prescription_duration
import es.sebas1705.haltera.feature.session.resources.session_prescription_range
import es.sebas1705.haltera.feature.session.resources.session_prescription_reps
import es.sebas1705.haltera.feature.session.resources.session_reason_target
import es.sebas1705.haltera.feature.session.resources.session_reason_top
import es.sebas1705.haltera.feature.session.resources.session_remove_exercise
import es.sebas1705.haltera.feature.session.resources.session_rest_info
import es.sebas1705.haltera.feature.session.resources.session_set_complete
import es.sebas1705.haltera.feature.session.resources.session_set_start_timer
import es.sebas1705.haltera.feature.session.resources.session_variant_last
import es.sebas1705.haltera.feature.session.resources.session_variant_never
import es.sebas1705.haltera.feature.session.resources.session_variant_pick
import es.sebas1705.haltera.feature.session.resources.session_warmup_complete
import es.sebas1705.haltera.feature.session.timer.TimerMath
import org.jetbrains.compose.resources.stringResource

/** Callbacks of an exercise card. */
@Suppress("LongParameterList") // One callback per card action.
internal class CardActions(
    val onToggleSet: (String) -> Unit,
    val onStartTimed: (String) -> Unit,
    val onEditSet: (String) -> Unit,
    val onAddSet: (String) -> Unit,
    val onChooseVariant: (cardId: String, variantId: String) -> Unit,
    val onMove: (cardId: String, delta: Int) -> Unit,
    val onRemove: (String) -> Unit,
    val onStatistics: (String) -> Unit = {},
    val onDescription: (String) -> Unit = {},
)

@Composable
internal fun ExerciseCardView(
    card: ExerciseCard,
    current: Boolean,
    editable: Boolean,
    settings: UserSettings,
    actions: CardActions,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = if (current) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            CardHeader(card, editable, actions)
            SuggestionLine(card, settings)
            // Flexible slot: pick the equipment when the exercise is reached, before its first set.
            val pickVariant = current && !card.started && card.variantOptions.size > 1
            if (editable && pickVariant) {
                VariantPicker(card, settings) { actions.onChooseVariant(card.id, it) }
            }
            card.sets.forEachIndexed { index, set ->
                SetRow(card, set, workingNumber(card.sets, index), editable, settings, actions)
            }
            if (editable) {
                TextButton(onClick = { actions.onAddSet(card.id) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(Res.string.session_add_set), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/** 1-based number among the working-type sets (warm-ups show "W" instead). */
private fun workingNumber(sets: List<SetLog>, index: Int): Int =
    sets.take(index + 1).count { it.kind != SetKind.WARMUP }

/** 1-based number of [set] among the warm-ups of [sets]. */
private fun warmupNumber(sets: List<SetLog>, set: SetLog): Int {
    val index = sets.indexOfFirst { it === set }.takeIf { it >= 0 } ?: sets.indexOf(set)
    return sets.take(index + 1).count { it.kind == SetKind.WARMUP }.coerceAtLeast(1)
}

@Composable
private fun CardHeader(card: ExerciseCard, editable: Boolean, actions: CardActions) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(card.name.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            card.name.subtitle()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            prescriptionText(card)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Statistics and description are always there; moving and removing only while the session can change.
        if (card.exerciseId != null || editable) CardMenu(card, editable, actions)
    }
}

@Composable
private fun CardMenu(card: ExerciseCard, editable: Boolean, actions: CardActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.session_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (card.exerciseId != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.session_exercise_statistics)) },
                    leadingIcon = { Icon(StatisticsIcon, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.onStatistics(card.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.session_exercise_description)) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.onDescription(card.id)
                    },
                )
            }
            if (editable) EditItems(card, actions) { open = false }
        }
    }
}

@Composable
private fun EditItems(card: ExerciseCard, actions: CardActions, close: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.session_move_up)) },
        leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
        onClick = {
            close()
            actions.onMove(card.id, -1)
        },
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.session_move_down)) },
        leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
        onClick = {
            close()
            actions.onMove(card.id, 1)
        },
    )
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.session_remove_exercise)) },
        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        onClick = {
            close()
            actions.onRemove(card.id)
        },
    )
}

/** A small bar chart: the exercise's statistics. */
private val StatisticsIcon: ImageVector by lazy {
    ImageVector.Builder("Statistics", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            // Three bars of rising height on a baseline.
            moveTo(4f, 20f)
            lineTo(20f, 20f)
            lineTo(20f, 18f)
            lineTo(4f, 18f)
            close()
            moveTo(5f, 12f)
            lineTo(8f, 12f)
            lineTo(8f, 17f)
            lineTo(5f, 17f)
            close()
            moveTo(10.5f, 8f)
            lineTo(13.5f, 8f)
            lineTo(13.5f, 17f)
            lineTo(10.5f, 17f)
            close()
            moveTo(16f, 4f)
            lineTo(19f, 4f)
            lineTo(19f, 17f)
            lineTo(16f, 17f)
            close()
        }
    }.build()
}

@Composable
private fun prescriptionText(card: ExerciseCard): String? {
    val slot = card.slot ?: return null
    val sets = slot.sets
    val target = when (val p = slot.prescription) {
        is Prescription.RepRange -> stringResource(Res.string.session_prescription_range, sets, p.min, p.max)
        is Prescription.Reps -> stringResource(Res.string.session_prescription_reps, sets, p.reps)
        is Prescription.Duration -> stringResource(Res.string.session_prescription_duration, sets, p.seconds)
        is Prescription.Distance -> stringResource(Res.string.session_prescription_distance, sets, p.meters)
    }
    val rest = stringResource(Res.string.session_rest_info, TimerMath.formatSeconds(slot.restBetweenSetsS.toLong()))
    return "$target · $rest"
}

@Composable
private fun SuggestionLine(card: ExerciseCard, settings: UserSettings) {
    val increase = card.suggestion as? Suggestion.Increase ?: return
    if (card.started) return
    val plate = isMachinePlate(card.sets.firstOrNull()?.loadDetail)
    val reason = stringResource(
        if (increase.reason == IncreaseReason.TOP_OF_RANGE) Res.string.session_reason_top else Res.string.session_reason_target,
    )
    val load = loadText(
        increase.toGrams,
        if (plate) card.sets.firstOrNull()?.loadDetail else null,
        card.loadType,
        settings.unit
    )
    Text(
        text = stringResource(Res.string.session_increase, load, reason),
        style = MaterialTheme.typography.labelLarge,
        color = HalteraTheme.colors.prGold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun VariantPicker(card: ExerciseCard, settings: UserSettings, onChoose: (String) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            stringResource(Res.string.session_variant_pick),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            items(card.variantOptions, key = { it.variantId }) { option ->
                val last = option.lastLoadGrams?.let {
                    stringResource(Res.string.session_variant_last, loadText(it, null, option.loadType, settings.unit))
                } ?: stringResource(Res.string.session_variant_never)
                val selected = option.variantId == card.variantId
                FilterChip(
                    selected = selected,
                    onClick = { onChoose(option.variantId) },
                    // Chips tell the variants apart: the variant label, never the (shared) exercise name.
                    label = { Text("${option.name.subtitle() ?: option.name.title()} · $last") },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    },
                    // The current card is secondaryContainer, the default selected fill: use primary instead.
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SetRow(
    card: ExerciseCard,
    set: SetLog,
    number: Int,
    editable: Boolean,
    settings: UserSettings,
    actions: CardActions,
) {
    val increase = card.suggestion is Suggestion.Increase && set.kind == SetKind.WORKING && !set.completed
    // Warm-ups have no working-set number: they are announced as "Warm-up 1 done".
    val doneLabel = if (set.kind == SetKind.WARMUP) {
        stringResource(Res.string.session_warmup_complete, warmupNumber(card.sets, set))
    } else {
        stringResource(Res.string.session_set_complete, number)
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = kindShort(set.kind) ?: number.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = if (set.kind == SetKind.WORKING) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.widthIn(min = 32.dp).padding(start = 8.dp),
        )
        Row(
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .clickable(enabled = editable) { actions.onEditSet(set.id) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = setValuesText(set, card.trackingType, card.loadType, settings.unit),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (set.completed) FontWeight.Normal else FontWeight.Medium,
                color = if (set.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (increase) {
                val marker = stringResource(Res.string.session_increase_marker)
                Text(
                    "↑",
                    color = HalteraTheme.colors.prGold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 6.dp).semantics { contentDescription = marker },
                )
            }
        }
        if (editable && card.trackingType == TrackingType.TIME && !set.completed) {
            IconButton(onClick = { actions.onStartTimed(set.id) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(Res.string.session_set_start_timer))
            }
        }
        Checkbox(
            checked = set.completed,
            onCheckedChange = if (editable) ({ actions.onToggleSet(set.id) }) else null,
            modifier = Modifier.size(48.dp).semantics { contentDescription = doneLabel },
        )
    }
}
