package es.sebas1705.haltera.feature.nutrition.ui.goals

import es.sebas1705.haltera.core.common.mvi.MVIBaseIntent
import es.sebas1705.haltera.core.common.mvi.MVIBaseState
import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.data.models.ActivityLevel
import es.sebas1705.haltera.data.models.MacroMode
import es.sebas1705.haltera.data.models.NutritionGoals
import es.sebas1705.haltera.data.models.NutritionProfile
import es.sebas1705.haltera.data.models.Objective
import es.sebas1705.haltera.data.models.Sex
import es.sebas1705.haltera.data.repository.BodyWeightRepository
import es.sebas1705.haltera.data.repository.NutritionRepository
import es.sebas1705.haltera.domain.usecases.nutrition.GoalCalculator
import es.sebas1705.haltera.domain.usecases.nutrition.GoalCalculator.KCAL_PER_G_CARBS
import es.sebas1705.haltera.domain.usecases.nutrition.GoalCalculator.KCAL_PER_G_FAT
import es.sebas1705.haltera.domain.usecases.nutrition.GoalCalculator.KCAL_PER_G_PROTEIN
import es.sebas1705.haltera.domain.usecases.nutrition.GoalSuggestion
import es.sebas1705.haltera.feature.nutrition.model.Today
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first

internal enum class Macro(val kcalPerGram: Double) { PROTEIN(
    KCAL_PER_G_PROTEIN
), CARBS(KCAL_PER_G_CARBS), FAT(KCAL_PER_G_FAT) }

internal data class GoalsState(
    val loading: Boolean = true,
    val kcalText: String = "",
    /** Macro goals as typed: grams, or percent of the kcal goal in [MacroMode.PERCENT]. */
    val macroText: Map<Macro, String> = emptyMap(),
    val mode: MacroMode = MacroMode.GRAMS,
    val sex: Sex? = null,
    val birthYearText: String = "",
    val heightText: String = "",
    val activity: ActivityLevel = ActivityLevel.MODERATE,
    val objective: Objective = Objective.MAINTAIN,
    val weightGrams: Long? = null,
    val currentYear: Int,
    val saved: Boolean = false,
) : MVIBaseState {
    val kcal: Int? get() = kcalText.trim().toIntOrNull()?.takeIf { it in 1..MAX_KCAL }

    fun macroText(macro: Macro): String = macroText[macro].orEmpty()

    /** A macro goal in grams (converted from percent when needed), or null. */
    fun macroGrams(macro: Macro): Int? {
        val value = macroText(macro).trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return when (mode) {
            MacroMode.GRAMS -> value.takeIf { it <= MAX_GRAMS }
            MacroMode.PERCENT -> kcal?.let {
                GoalCalculator.gramsFromPercent(
                    it,
                    value.coerceAtMost(PERCENT),
                    macro.kcalPerGram
                )
            }
        }
    }

    /** Sum of the macro percentages (PERCENT mode). */
    val percentSum: Int get() = Macro.entries.sumOf { macroText(it).trim().toIntOrNull() ?: 0 }

    val kcalFromMacros: Int?
        get() = Macro.entries.map { m ->
            macroGrams(
                m
            )?.let { it * m.kcalPerGram }
        }.takeIf { list -> list.all { it != null } }
            ?.sumOf { it ?: 0.0 }?.toInt()

    val profile: NutritionProfile
        get() = NutritionProfile(
            sex = sex,
            birthYear = birthYearText.trim().toIntOrNull(),
            heightCm = heightText.trim().toIntOrNull(),
            activity = activity,
            objective = objective,
        )

    val suggestion: GoalSuggestion? get() = GoalCalculator.suggest(
        profile,
        weightGrams?.let { it / GRAMS_PER_KG },
        currentYear
    )

    val kcalError: Boolean get() = kcalText.isNotBlank() && kcal == null

    companion object {
        const val MAX_KCAL = 10_000
        const val MAX_GRAMS = 2_000
        const val PERCENT = 100
        const val GRAMS_PER_KG = 1_000.0
    }
}

internal sealed interface GoalsIntent : MVIBaseIntent {
    data class KcalChanged(val text: String) : GoalsIntent

    data class MacroChanged(val macro: Macro, val text: String) : GoalsIntent

    data class ModeChanged(val mode: MacroMode) : GoalsIntent

    data class SexChanged(val sex: Sex) : GoalsIntent

    data class BirthYearChanged(val text: String) : GoalsIntent

    data class HeightChanged(val text: String) : GoalsIntent

    data class ActivityChanged(val activity: ActivityLevel) : GoalsIntent

    data class ObjectiveChanged(val objective: Objective) : GoalsIntent

    data object ApplySuggestion : GoalsIntent

    data object Clear : GoalsIntent

    data object Save : GoalsIntent
}

/** Optional daily goals with the Mifflin–St Jeor calculator (design §1). Nothing is enforced. */
internal class GoalsViewModel(
    private val repository: NutritionRepository,
    private val bodyWeight: BodyWeightRepository,
    today: Today,
) : MVIBaseViewModel<GoalsState, GoalsIntent>(GoalsState(currentYear = today.date().year)) {

    init {
        execute {
            val goals = repository.observeGoals().first()
            updateState { it.from(goals).copy(loading = false) }
        }
        execute {
            bodyWeight.observeLatest().collect { latest ->
                updateState {
                    it.copy(
                        weightGrams = latest?.weightGrams
                    )
                }
            }
        }
    }

    override fun intentHandler(intent: GoalsIntent): Job = execute {
        when (intent) {
            is GoalsIntent.KcalChanged -> updateState { it.copy(kcalText = intent.text.filter(Char::isDigit)) }
            is GoalsIntent.MacroChanged -> updateState {
                it.copy(
                    macroText = it.macroText + (intent.macro to intent.text.filter(Char::isDigit))
                )
            }
            is GoalsIntent.ModeChanged -> updateState { it.switchMode(intent.mode) }
            is GoalsIntent.SexChanged -> updateState { it.copy(sex = intent.sex) }
            is GoalsIntent.BirthYearChanged -> updateState {
                it.copy(
                    birthYearText = intent.text.filter(Char::isDigit).take(YEAR_DIGITS)
                )
            }
            is GoalsIntent.HeightChanged -> updateState {
                it.copy(
                    heightText = intent.text.filter(Char::isDigit).take(HEIGHT_DIGITS)
                )
            }
            is GoalsIntent.ActivityChanged -> updateState { it.copy(activity = intent.activity) }
            is GoalsIntent.ObjectiveChanged -> updateState { it.copy(objective = intent.objective) }
            GoalsIntent.ApplySuggestion -> updateState { it.applySuggestion() }
            GoalsIntent.Clear -> updateState {
                it.copy(kcalText = "", macroText = emptyMap())
            }
            GoalsIntent.Save -> save()
        }
    }

    private fun save() {
        val s = currentState
        if (s.kcalError) return
        repository.setGoals(
            NutritionGoals(
                kcal = s.kcal,
                proteinG = s.macroGrams(Macro.PROTEIN),
                carbsG = s.macroGrams(Macro.CARBS),
                fatG = s.macroGrams(Macro.FAT),
                macroMode = s.mode,
                profile = s.profile,
            ),
        )
        updateState { it.copy(saved = true) }
    }

    private companion object {
        const val YEAR_DIGITS = 4
        const val HEIGHT_DIGITS = 3
    }
}

private fun GoalsState.from(goals: NutritionGoals): GoalsState {
    val base = copy(
        kcalText = goals.kcal?.toString().orEmpty(),
        mode = MacroMode.GRAMS,
        macroText = mapOf(
            Macro.PROTEIN to goals.proteinG?.toString().orEmpty(),
            Macro.CARBS to goals.carbsG?.toString().orEmpty(),
            Macro.FAT to goals.fatG?.toString().orEmpty(),
        ),
        sex = goals.profile.sex,
        birthYearText = goals.profile.birthYear?.toString().orEmpty(),
        heightText = goals.profile.heightCm?.toString().orEmpty(),
        activity = goals.profile.activity,
        objective = goals.profile.objective,
    )
    return if (goals.macroMode == MacroMode.PERCENT) base.switchMode(MacroMode.PERCENT) else base
}

/** Converts the typed macros to the other mode (percent needs the kcal goal). */
private fun GoalsState.switchMode(mode: MacroMode): GoalsState {
    if (mode == this.mode) return this
    val kcal = kcal
    val converted = Macro.entries.associateWith { macro ->
        val value = macroText(macro).trim().toIntOrNull()
        when {
            value == null || kcal == null -> macroText(macro)
            mode == MacroMode.PERCENT -> GoalCalculator.percentFromGrams(kcal, value, macro.kcalPerGram).toString()
            else -> GoalCalculator.gramsFromPercent(kcal, value, macro.kcalPerGram).toString()
        }
    }
    return copy(mode = mode, macroText = converted)
}

private fun GoalsState.applySuggestion(): GoalsState {
    val s = suggestion ?: return this
    val grams = copy(
        kcalText = s.kcal.toString(),
        mode = MacroMode.GRAMS,
        macroText = mapOf(
            Macro.PROTEIN to s.proteinG.toString(),
            Macro.CARBS to s.carbsG.toString(),
            Macro.FAT to s.fatG.toString()
        ),
    )
    return if (mode == MacroMode.PERCENT) grams.switchMode(MacroMode.PERCENT) else grams
}
