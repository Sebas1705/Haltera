package es.sebas1705.haltera.feature.exercises.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.sebas1705.haltera.data.models.ExerciseUsage
import es.sebas1705.haltera.feature.exercises.resources.Res
import es.sebas1705.haltera.feature.exercises.resources.exercises_cancel
import es.sebas1705.haltera.feature.exercises.resources.exercises_delete_action
import es.sebas1705.haltera.feature.exercises.resources.exercises_delete_confirm
import es.sebas1705.haltera.feature.exercises.resources.exercises_delete_confirm_routines
import es.sebas1705.haltera.feature.exercises.resources.exercises_delete_hidden
import es.sebas1705.haltera.feature.exercises.resources.exercises_delete_routines
import es.sebas1705.haltera.feature.exercises.resources.exercises_delete_sessions
import es.sebas1705.haltera.feature.exercises.resources.exercises_delete_sets
import es.sebas1705.haltera.feature.exercises.resources.exercises_delete_title
import es.sebas1705.haltera.feature.exercises.resources.exercises_delete_unused
import es.sebas1705.haltera.feature.exercises.resources.exercises_detail_deleted
import es.sebas1705.haltera.feature.exercises.resources.exercises_edit_action
import es.sebas1705.haltera.feature.exercises.resources.exercises_restore
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// Custom exercise actions of the detail (core design §3.1.1): edit, delete with its confirmation, restore.

/** Shown on a custom exercise deleted while it had history: hidden everywhere else, restorable here. */
@Composable
internal fun DeletedBanner(onRestore: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.exercises_detail_deleted), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRestore) { Text(stringResource(Res.string.exercises_restore)) }
        }
    }
}

@Composable
internal fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

/**
 * The delete confirmation (core design §3.1.1): the routines that use the exercise (confirming removes it from
 * them) and its history (kept: the exercise is only hidden), or that nothing uses it (deleted for good).
 */
@Composable
internal fun DeleteDialog(
    name: String,
    usage: ExerciseUsage,
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(stringResource(Res.string.exercises_delete_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (usage.inRoutines) {
                    val count = usage.routines.size
                    Text(
                        pluralStringResource(
                            Res.plurals.exercises_delete_routines,
                            count,
                            count,
                            usage.routines.joinToString(", ") { it.name },
                        ),
                    )
                }
                if (usage.hasHistory) {
                    Text(pluralStringResource(Res.plurals.exercises_delete_sessions, usage.sessions, usage.sessions))
                    if (usage.sets > 0) {
                        Text(pluralStringResource(Res.plurals.exercises_delete_sets, usage.sets, usage.sets))
                    }
                    Text(stringResource(Res.string.exercises_delete_hidden))
                }
                if (!usage.inRoutines && !usage.hasHistory) Text(stringResource(Res.string.exercises_delete_unused))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !deleting) {
                Text(
                    stringResource(
                        if (usage.inRoutines) Res.string.exercises_delete_confirm_routines else Res.string.exercises_delete_confirm,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) { Text(stringResource(Res.string.exercises_cancel)) }
        },
    )
}

/** Edit and delete, for a custom exercise that is not deleted. */
@Composable
internal fun CustomActions(onEdit: () -> Unit, onDelete: () -> Unit) {
    IconButton(onClick = onEdit) {
        Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.exercises_edit_action))
    }
    IconButton(onClick = onDelete) {
        Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.exercises_delete_action))
    }
}
