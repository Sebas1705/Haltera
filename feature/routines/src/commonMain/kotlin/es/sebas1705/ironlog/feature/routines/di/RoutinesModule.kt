package es.sebas1705.ironlog.feature.routines.di

import androidx.compose.ui.text.intl.Locale
import es.sebas1705.ironlog.feature.routines.model.AppLanguage
import es.sebas1705.ironlog.feature.routines.ui.catalogue.CatalogueViewModel
import es.sebas1705.ironlog.feature.routines.ui.catalogue.TemplatePreviewViewModel
import es.sebas1705.ironlog.feature.routines.ui.editor.RoutineEditorViewModel
import es.sebas1705.ironlog.feature.routines.ui.list.RoutinesViewModel
import es.sebas1705.ironlog.feature.routines.ui.picker.ExercisePickerViewModel
import es.sebas1705.ironlog.feature.routines.ui.share.ImportSource
import es.sebas1705.ironlog.feature.routines.ui.share.RoutineExportViewModel
import es.sebas1705.ironlog.feature.routines.ui.share.RoutineImportViewModel
import es.sebas1705.ironlog.feature.routines.ui.slot.SlotEditorViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin bindings of the routines feature (ViewModels, feature-local classes). Registered in composeApp. */
val routinesModule = module {
    single<AppLanguage> { AppLanguage { Locale.current.language } }
    viewModelOf(::RoutinesViewModel)
    viewModelOf(::CatalogueViewModel)
    viewModelOf(::RoutineExportViewModel)
    viewModel { (source: ImportSource) -> RoutineImportViewModel(source, get(), get(), get(), get()) }
    viewModel { (templateId: String) -> TemplatePreviewViewModel(templateId, get(), get(), get()) }
    viewModel { (routineId: String) -> RoutineEditorViewModel(routineId, get(), get(), get()) }
    viewModel { (dayId: String) -> ExercisePickerViewModel(dayId, get(), get(), get()) }
    viewModel { (routineId: String, slotId: String) ->
        SlotEditorViewModel(
            routineId,
            slotId,
            get(),
            get(),
            get(),
            get()
        )
    }
}
