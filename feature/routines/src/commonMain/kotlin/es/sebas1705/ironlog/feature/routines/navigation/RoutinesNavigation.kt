@file:Suppress("MatchingDeclarationName") // Route objects and their graph builder live together.

package es.sebas1705.ironlog.feature.routines.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import es.sebas1705.ironlog.feature.routines.ui.analysis.RoutineAnalysisScreen
import es.sebas1705.ironlog.feature.routines.ui.catalogue.CatalogueScreen
import es.sebas1705.ironlog.feature.routines.ui.catalogue.TemplatePreviewScreen
import es.sebas1705.ironlog.feature.routines.ui.editor.RoutineEditorScreen
import es.sebas1705.ironlog.feature.routines.ui.list.RoutinesScreen
import es.sebas1705.ironlog.feature.routines.ui.picker.ExercisePickerScreen
import es.sebas1705.ironlog.feature.routines.ui.share.ImportSource
import es.sebas1705.ironlog.feature.routines.ui.share.RoutineImportScreen
import es.sebas1705.ironlog.feature.routines.ui.slot.SlotEditorScreen
import kotlinx.serialization.Serializable

/** Top-level destination of the Routines tab. */
@Serializable
data object RoutinesRoute

@Serializable
internal data class RoutineEditorRoute(val routineId: String)

@Serializable
internal data class ExercisePickerRoute(val routineId: String, val dayId: String)

@Serializable
internal data class SlotEditorRoute(val routineId: String, val slotId: String)

@Serializable
internal data object CatalogueRoute

@Serializable
internal data class TemplatePreviewRoute(val templateId: String)

@Serializable
internal data class ImportRoutineRoute(val source: String)

/** Analysis of a saved routine ([routineId]) or of a famous-routine template ([templateId]). */
@Serializable
internal data class RoutineAnalysisRoute(val routineId: String? = null, val templateId: String? = null)

/**
 * Registers the routines destinations. The app shell calls this from its NavHost.
 * Nested destinations (editor, slot editor, exercise picker, catalogue) draw their own top bar.
 */
@Suppress("LongMethod") // One composable per destination.
fun NavGraphBuilder.routinesGraph(
    navController: NavController,
) {
    val back: () -> Unit = { navController.popBackStack() }
    composable<RoutinesRoute> {
        RoutinesScreen(
            onOpenRoutine = { navController.navigate(RoutineEditorRoute(it)) },
            onOpenCatalogue = { navController.navigate(CatalogueRoute) },
            onImport = { navController.navigate(ImportRoutineRoute(it.name)) },
        )
    }
    composable<ImportRoutineRoute> { entry ->
        RoutineImportScreen(
            source = ImportSource.valueOf(entry.toRoute<ImportRoutineRoute>().source),
            onBack = back,
            onImported = { routineId ->
                // The new routine opens in the editor; back goes to My routines.
                navController.navigate(RoutineEditorRoute(routineId)) {
                    popUpTo<RoutinesRoute> { inclusive = false }
                }
            },
        )
    }
    composable<RoutineEditorRoute> { entry ->
        val routineId = entry.toRoute<RoutineEditorRoute>().routineId
        RoutineEditorScreen(
            routineId = routineId,
            onBack = back,
            onOpenSlot = { navController.navigate(SlotEditorRoute(routineId, it)) },
            onAddSlot = { navController.navigate(ExercisePickerRoute(routineId, it)) },
            onOpenAnalysis = { navController.navigate(RoutineAnalysisRoute(routineId = routineId)) },
        )
    }
    composable<ExercisePickerRoute> { entry ->
        val route = entry.toRoute<ExercisePickerRoute>()
        ExercisePickerScreen(
            dayId = route.dayId,
            onBack = back,
            onAdded = { slotId ->
                // Replace the picker with the new slot's editor, so back returns to the routine.
                navController.navigate(SlotEditorRoute(route.routineId, slotId)) {
                    popUpTo<ExercisePickerRoute> { inclusive = true }
                }
            },
        )
    }
    composable<SlotEditorRoute> { entry ->
        val route = entry.toRoute<SlotEditorRoute>()
        SlotEditorScreen(routineId = route.routineId, slotId = route.slotId, onBack = back)
    }
    composable<CatalogueRoute> {
        CatalogueScreen(onBack = back, onOpenTemplate = { navController.navigate(TemplatePreviewRoute(it)) })
    }
    composable<RoutineAnalysisRoute> { entry ->
        val route = entry.toRoute<RoutineAnalysisRoute>()
        RoutineAnalysisScreen(routineId = route.routineId, templateId = route.templateId, onBack = back)
    }
    composable<TemplatePreviewRoute> { entry ->
        TemplatePreviewScreen(
            templateId = entry.toRoute<TemplatePreviewRoute>().templateId,
            onBack = back,
            onOpenAnalysis = { navController.navigate(RoutineAnalysisRoute(templateId = it)) },
            onAdded = { routineId ->
                // The copy opens in the editor; back goes to My routines.
                navController.navigate(RoutineEditorRoute(routineId)) {
                    popUpTo<RoutinesRoute> { inclusive = false }
                }
            },
        )
    }
}
