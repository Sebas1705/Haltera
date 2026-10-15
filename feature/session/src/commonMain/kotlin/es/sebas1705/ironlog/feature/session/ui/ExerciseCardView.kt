@file:Suppress("MatchingDeclarationName") // The card composable and its callbacks live together.

package es.sebas1705.ironlog.feature.session.ui

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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.core.datastore.UserSettings
import es.sebas1705.ironlog.core.ui.theme.IronlogTheme
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.SetKind
import es.sebas1705.ironlog.data.models.SetLog
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.domain.usecases.progression.IncreaseReason
import es.sebas1705.ironlog.domain.usecases.progression.Suggestion
import es.sebas1705.ironlog.feature.session.load.isMachinePlate
import es.sebas1705.ironlog.feature.session.resources.Res
import es.sebas1705.ironlog.feature.session.resources.session_add_set
import es.sebas1705.ironlog.feature.session.resources.session_increase
import es.sebas1705.ironlog.feature.session.resources.session_increase_marker
import es.sebas1705.ironlog.feature.session.resources.session_more
import es.sebas1705.ironlog.feature.session.resources.session_move_down
import es.sebas1705.ironlog.feature.session.resources.session_move_up
import es.sebas1705.ironlog.feature.session.resources.session_prescription_distance
import es.sebas1705.ironlog.feature.session.resources.session_prescription_duration
import es.sebas1705.ironlog.feature.session.resources.session_prescription_range
import es.sebas1705.ironlog.feature.session.resources.session_prescription_reps
import es.sebas1705.ironlog.feature.session.resources.session_reason_target
import es.sebas1705.ironlog.feature.session.resources.session_reason_top
import es.sebas1705.ironlog.feature.session.resources.session_remove_exercise
import es.sebas1705.ironlog.feature.session.resources.session_rest_info
import es.sebas1705.ironlog.feature.session.resources.session_set_complete
import es.sebas1705.ironlog.feature.session.resources.session_set_start_timer
import es.sebas1705.ironlog.feature.session.resources.session_variant_last
import es.sebas1705.ironlog.feature.session.resources.session_variant_never
import es.sebas1705.ironlog.feature.session.resources.session_variant_pick
import es.sebas1705.ironlog.feature.session.timer.TimerMath
import org.jetbrains.compose.resources.stringResource

/** Callbacks of an exercise card. */
internal class CardActions(
    val onToggleSet: (String) -> Unit,
    val onStartTimed: (String) -> Unit,
    val onEditSet: (String) -> Unit,
    val onAddSet: (String) -> Unit,
    val onChooseVariant: (cardId: String, variantId: String) -> Unit,
    val onMove: (cardId: String, delta: Int) -> Unit,
    val onRemove: (String) -> Unit,
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
        if (editable) CardMenu(card, actions)
    }
}

@Composable
private fun CardMenu(card: ExerciseCard, actions: CardActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.session_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.session_move_up)) },
                leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                onClick = {
                    open = false
                    actions.onMove(card.id, -1)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.session_move_down)) },
                leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                onClick = {
                    open = false
                    actions.onMove(card.id, 1)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.session_remove_exercise)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    open = false
                    actions.onRemove(card.id)
                },
            )
        }
    }
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
        color = IronlogTheme.colors.prGold,
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
                FilterChip(
                    selected = option.variantId == card.variantId,
                    onClick = { onChoose(option.variantId) },
                    // Chips tell the variants apart: the variant label, never the (shared) exercise name.
                    label = { Text("${option.name.subtitle() ?: option.name.title()} · $last") },
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
    val doneLabel = stringResource(Res.string.session_set_complete, number)
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
                    color = IronlogTheme.colors.prGold,
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
