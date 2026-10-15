package es.sebas1705.ironlog.feature.routines.ui.analysis

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import es.sebas1705.ironlog.core.ui.theme.IronlogTheme
import es.sebas1705.ironlog.core.units.Numbers
import es.sebas1705.ironlog.domain.usecases.routinescore.Advice
import es.sebas1705.ironlog.domain.usecases.routinescore.BalanceSide
import es.sebas1705.ironlog.domain.usecases.routinescore.CriterionId
import es.sebas1705.ironlog.domain.usecases.routinescore.Finding
import es.sebas1705.ironlog.domain.usecases.routinescore.MuscleGroup
import es.sebas1705.ironlog.domain.usecases.routinescore.RepZone
import es.sebas1705.ironlog.domain.usecases.routinescore.RoutineFocus
import es.sebas1705.ironlog.domain.usecases.routinescore.RoutineScorer
import es.sebas1705.ironlog.domain.usecases.routinescore.SideRatio
import es.sebas1705.ironlog.domain.usecases.routinescore.Verdict
import es.sebas1705.ironlog.feature.routines.resources.Res
import es.sebas1705.ironlog.feature.routines.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Localised text for RoutineScore data (findings, advice, labels). Numbers are formatted here; words come from resources.

/** Colour for a 0–100 score: green from "good", amber for "fair", red below. */
@Composable
internal fun scoreColor(score: Int): Color = when {
    score >= RoutineScorer.GOOD_FROM -> IronlogTheme.colors.success
    score >= RoutineScorer.FAIR_FROM -> IronlogTheme.colors.warning
    else -> IronlogTheme.colors.error
}

/** A number with at most one decimal, with the decimal separator of [language]. */
internal fun formatOne(value: Double, language: String): String =
    Numbers.format(value, maxDecimals = 1, language = language)

internal fun formatPercent(fraction: Double, language: String): String {
    return Numbers.percent((fraction * 100).roundToInt().toDouble(), language = language)
}

private fun formatRatio(sides: SideRatio?, language: String, none: String): String {
    val ratio = sides?.ratio ?: return none
    return "${formatOne(ratio, language)}:1"
}

@Composable
internal fun groupLabel(group: MuscleGroup): String = stringResource(
    when (group) {
        MuscleGroup.CHEST -> Res.string.routines_group_chest
        MuscleGroup.BACK -> Res.string.routines_group_back
        MuscleGroup.SHOULDERS -> Res.string.routines_group_shoulders
        MuscleGroup.QUADS -> Res.string.routines_group_quads
        MuscleGroup.HAMSTRINGS -> Res.string.routines_group_hamstrings
        MuscleGroup.GLUTES -> Res.string.routines_group_glutes
        MuscleGroup.BICEPS -> Res.string.routines_group_biceps
        MuscleGroup.TRICEPS -> Res.string.routines_group_triceps
        MuscleGroup.CALVES -> Res.string.routines_group_calves
        MuscleGroup.ABS -> Res.string.routines_group_abs
        MuscleGroup.TRAPS -> Res.string.routines_group_traps
        MuscleGroup.LOWER_BACK -> Res.string.routines_group_lower_back
        MuscleGroup.FOREARMS -> Res.string.routines_group_forearms
        MuscleGroup.HIPS -> Res.string.routines_group_hips
        MuscleGroup.NECK -> Res.string.routines_group_neck
    },
)

internal fun criterionTitleRes(id: CriterionId): StringResource = when (id) {
    CriterionId.VOLUME -> Res.string.routines_criterion_volume
    CriterionId.FREQUENCY -> Res.string.routines_criterion_frequency
    CriterionId.COVERAGE -> Res.string.routines_criterion_coverage
    CriterionId.BALANCE -> Res.string.routines_criterion_balance
    CriterionId.REP_RANGES -> Res.string.routines_criterion_rep_ranges
    CriterionId.RECOVERY -> Res.string.routines_criterion_recovery
    CriterionId.PROGRESSION -> Res.string.routines_criterion_progression
    CriterionId.SESSION_LENGTH -> Res.string.routines_criterion_session_length
    CriterionId.ADHERENCE -> Res.string.routines_criterion_adherence
    CriterionId.LOAD_PROGRESS -> Res.string.routines_criterion_load_progress
}

internal fun criterionInfoRes(id: CriterionId): StringResource = when (id) {
    CriterionId.VOLUME -> Res.string.routines_info_volume
    CriterionId.FREQUENCY -> Res.string.routines_info_frequency
    CriterionId.COVERAGE -> Res.string.routines_info_coverage
    CriterionId.BALANCE -> Res.string.routines_info_balance
    CriterionId.REP_RANGES -> Res.string.routines_info_rep_ranges
    CriterionId.RECOVERY -> Res.string.routines_info_recovery
    CriterionId.PROGRESSION -> Res.string.routines_info_progression
    CriterionId.SESSION_LENGTH -> Res.string.routines_info_session_length
    CriterionId.ADHERENCE -> Res.string.routines_info_adherence
    CriterionId.LOAD_PROGRESS -> Res.string.routines_info_load_progress
}

@Composable
internal fun verdictLabel(verdict: Verdict): String = when (verdict) {
    Verdict.EXCELLENT -> stringResource(Res.string.routines_verdict_excellent)
    Verdict.GOOD -> stringResource(Res.string.routines_verdict_good)
    Verdict.FAIR -> stringResource(Res.string.routines_verdict_fair)
    Verdict.NEEDS_WORK -> stringResource(Res.string.routines_verdict_needs_work)
    Verdict.NOT_SCORED -> stringResource(Res.string.routines_score_cardio)
}

@Composable
internal fun verdictText(verdict: Verdict): String = when (verdict) {
    Verdict.EXCELLENT -> stringResource(Res.string.routines_verdict_excellent_text)
    Verdict.GOOD -> stringResource(Res.string.routines_verdict_good_text)
    Verdict.FAIR -> stringResource(Res.string.routines_verdict_fair_text)
    Verdict.NEEDS_WORK -> stringResource(Res.string.routines_verdict_needs_work_text)
    Verdict.NOT_SCORED -> stringResource(Res.string.routines_analysis_not_scored)
}

@Composable
internal fun focusLabel(focus: RoutineFocus): String? = when (focus) {
    RoutineFocus.STRENGTH -> stringResource(Res.string.routines_focus_strength)
    RoutineFocus.HYPERTROPHY -> stringResource(Res.string.routines_focus_hypertrophy)
    RoutineFocus.CONDITIONING, RoutineFocus.EMPTY -> null
}

/** The lines describing a finding. */
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod") // One branch per finding type.
internal fun findingLines(finding: Finding, language: String): List<String> {
    val none = stringResource(Res.string.routines_not_available)
    return when (finding) {
        is Finding.Volume -> buildList {
            add(stringResource(Res.string.routines_finding_volume, finding.inRange, finding.counted))
            if (finding.low.isNotEmpty()) {
                val list = finding.low.map { "${groupLabel(it.group)} ${formatOne(it.sets, language)}" }
                add(stringResource(Res.string.routines_finding_volume_low, list.joinToString(", ")))
            }
            if (finding.high.isNotEmpty()) {
                val list = finding.high.map { sets ->
                    val split = if (sets.secondary > 0.0) {
                        " (" + setsSplit(sets.direct, sets.secondary, language) + ")"
                    } else {
                        ""
                    }
                    "${groupLabel(sets.group)} ${formatOne(sets.sets, language)}$split"
                }
                add(stringResource(Res.string.routines_finding_volume_high, list.joinToString(", ")))
            }
        }
        is Finding.Frequency -> if (finding.low.isEmpty()) {
            listOf(stringResource(Res.string.routines_finding_frequency_ok))
        } else {
            val list = finding.low.map { "${groupLabel(it.group)} ${formatOne(it.perWeek, language)}×" }
            listOf(stringResource(Res.string.routines_finding_frequency_low, list.joinToString(", ")))
        }
        is Finding.Coverage -> if (finding.missing.isEmpty()) {
            listOf(stringResource(Res.string.routines_finding_coverage_ok))
        } else {
            val list = finding.missing.map { groupLabel(it) }
            listOf(stringResource(Res.string.routines_finding_coverage_missing, list.joinToString(", ")))
        }
        is Finding.Balance -> listOf(
            stringResource(
                Res.string.routines_finding_balance,
                formatRatio(finding.pushPull, language, none),
                formatRatio(finding.quadsPosterior, language, none),
            ),
        )
        is Finding.RepRanges -> listOf(
            stringResource(
                Res.string.routines_finding_rep_ranges,
                formatPercent(finding.shares[RepZone.HEAVY] ?: 0.0, language),
                formatPercent(finding.shares[RepZone.MODERATE] ?: 0.0, language),
                formatPercent(finding.shares[RepZone.LIGHT] ?: 0.0, language),
            ),
            stringResource(
                if (finding.heavyCompound) {
                    Res.string.routines_finding_heavy_compound
                } else {
                    Res.string.routines_finding_no_heavy_compound
                },
            ),
        )
        is Finding.Recovery -> if (finding.conflicts.isEmpty()) {
            listOf(stringResource(Res.string.routines_finding_recovery_ok))
        } else {
            val list = finding.conflicts.map { "${groupLabel(it.group)} (${it.firstDay} → ${it.secondDay})" }
            listOf(stringResource(Res.string.routines_finding_recovery, list.joinToString(", ")))
        }
        is Finding.SessionLength -> listOf(
            stringResource(
                Res.string.routines_finding_session_length,
                finding.longestDay,
                finding.longestMinutes,
                finding.averageMinutes,
            ),
        )
        is Finding.Progression ->
            listOf(stringResource(Res.string.routines_finding_progression, finding.withRule, finding.total))
        is Finding.Adherence -> listOf(
            stringResource(
                Res.string.routines_finding_adherence,
                finding.done,
                finding.planned,
                finding.weeks,
                formatPercent(finding.setCompletion, language),
            ),
        )
        is Finding.LoadProgress -> listOf(
            stringResource(Res.string.routines_finding_load_progress, finding.improved, finding.tracked, finding.weeks),
        )
    }
}

/** "Romanian deadlift or leg curl". */
@Composable
private fun exercises(ids: List<String>, name: (String) -> String): String = when (ids.size) {
    0 -> ""
    1 -> name(ids[0])
    else -> stringResource(Res.string.routines_or, ids.dropLast(1).joinToString(", ") { name(it) }, name(ids.last()))
}

@Composable
private fun dayOrAny(day: String?): String = day?.let(::quoted) ?: stringResource(Res.string.routines_any_day)

/** A routine day name in quotes, so names like "Back" read as days, not muscles. */
private fun quoted(name: String): String = "“$name”"

/** One advice line; [exerciseName] turns a catalogue exercise id into its name. */
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod") // One branch per advice type.
internal fun adviceText(advice: Advice, language: String, exerciseName: (String) -> String): String = when (advice) {
    is Advice.AddSets -> stringResource(
        Res.string.routines_advice_add_sets,
        groupLabel(advice.group),
        formatOne(advice.weeklySets, language),
        advice.addMin,
        advice.addMax,
        exercises(advice.exerciseIds, exerciseName),
        dayOrAny(advice.day),
    )
    is Advice.RemoveSets -> stringResource(
        Res.string.routines_advice_remove_sets,
        groupLabel(advice.group),
        formatOne(advice.weeklySets, language),
        advice.removeMin,
        advice.removeMax,
    )
    is Advice.SpreadAcrossDays -> stringResource(
        Res.string.routines_advice_spread,
        groupLabel(advice.group),
        formatOne(advice.perWeek, language),
        formatOne(advice.weeklySets, language),
        dayOrAny(advice.day),
    )
    is Advice.ShoulderOverlap -> stringResource(
        Res.string.routines_advice_shoulder_overlap,
        formatOne(advice.sets.sets, language),
        setsSplit(advice.sets.direct, advice.sets.secondary, language),
    )
    is Advice.Cover -> stringResource(
        Res.string.routines_advice_cover,
        groupLabel(advice.group),
        exercises(advice.exerciseIds, exerciseName),
        dayOrAny(advice.day),
    )
    is Advice.Rebalance -> stringResource(
        when (advice.weaker) {
            BalanceSide.PULL -> Res.string.routines_advice_more_pull
            BalanceSide.PUSH -> Res.string.routines_advice_more_push
            BalanceSide.QUADS -> Res.string.routines_advice_more_quads
            BalanceSide.POSTERIOR -> Res.string.routines_advice_more_posterior
        },
        "${formatOne(advice.ratio, language)}:1",
        exercises(advice.exerciseIds, exerciseName),
        dayOrAny(advice.day),
    )
    is Advice.AddHeavyCompound ->
        stringResource(Res.string.routines_advice_heavy_compound, exercises(advice.exerciseIds, exerciseName))
    is Advice.AddRepZone -> stringResource(
        when (advice.only) {
            RepZone.HEAVY -> Res.string.routines_advice_zone_heavy
            RepZone.MODERATE -> Res.string.routines_advice_zone_moderate
            RepZone.LIGHT -> Res.string.routines_advice_zone_light
        },
    )
    is Advice.SeparateDays -> stringResource(
        Res.string.routines_advice_separate,
        groupLabel(advice.conflict.group),
        quoted(advice.conflict.firstDay),
        quoted(advice.conflict.secondDay),
    )
    is Advice.ShortenSession -> stringResource(Res.string.routines_advice_shorten, quoted(advice.day), advice.minutes)
    is Advice.AddProgressionRules -> stringResource(
        Res.string.routines_advice_progression,
        advice.without,
        advice.total
    )
    is Advice.FitTheSchedule -> stringResource(Res.string.routines_advice_schedule, advice.done, advice.planned)
    is Advice.BreakPlateau -> stringResource(
        Res.string.routines_advice_plateau,
        exercises(advice.exerciseIds, exerciseName)
    )
}

/** "30 direct + 7 secondary" / "30 directas + 1 secundaria", each part agreeing with its own count. */
@Composable
internal fun setsSplit(direct: Double, secondary: Double, language: String): String = stringResource(
    Res.string.routines_sets_split,
    pluralStringResource(Res.plurals.routines_sets_direct, pluralQuantity(direct), formatOne(direct, language)),
    pluralStringResource(
        Res.plurals.routines_sets_secondary,
        pluralQuantity(secondary),
        formatOne(secondary, language)
    ),
)

/** The plural quantity of a possibly fractional count: 1 is "one", 1.5 or 0 is "other". */
internal fun pluralQuantity(value: Double): Int = if (value % 1.0 == 0.0) value.toInt() else 2
