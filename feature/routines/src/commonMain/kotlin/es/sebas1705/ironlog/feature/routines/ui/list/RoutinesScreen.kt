package es.sebas1705.ironlog.feature.routines.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.data.models.Routine
import es.sebas1705.ironlog.feature.routines.resources.Res
import es.sebas1705.ironlog.feature.routines.resources.routines_active
import es.sebas1705.ironlog.feature.routines.resources.routines_delete
import es.sebas1705.ironlog.feature.routines.resources.routines_delete_routine_message
import es.sebas1705.ironlog.feature.routines.resources.routines_delete_routine_title
import es.sebas1705.ironlog.feature.routines.resources.routines_empty_message
import es.sebas1705.ironlog.feature.routines.resources.routines_empty_title
import es.sebas1705.ironlog.feature.routines.resources.routines_error
import es.sebas1705.ironlog.feature.routines.resources.routines_from_catalogue
import es.sebas1705.ironlog.feature.routines.resources.routines_import
import es.sebas1705.ironlog.feature.routines.resources.routines_import_file
import es.sebas1705.ironlog.feature.routines.resources.routines_import_scan
import es.sebas1705.ironlog.feature.routines.resources.routines_more_actions
import es.sebas1705.ironlog.feature.routines.resources.routines_new_routine
import es.sebas1705.ironlog.feature.routines.resources.routines_new_routine_default_name
import es.sebas1705.ironlog.feature.routines.resources.routines_day_default_name
import es.sebas1705.ironlog.feature.routines.resources.routines_set_active
import es.sebas1705.ironlog.feature.routines.resources.routines_set_inactive
import es.sebas1705.ironlog.feature.routines.resources.routines_untitled
import es.sebas1705.ironlog.feature.routines.ui.components.ConfirmDialog
import es.sebas1705.ironlog.feature.routines.ui.components.cycleLabel
import es.sebas1705.ironlog.feature.routines.ui.components.trainingDays
import es.sebas1705.ironlog.feature.routines.share.RoutineShareBridge
import es.sebas1705.ironlog.feature.routines.ui.share.ExportMenuItems
import es.sebas1705.ironlog.feature.routines.ui.share.ExportOverlays
import es.sebas1705.ironlog.feature.routines.ui.share.ImportSource
import es.sebas1705.ironlog.feature.routines.ui.share.RoutineExportViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Routines tab root: my routines with the active badge, and the ways to add one. */
@Composable
@Suppress("LongMethod") // Wires the list, the add/import buttons and the export overlays.
internal fun RoutinesScreen(
    onOpenRoutine: (routineId: String) -> Unit,
    onOpenCatalogue: () -> Unit,
    onImport: (ImportSource) -> Unit,
    viewModel: RoutinesViewModel = koinViewModel(),
    export: RoutineExportViewModel = koinViewModel(),
    bridge: RoutineShareBridge = koinInject(),
) {
    val incoming by bridge.incoming.collectAsStateWithLifecycle()
    // A routine opened from outside the app (file or share sheet) goes straight to the import preview.
    LaunchedEffect(incoming) { if (incoming != null) onImport(ImportSource.INCOMING) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val newName = stringResource(Res.string.routines_new_routine_default_name)
    val firstDay = stringResource(Res.string.routines_day_default_name, 1)
    val create: () -> Unit = { viewModel.onEvent(RoutinesIntent.Create(newName, firstDay)) }

    LaunchedEffect(state.openRoutineId) {
        state.openRoutineId?.let { id ->
            viewModel.onEvent(RoutinesIntent.OpenHandled)
            onOpenRoutine(id)
        }
    }
    LaunchedEffect(state.error) {
        if (state.error) {
            snackbar.showSnackbar(getString(Res.string.routines_error))
            viewModel.onEvent(RoutinesIntent.ErrorShown)
        }
    }

    Scaffold(
        // The shell already pads the tab roots for the system bars.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.routines.isNotEmpty()) {
                AddButtons(
                    onCreate = { if (!state.creating) create() },
                    onOpenCatalogue,
                    onImport
                )
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingScreen(Modifier.padding(padding))
            state.routines.isEmpty() -> EmptyRoutines(
                onCreate = create,
                onOpenCatalogue = onOpenCatalogue,
                onImport = onImport,
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                // Room for the two stacked buttons.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.routines, key = { it.id }) { routine ->
                    RoutineCard(
                        routine = routine,
                        onOpen = { onOpenRoutine(routine.id) },
                        onSetActive = { viewModel.onEvent(RoutinesIntent.SetActive(routine.id, it)) },
                        onDelete = { viewModel.onEvent(RoutinesIntent.RequestDelete(routine)) },
                        export = export,
                    )
                }
            }
        }
    }

    state.pendingDelete?.let { routine ->
        ConfirmDialog(
            title = stringResource(Res.string.routines_delete_routine_title),
            message = stringResource(Res.string.routines_delete_routine_message, routine.name),
            confirmLabel = stringResource(Res.string.routines_delete),
            onConfirm = { viewModel.onEvent(RoutinesIntent.ConfirmDelete) },
            onDismiss = { viewModel.onEvent(RoutinesIntent.DismissDelete) },
        )
    }
    ExportOverlays(export)
}

@Composable
private fun RoutineCard(
    routine: Routine,
    onOpen: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    export: RoutineExportViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen),
        colors = if (routine.isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        routine.name.ifBlank { stringResource(Res.string.routines_untitled) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (routine.isActive) ActiveBadge()
                }
                Text(
                    cycleLabel(routine.days.size, trainingDays(routine.days.map { it.kind })),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (routine.description.isNotBlank()) {
                    Text(
                        routine.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RoutineMenu(routine, onSetActive, onDelete, export)
        }
    }
}

/** "From catalogue" and "New routine", stacked. */
@Composable
private fun AddButtons(onCreate: () -> Unit, onOpenCatalogue: () -> Unit, onImport: (ImportSource) -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ImportMenuButton(onImport)
        ExtendedFloatingActionButton(
            onClick = onOpenCatalogue,
            icon = { Icon(Icons.Filled.Search, contentDescription = null) },
            text = { Text(stringResource(Res.string.routines_from_catalogue)) },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ExtendedFloatingActionButton(
            onClick = onCreate,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(Res.string.routines_new_routine)) },
        )
    }
}

@Composable
private fun RoutineMenu(
    routine: Routine,
    onSetActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
    export: RoutineExportViewModel,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menu = true }) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(Res.string.routines_more_actions, routine.name)
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (routine.isActive) Res.string.routines_set_inactive else Res.string.routines_set_active
                        ),
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                onClick = {
                    menu = false
                    onSetActive(!routine.isActive)
                },
            )
            ExportMenuItems(routine.id, export) { menu = false }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.routines_delete)) },
                onClick = {
                    menu = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
internal fun ActiveBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            stringResource(Res.string.routines_active),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyRoutines(
    onCreate: () -> Unit,
    onOpenCatalogue: () -> Unit,
    onImport: (ImportSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(Res.string.routines_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(Res.string.routines_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpenCatalogue, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Search, contentDescription = null)
            Text(stringResource(Res.string.routines_from_catalogue), Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(Res.string.routines_new_routine), Modifier.padding(start = 8.dp))
        }
        ImportMenuButton(onImport)
    }
}

/** "Import" with a menu: from a file or by scanning a QR code. */
@Composable
private fun ImportMenuButton(onImport: (ImportSource) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ExtendedFloatingActionButton(
            onClick = { menu = true },
            icon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            text = { Text(stringResource(Res.string.routines_import)) },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.routines_import_file)) },
                onClick = {
                    menu = false
                    onImport(ImportSource.FILE)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.routines_import_scan)) },
                onClick = {
                    menu = false
                    onImport(ImportSource.SCAN)
                },
            )
        }
    }
}
