package es.sebas1705.ironlog.feature.session.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.sebas1705.ironlog.data.models.Exercise
import es.sebas1705.ironlog.feature.session.resources.Res
import es.sebas1705.ironlog.feature.session.resources.session_cancel
import es.sebas1705.ironlog.feature.session.resources.session_delete
import es.sebas1705.ironlog.feature.session.resources.session_delete_text
import es.sebas1705.ironlog.feature.session.resources.session_delete_title
import es.sebas1705.ironlog.feature.session.resources.session_discard
import es.sebas1705.ironlog.feature.session.resources.session_discard_text
import es.sebas1705.ironlog.feature.session.resources.session_discard_title
import es.sebas1705.ironlog.feature.session.resources.session_finish
import es.sebas1705.ironlog.feature.session.resources.session_finish_text
import es.sebas1705.ironlog.feature.session.resources.session_finish_title
import es.sebas1705.ironlog.feature.session.resources.session_no_results
import es.sebas1705.ironlog.feature.session.resources.session_search_hint
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ConfirmFinishDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) = ConfirmDialog(
    Res.string.session_finish_title,
    Res.string.session_finish_text,
    Res.string.session_finish,
    onConfirm,
    onDismiss,
)

@Composable
internal fun ConfirmDiscardDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) = ConfirmDialog(
    Res.string.session_discard_title,
    Res.string.session_discard_text,
    Res.string.session_discard,
    onConfirm,
    onDismiss,
)

/** Log mode: deletes a saved session (the only confirmation the logbook asks for). */
@Composable
internal fun ConfirmDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) = ConfirmDialog(
    Res.string.session_delete_title,
    Res.string.session_delete_text,
    Res.string.session_delete,
    onConfirm,
    onDismiss,
)

@Composable
private fun ConfirmDialog(
    title: StringResource,
    text: StringResource,
    confirm: StringResource,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.session_cancel)) } },
    )
}

/**
 * Exercise picker (`ExerciseRepository.search`). Tapping an exercise with one variant adds it; with
 * several, it expands to its variants.
 */
@Composable
internal fun AddExerciseDialog(
    picker: ExercisePicker,
    onQuery: (String) -> Unit,
    onPick: (variantId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            OutlinedTextField(
                value = picker.query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.session_search_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            if (picker.results.isEmpty()) {
                Text(stringResource(Res.string.session_no_results))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(picker.results, key = { it.id }) { exercise ->
                        ExerciseResult(exercise, expanded == exercise.id, onPick) {
                            expanded = if (expanded == exercise.id) null else exercise.id
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.session_cancel)) } },
    )
}

@Composable
private fun ExerciseResult(exercise: Exercise, expanded: Boolean, onPick: (String) -> Unit, onExpand: () -> Unit) {
    val language = uiLanguage()
    val variants = exercise.variants.filterNot { it.deprecated }
    Column(Modifier.fillMaxWidth()) {
        Text(
            exercise.name.get(language),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                if (variants.size == 1) onPick(variants.first().id) else onExpand()
            }.padding(vertical = 12.dp),
        )
        if (expanded) {
            variants.forEach { variant ->
                Text(
                    variant.name?.get(language) ?: stringResource(equipmentLabel(variant.equipment)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(variant.id) }
                        .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}
