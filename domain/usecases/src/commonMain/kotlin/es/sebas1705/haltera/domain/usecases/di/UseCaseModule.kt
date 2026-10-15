package es.sebas1705.haltera.domain.usecases.di

import es.sebas1705.haltera.domain.usecases.session.LogPastSession
import es.sebas1705.haltera.domain.usecases.session.ObserveTodayPlan
import es.sebas1705.haltera.domain.usecases.session.SuggestNextLoad
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/** Use cases with dependencies. The pure engines (`ProgressionEngine`, `Cycle`, `Stats`) are objects. */
val useCaseModule = module {
    factoryOf(::SuggestNextLoad)
    factoryOf(::ObserveTodayPlan)
    factoryOf(::LogPastSession)
}
