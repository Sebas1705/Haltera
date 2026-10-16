package es.sebas1705.haltera.feature.routines.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import es.sebas1705.haltera.feature.routines.resources.routines_day_actions
import es.sebas1705.haltera.feature.routines.resources.routines_duplicate_day
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import es.sebas1705.haltera.data.models.DayKind
import es.sebas1705.haltera.data.models.RoutineDay
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.feature.routines.model.ExerciseIndex
import es.sebas1705.haltera.feature.routines.resources.Res
import es.sebas1705.haltera.feature.routines.resources.routines_add_exercise
import es.sebas1705.haltera.feature.routines.resources.routines_day_number
import es.sebas1705.haltera.feature.routines.resources.routines_delete_day
import es.sebas1705.haltera.feature.routines.resources.routines_delete_slot
import es.sebas1705.haltera.feature.routines.resources.routines_kind_rest
import es.sebas1705.haltera.feature.routines.resources.routines_kind_training
import es.sebas1705.haltera.feature.routines.resources.routines_no_slots
import es.sebas1705.haltera.feature.routines.resources.routines_rename_day
import es.sebas1705.haltera.feature.routines.resources.routines_rest_day
import es.sebas1705.haltera.feature.routines.ui.components.MoveButtons
import es.sebas1705.haltera.feature.routines.ui.components.slotSummary
import es.sebas1705.haltera.feature.routines.ui.components.targetLabel
import org.jetbrains.compose.resources.stringResource

/** One day of the cycle: name, training/rest, order, and its slots. */
@Composable
internal fun DayCard(
    day: RoutineDay,
    dayCount: Int,
    index: ExerciseIndex,
    language: String,
    send: (RoutineEditorIntent) -> Unit,
    onRename: () -> Unit,
    onOpenSlot: (String) -> Unit,
    onAddSlot: () -> Unit,
    highlighted: Boolean = false,
    /** False for a flexible routine: every day trains (a stray rest day still shows the switch to fix it). */
    showKind: Boolean = true,
) {
    val rest = day.kind == DayKind.REST
    val accent = MaterialTheme.colorScheme.primary
    val highlight by animateColorAsState(if (highlighted) accent else accent.copy(alpha = 0f), label = "day-highlight")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (rest) CardDefaults.outlinedCardColors() else CardDefaults.cardColors(),
        border = when {
            highlighted -> BorderStroke(2.dp, highlight)
            rest -> CardDefaults.outlinedCardBorder()
            else -> BorderStroke(2.dp, highlight)
        },
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            DayHeader(day, dayCount, send, onRename)
            if (showKind || rest) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !rest,
                        onClick = { send(RoutineEditorIntent.SetDayKind(day.id, DayKind.TRAINING)) },
                        label = { Text(stringResource(Res.string.routines_kind_training)) },
                    )
                    FilterChip(
                        selected = rest,
                        onClick = { send(RoutineEditorIntent.SetDayKind(day.id, DayKind.REST)) },
                        label = { Text(stringResource(Res.string.routines_kind_rest)) },
                    )
                }
            }
            if (rest && day.slots.isEmpty()) {
                MutedText(stringResource(Res.string.routines_rest_day))
            } else {
                DaySlots(day, index, language, send, onOpenSlot)
                if (!rest) {
                    TextButton(onClick = onAddSlot) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(Res.string.routines_add_exercise), Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MutedText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

/** "Day 2 / Push ✎" (tap to rename), then move and delete buttons. */
@Composable
private fun DayHeader(day: RoutineDay, dayCount: Int, send: (RoutineEditorIntent) -> Unit, onRename: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(Res.string.routines_rename_day),
                    onClick = onRename,
                )
                .padding(vertical = 8.dp),
        ) {
            Text(
                stringResource(Res.string.routines_day_number, day.position + 1),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    day.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp).padding(2.dp),
                )
            }
        }
        MoveButtons(
            itemLabel = day.name,
            canMoveUp = day.position > 0,
            canMoveDown = day.position < dayCount - 1,
            onMove = { send(RoutineEditorIntent.MoveDay(day.id, it)) },
        )
        DayMenu(day, send)
    }
}

@Composable
private fun DaySlots(
    day: RoutineDay,
    index: ExerciseIndex,
    language: String,
    send: (RoutineEditorIntent) -> Unit,
    onOpenSlot: (String) -> Unit,
) {
    if (day.slots.isEmpty()) MutedText(stringResource(Res.string.routines_no_slots))
    day.slots.forEachIndexed { position, slot ->
        if (position > 0) HorizontalDivider()
        SlotRow(
            slot = slot,
            label = targetLabel(slot.target, index, language),
            canMoveUp = position > 0,
            canMoveDown = position < day.slots.lastIndex,
            send = send,
            onOpen = { onOpenSlot(slot.id) },
        )
    }
}

@Composable
private fun SlotRow(
    slot: RoutineSlot,
    label: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    send: (RoutineEditorIntent) -> Unit,
    onOpen: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                slotSummary(slot.toSpec()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoveButtons(
            itemLabel = label,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onMove = { send(RoutineEditorIntent.MoveSlot(slot.id, it)) },
        )
        IconButton(onClick = { send(RoutineEditorIntent.RequestDeleteSlot(slot.id)) }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.routines_delete_slot, label))
        }
    }
}

/** The day's overflow menu: duplicate and delete. */
@Composable
private fun DayMenu(day: RoutineDay, send: (RoutineEditorIntent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.routines_day_actions, day.name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.routines_duplicate_day)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    open = false
                    send(RoutineEditorIntent.DuplicateDay(day.id))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.routines_delete_day, day.name)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    open = false
                    send(RoutineEditorIntent.RequestDeleteDay(day.id))
                },
            )
        }
    }
}
