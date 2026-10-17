package es.sebas1705.haltera.feature.today.di

import es.sebas1705.haltera.feature.today.ui.TodayClock
import es.sebas1705.haltera.feature.today.ui.TodayNutritionViewModel
import es.sebas1705.haltera.feature.today.ui.TodayStepsViewModel
import es.sebas1705.haltera.feature.today.ui.TodayViewModel
import kotlinx.datetime.TimeZone
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Koin bindings of the today feature (ViewModels, feature-local classes). Registered in composeApp. */
@OptIn(ExperimentalTime::class)
val todayModule = module {
    factory {
        TodayClock(
            now = { Clock.System.now().toEpochMilliseconds() },
            timeZone = TimeZone.currentSystemDefault()
        )
    }
    viewModelOf(::TodayViewModel)
    viewModelOf(::TodayNutritionViewModel)
    viewModelOf(::TodayStepsViewModel)
}
