package es.sebas1705.haltera.feature.progress.ui.exercise

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import es.sebas1705.haltera.core.ui.components.LoadingScreen
import es.sebas1705.haltera.feature.progress.resources.Res
import es.sebas1705.haltera.feature.progress.resources.progress_back
import es.sebas1705.haltera.feature.progress.resources.progress_exercise_focus_empty
import es.sebas1705.haltera.feature.progress.resources.progress_tab_exercises
import es.sebas1705.haltera.feature.progress.ui.components.currentLanguage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The progress of one exercise (e1RM trend, progression table, records), opened from outside the Progress tab
 * (the live session). A nested destination: its own top bar with the exercise name and a back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseProgressScreen(
    exerciseId: String,
    variantId: String?,
    onBack: () -> Unit,
    onOpenExerciseDetail: (exerciseId: String) -> Unit = {},
    viewModel: ExerciseProgressViewModel = koinViewModel(key = "exercise-progress-$exerciseId-$variantId") {
        parametersOf(ExerciseFocus(exerciseId, variantId))
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = state.focusName?.get(currentLanguage()) ?: stringResource(Res.string.progress_tab_exercises)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.progress_back)
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                state.loading -> LoadingScreen()
                state.variants.isEmpty() -> EmptyMessage(stringResource(Res.string.progress_exercise_focus_empty))
                else -> ExerciseProgressContent(state, onOpenExerciseDetail) { viewModel.onEvent(it) }
            }
        }
    }
}
