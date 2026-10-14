package es.sebas1705.ironlog.feature.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.ironlog.core.ui.components.LoadingScreen
import es.sebas1705.ironlog.feature.session.platform.KeepScreenOn
import es.sebas1705.ironlog.feature.session.platform.RequestSessionPermissions
import es.sebas1705.ironlog.feature.session.resources.Res
import es.sebas1705.ironlog.feature.session.resources.session_add_exercise
import es.sebas1705.ironlog.feature.session.resources.session_back
import es.sebas1705.ironlog.feature.session.resources.session_discard
import es.sebas1705.ironlog.feature.session.resources.session_empty
import es.sebas1705.ironlog.feature.session.resources.session_finish
import es.sebas1705.ironlog.feature.session.resources.session_free
import es.sebas1705.ironlog.feature.session.resources.session_missing
import es.sebas1705.ironlog.feature.session.resources.session_more
import es.sebas1705.ironlog.feature.session.timer.TimerMath
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The live session of [sessionId] (spec §5). [onFinished] leaves after finishing or discarding. */
@Composable
fun SessionScreen(
    sessionId: String,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionViewModel = koinViewModel(key = "session-$sessionId") { parametersOf(sessionId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val language = uiLanguage()
    LaunchedEffect(language) { viewModel.onEvent(SessionIntent.SetLanguage(language)) }
    LaunchedEffect(state.discarded) { if (state.discarded) onFinished() }
    KeepScreenOn(enabled = state.inProgress && state.settings.keepScreenOn)
    if (state.inProgress) RequestSessionPermissions()

    SessionContent(state, viewModel::onEvent, onBack, onFinished, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionContent(
    state: SessionState,
    onEvent: (SessionIntent) -> Unit,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by rememberNow()
    Scaffold(
        modifier = modifier,
        topBar = { SessionTopBar(state, now, onEvent, onBack) },
        bottomBar = {
            if (state.inProgress) {
                RestBanner(
                    timer = state.timer,
                    now = now,
                    onAdjust = { onEvent(SessionIntent.AdjustRest(it)) },
                    onSkip = { onEvent(SessionIntent.SkipRest) },
                    onStop = { onEvent(SessionIntent.StopTimedSet) },
                )
            }
        },
    ) { padding ->
        val summary = state.summary
        when {
            state.loading || state.discarded -> LoadingScreen(Modifier.padding(padding))
            state.missing -> Centered(stringResource(Res.string.session_missing), Modifier.padding(padding))
            // A session finished here returns to Today; a past one (opened from Progress) just goes back.
            summary != null -> SummaryView(
                summary = summary,
                unit = state.settings.unit,
                onDone = if (state.finishedHere) onFinished else onBack,
                modifier = Modifier.padding(padding),
            )
            else -> ExerciseList(state, onEvent, padding)
        }
    }
    SessionOverlays(state, onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionTopBar(state: SessionState, now: Long, onEvent: (SessionIntent) -> Unit, onBack: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.session_back))
            }
        },
        title = {
            Column {
                Text(state.dayName ?: stringResource(Res.string.session_free), maxLines = 1)
                if (state.startedAt > 0) {
                    val end = state.endedAt ?: now
                    Text(TimerMath.formatElapsed(end - state.startedAt), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        actions = {
            if (state.inProgress) {
                IconButton(onClick = { onEvent(SessionIntent.OpenAddExercise) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.session_add_exercise))
                }
                TextButton(onClick = { onEvent(SessionIntent.RequestFinish) }, enabled = !state.busy) {
                    Text(stringResource(Res.string.session_finish))
                }
                OverflowMenu(onDiscard = { onEvent(SessionIntent.RequestDiscard) })
            }
        },
    )
}

@Composable
private fun OverflowMenu(onDiscard: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.session_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.session_discard)) },
                onClick = {
                    open = false
                    onDiscard()
                },
            )
        }
    }
}

@Composable
private fun ExerciseList(state: SessionState, onEvent: (SessionIntent) -> Unit, padding: PaddingValues) {
    if (state.cards.isEmpty()) {
        Centered(stringResource(Res.string.session_empty), Modifier.padding(padding))
        return
    }
    val actions = remember(onEvent) {
        CardActions(
            onToggleSet = { onEvent(SessionIntent.ToggleSet(it)) },
            onStartTimed = { onEvent(SessionIntent.StartTimedSet(it)) },
            onEditSet = { onEvent(SessionIntent.EditSet(it)) },
            onAddSet = { onEvent(SessionIntent.AddSet(it)) },
            onChooseVariant = { card, variant -> onEvent(SessionIntent.ChooseVariant(card, variant)) },
            onMove = { card, delta -> onEvent(SessionIntent.MoveExercise(card, delta)) },
            onRemove = { onEvent(SessionIntent.RemoveExercise(it)) },
        )
    }
    val current = state.currentCardId
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.cards, key = { it.id }) { card ->
            ExerciseCardView(
                card = card,
                current = card.id == current,
                editable = state.inProgress,
                settings = state.settings,
                actions = actions,
            )
        }
    }
}

@Composable
private fun SessionOverlays(state: SessionState, onEvent: (SessionIntent) -> Unit) {
    val dismiss = { onEvent(SessionIntent.DismissDialog) }
    when (state.dialog) {
        SessionDialog.ConfirmFinish -> ConfirmFinishDialog(onConfirm = {
            onEvent(SessionIntent.ConfirmFinish)
        }, onDismiss = dismiss)
        SessionDialog.ConfirmDiscard -> ConfirmDiscardDialog(onConfirm = {
            onEvent(SessionIntent.ConfirmDiscard)
        }, onDismiss = dismiss)
        SessionDialog.AddExercise -> AddExerciseDialog(
            picker = state.picker,
            onQuery = { onEvent(SessionIntent.SearchExercises(it)) },
            onPick = { onEvent(SessionIntent.AddExercise(it)) },
            onDismiss = dismiss,
        )
        null -> Unit
    }
    val editor = state.editor ?: return
    val card = state.cards.firstOrNull { it.id == editor.cardId } ?: return
    SetEditorSheet(
        card = card,
        draft = editor.draft,
        settings = state.settings,
        onChange = { onEvent(SessionIntent.ChangeDraft(it)) },
        onSave = { onEvent(SessionIntent.SaveDraft) },
        onDelete = { onEvent(SessionIntent.DeleteEditedSet) },
        onDismiss = { onEvent(SessionIntent.CloseEditor) },
    )
}

@Composable
private fun Centered(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
