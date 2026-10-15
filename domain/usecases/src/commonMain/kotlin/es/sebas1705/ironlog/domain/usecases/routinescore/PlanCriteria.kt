package es.sebas1705.ironlog.domain.usecases.routinescore

import es.sebas1705.ironlog.data.models.ProgressionRule
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/** The criteria computed from the plan alone (design §3.1–3.8). */
@Suppress("TooManyFunctions") // One function per criterion plus their scoring helpers.
internal class PlanCriteria(private val plan: RoutinePlan, private val focus: RoutineFocus) {

    fun all(): List<Criterion> =
        listOfNotNull(
            volume(),
            frequency(),
            coverage(),
            balance(),
            repRanges(),
            recovery(),
            progression(),
            sessionLength()
        )

    private fun criterion(
        id: CriterionId,
        score: Double,
        finding: Finding,
        advice: List<Advice>,
        vararg evidence: Reference,
    ) = Criterion(
        id,
        percent(score),
        RoutineScorer.WEIGHTS.getValue(id),
        finding,
        advice.take(MAX_ADVICE),
        evidence.toList()
    )

    private fun groupWeight(group: MuscleGroup): Double = when {
        group.large -> 1.0
        focus == RoutineFocus.STRENGTH -> STRENGTH_SMALL_WEIGHT
        else -> SMALL_WEIGHT
    }

    private fun weightedMean(scores: Map<MuscleGroup, Double>): Double {
        val weights = scores.keys.sumOf(::groupWeight)
        return if (weights == 0.0) 1.0 else scores.entries.sumOf { (g, s) -> s * groupWeight(g) } / weights
    }

    // 3.1 Weekly volume: 10–20 sets per major group (strength focus: from 6).
    private fun volume(): Criterion {
        val lower = if (focus == RoutineFocus.STRENGTH) STRENGTH_LOW else TARGET_LOW
        val scores = MuscleGroup.majors.associateWith { volumeScore(plan.weekly(it), lower) }
        val low = MuscleGroup.majors.filter { plan.weekly(it) < FLAG_LOW }
        val high = MuscleGroup.majors.filter { plan.weekly(it) > FLAG_HIGH }
        val advice = buildList {
            MuscleGroup.majors
                .filter { plan.weekly(it) < lower }
                .sortedByDescending { (lower - plan.weekly(it)) * groupWeight(it) }
                .take(2)
                .forEach { group ->
                    val weekly = plan.weekly(group)
                    val perCycle = plan.perCycle(lower - weekly)
                    val addMin = (perCycle / 2).roundToInt().coerceIn(MIN_ADD, MAX_ADD_MIN)
                    val addMax = ceil(perCycle).toInt().coerceIn(addMin + 1, MAX_ADD)
                    val newDay = plan.frequency(group) < MIN_FREQUENCY
                    add(
                        Advice.AddSets(
                            group,
                            floor(weekly).toInt(), // Rounded down: 9.5 must not read as the 10-set target.
                            addMin,
                            addMax,
                            RoutineScorer.SUGGESTED_EXERCISES[group].orEmpty().take(2),
                            plan.dayFor(group, newDay),
                        ),
                    )
                }
            high.forEach { group ->
                val weekly = plan.weekly(group)
                add(
                    Advice.RemoveSets(
                        group,
                        weekly.roundToInt(),
                        ceil(weekly - FLAG_HIGH).toInt().coerceAtLeast(MIN_ADD),
                        ceil(weekly - TARGET_HIGH).toInt().coerceAtLeast(MIN_ADD + 1),
                    ),
                )
            }
        }
        return criterion(
            CriterionId.VOLUME,
            weightedMean(scores),
            Finding.Volume(
                low = low.map { GroupSets(it, round1(plan.weekly(it))) },
                high = high.map { GroupSets(it, round1(plan.weekly(it))) },
                inRange = MuscleGroup.majors.count { plan.weekly(it) in TARGET_LOW..TARGET_HIGH },
                counted = MuscleGroup.majors.size,
            ),
            advice,
            Reference.SCHOENFELD_2017_VOLUME,
            Reference.PELLAND_2024,
            Reference.RALSTON_2017,
        )
    }

    private fun volumeScore(sets: Double, lower: Double): Double = when {
        sets < lower && focus == RoutineFocus.STRENGTH && sets >= STRENGTH_LOW ->
            STRENGTH_BAND_FLOOR + (1 - STRENGTH_BAND_FLOOR) * (sets - STRENGTH_LOW) / (TARGET_LOW - STRENGTH_LOW)
        sets < lower -> sets / lower * (if (focus == RoutineFocus.STRENGTH) STRENGTH_BAND_FLOOR else 1.0)
        sets <= TARGET_HIGH -> 1.0
        sets <= FLAG_HIGH -> 1 - (sets - TARGET_HIGH) / (FLAG_HIGH - TARGET_HIGH) * (1 - HIGH_EDGE)
        else -> max(
            VERY_HIGH_FLOOR,
            HIGH_EDGE - (sets - FLAG_HIGH) / (VERY_HIGH - FLAG_HIGH) * (HIGH_EDGE - VERY_HIGH_FLOOR)
        )
    }

    // 3.2 Frequency: at least twice a week for groups with moderate volume.
    private fun frequency(): Criterion? {
        val checked = MuscleGroup.majors.filter { plan.weekly(it) >= FLAG_LOW }
        if (checked.isEmpty()) return null
        val scores = checked.associateWith { frequencyScore(plan.frequency(it)) }
        val low = checked.filter { plan.frequency(it) < MIN_FREQUENCY - EPSILON }
            .sortedWith(compareBy<MuscleGroup> { plan.frequency(it) }.thenByDescending { plan.weekly(it) })
        val advice = low.take(MAX_ADVICE).map { group ->
            Advice.SpreadAcrossDays(
                group,
                round1(plan.frequency(group)),
                plan.weekly(group).roundToInt(),
                plan.dayFor(group, true)
            )
        }
        return criterion(
            CriterionId.FREQUENCY,
            weightedMean(scores),
            Finding.Frequency(
                low.map { GroupFrequency(it, round1(plan.frequency(it)), round1(plan.weekly(it))) },
                checked.size
            ),
            advice,
            Reference.SCHOENFELD_2016_FREQUENCY,
            Reference.SCHOENFELD_2019_FREQUENCY,
            Reference.PELLAND_2024,
        )
    }

    /** 1 from twice a week; 0.4 at once a week (Schoenfeld 2016: 2+ times beat once), 0 when never. */
    private fun frequencyScore(perWeek: Double): Double = when {
        perWeek >= MIN_FREQUENCY - EPSILON -> 1.0
        perWeek >= 1.0 -> ONCE_A_WEEK + (1 - ONCE_A_WEEK) * (perWeek - 1)
        else -> ONCE_A_WEEK * perWeek
    }

    // 3.3 Coverage: every major group gets some work.
    private fun coverage(): Criterion {
        val missing = MuscleGroup.majors.filter { plan.weekly(it) < COVERED_SETS }
        val weight = { g: MuscleGroup -> if (g.large) 2.0 else 1.0 }
        val total = MuscleGroup.majors.sumOf(weight)
        val score = (total - missing.sumOf(weight)) / total
        val advice = missing.sortedByDescending(weight).map { group ->
            Advice.Cover(group, RoutineScorer.SUGGESTED_EXERCISES[group].orEmpty().take(2), plan.dayFor(group, true))
        }
        return criterion(
            CriterionId.COVERAGE,
            score,
            Finding.Coverage(missing),
            advice,
            Reference.RATAMESS_2009_ACSM,
            Reference.SCHOENFELD_2017_VOLUME,
        )
    }

    // 3.4 Balance: push:pull and quads:posterior chain.
    private fun balance(): Criterion? {
        val pushPull = SideRatio(plan.weeklySetsWhere { it.push }, plan.weeklySetsWhere { it.pull })
        val lower = SideRatio(plan.weeklySetsWhere { it.quadDominant }, plan.weeklySetsWhere { it.hipDominant })
        val parts = buildList {
            ratioScore(pushPull, PUSH_PULL_LOW, PUSH_PULL_HIGH)?.let(::add)
            ratioScore(lower, QUADS_LOW, QUADS_HIGH)?.let(::add)
        }
        if (parts.isEmpty()) return null
        val advice = buildList {
            rebalance(
                pushPull,
                PUSH_PULL_LOW,
                PUSH_PULL_HIGH,
                BalanceSide.PUSH,
                BalanceSide.PULL,
                MuscleGroup.CHEST,
                MuscleGroup.BACK
            )
                ?.let(::add)
            rebalance(
                lower,
                QUADS_LOW,
                QUADS_HIGH,
                BalanceSide.QUADS,
                BalanceSide.POSTERIOR,
                MuscleGroup.QUADS,
                MuscleGroup.HAMSTRINGS
            )
                ?.let(::add)
        }
        return criterion(
            CriterionId.BALANCE,
            parts.average(),
            Finding.Balance(pushPull.takeIf { it.first + it.second > 0 }, lower.takeIf { it.first + it.second > 0 }),
            advice,
            Reference.RATAMESS_2009_ACSM,
            Reference.COOMBS_2002,
        )
    }

    /** 1 inside [low, high]; falls to 0 at three times outside the band; null when neither side is trained. */
    private fun ratioScore(sides: SideRatio, low: Double, high: Double): Double? {
        if (sides.first + sides.second <= 0.0) return null
        val ratio = sides.ratio ?: return 0.0
        if (ratio <= 0.0) return 0.0
        val outside = when {
            ratio > high -> ln(ratio / high)
            ratio < low -> ln(low / ratio)
            else -> 0.0
        }
        return (1 - outside / ln(BALANCE_SPAN)).coerceIn(0.0, 1.0)
    }

    @Suppress("LongParameterList") // Two sides, their bands and the groups that pick the day.
    private fun rebalance(
        sides: SideRatio,
        low: Double,
        high: Double,
        firstSide: BalanceSide,
        secondSide: BalanceSide,
        firstGroup: MuscleGroup,
        secondGroup: MuscleGroup,
    ): Advice? {
        if (sides.first + sides.second <= 0.0) return null
        val ratio = sides.ratio
        val (weaker, group) = when {
            ratio == null || ratio > high -> secondSide to secondGroup
            ratio < low -> firstSide to firstGroup
            else -> return null
        }
        return Advice.Rebalance(
            weaker,
            round1(ratio ?: sides.first),
            RoutineScorer.BALANCE_EXERCISES.getValue(weaker).take(2),
            plan.dayFor(group, false),
        )
    }

    // 3.5 Rep ranges and loading: some heavy compound work, not everything in one zone.
    private fun repRanges(): Criterion? {
        val zoned = plan.slots.filter { it.resistance && it.zone != null }
        val totalSets = zoned.sumOf { it.sets }
        if (totalSets == 0) return null
        val shares = RepZone.entries.associateWith { zone ->
            zoned.filter { it.zone == zone }.sumOf { it.sets }.toDouble() / totalSets
        }
        val used = shares.filterValues { it >= ZONE_SHARE }.keys
        val variety = when (used.size) {
            1 -> ONE_ZONE
            2 -> TWO_ZONES
            else -> 1.0
        }
        val heavyCompound = zoned.any { it.compound && it.zone == RepZone.HEAVY }
        val score = if (focus == RoutineFocus.STRENGTH) {
            STRENGTH_VARIETY * variety + (1 - STRENGTH_VARIETY) * (if (heavyCompound) 1.0 else 0.0)
        } else {
            GENERAL_VARIETY * variety + (1 - GENERAL_VARIETY) * (if (heavyCompound) 1.0 else NO_HEAVY_GENERAL)
        }
        val advice = buildList {
            if (!heavyCompound) add(Advice.AddHeavyCompound(RoutineScorer.HEAVY_COMPOUNDS.take(2)))
            if (used.size == 1) {
                val only = used.first()
                add(Advice.AddRepZone(only, if (only == RepZone.MODERATE) RepZone.HEAVY else RepZone.MODERATE))
            }
        }
        return criterion(
            CriterionId.REP_RANGES,
            score,
            Finding.RepRanges(shares.mapValues { (_, v) -> (v * 100).roundToInt() / 100.0 }, heavyCompound),
            advice,
            Reference.SCHOENFELD_2017_LOAD,
            Reference.SCHOENFELD_2021_CONTINUUM,
        )
    }

    // 3.6 Recovery: the same group trained hard on back-to-back days.
    private fun recovery(): Criterion? {
        if (plan.days.size < 2) return null
        val conflicts = plan.consecutivePairs.flatMap { (a, b) ->
            RECOVERY_GROUPS.filter { g -> (a.direct[g] ?: 0.0) >= HARD_SETS && (b.direct[g] ?: 0.0) >= HARD_SETS }
                .map { RecoveryConflict(it, a.name, b.name) }
        }
        return criterion(
            CriterionId.RECOVERY,
            1 - conflicts.size * CONFLICT_COST,
            Finding.Recovery(conflicts),
            conflicts.map { Advice.SeparateDays(it) },
            Reference.MACDOUGALL_1995,
            Reference.SCHOENFELD_2016_FREQUENCY,
        )
    }

    // 3.7 Progression: share of resistance slots with a progression rule.
    private fun progression(): Criterion? {
        val slots = plan.slots.filter { it.resistance && it.zone != null }
        if (slots.isEmpty()) return null
        val withRule = slots.count { it.slot.progression !is ProgressionRule.None }
        val share = withRule.toDouble() / slots.size
        val advice = if (withRule < slots.size) {
            listOf(
                Advice.AddProgressionRules(slots.size - withRule, slots.size)
            )
        } else {
            emptyList()
        }
        return criterion(
            CriterionId.PROGRESSION,
            share / FULL_PROGRESSION_SHARE,
            Finding.Progression(withRule, slots.size),
            advice,
            Reference.KRAEMER_2004,
            Reference.RATAMESS_2009_ACSM,
        )
    }

    // 3.8 Session length: estimated minutes per day; flag over 90.
    private fun sessionLength(): Criterion? {
        if (plan.days.isEmpty()) return null
        val longest = plan.days.maxBy { it.minutes }
        val score = plan.days.map { lengthScore(it.minutes) }.average()
        val advice = plan.days.filter { it.minutes > LONG_SESSION }.sortedByDescending { it.minutes }
            .map { Advice.ShortenSession(it.name, it.minutes) }
        return criterion(
            CriterionId.SESSION_LENGTH,
            score,
            Finding.SessionLength(longest.name, longest.minutes, plan.days.map { it.minutes }.average().roundToInt()),
            advice,
        )
    }

    private fun lengthScore(minutes: Int): Double = when {
        minutes <= COMFORTABLE_SESSION -> 1.0
        minutes <= LONG_SESSION -> 1 - (minutes - COMFORTABLE_SESSION).toDouble() / (LONG_SESSION - COMFORTABLE_SESSION) * (1 - LONG_EDGE)
        else -> max(
            0.0,
            LONG_EDGE - (minutes - LONG_SESSION).toDouble() / (TOO_LONG_SESSION - LONG_SESSION) * LONG_EDGE
        )
    }

    companion object {
        const val TARGET_LOW = 10.0
        const val TARGET_HIGH = 20.0
        const val STRENGTH_LOW = 6.0
        const val FLAG_LOW = 6.0
        const val FLAG_HIGH = 25.0
        const val MIN_FREQUENCY = 2.0
        const val COVERED_SETS = 1.0
        const val HARD_SETS = 4.0

        private const val SMALL_WEIGHT = 0.5
        private const val STRENGTH_SMALL_WEIGHT = 0.25
        private const val STRENGTH_BAND_FLOOR = 0.85
        private const val HIGH_EDGE = 0.9
        private const val VERY_HIGH_FLOOR = 0.6
        private const val VERY_HIGH = 40.0
        private const val MIN_ADD = 2
        private const val MAX_ADD_MIN = 4
        private const val MAX_ADD = 6
        private const val MAX_ADVICE = 3
        private const val EPSILON = 1e-6
        private const val ONCE_A_WEEK = 0.4
        private const val PUSH_PULL_LOW = 2.0 / 3.0
        private const val PUSH_PULL_HIGH = 1.5
        private const val QUADS_LOW = 0.5
        private const val QUADS_HIGH = 2.0
        private const val BALANCE_SPAN = 3.0
        private const val ZONE_SHARE = 0.1
        private const val ONE_ZONE = 0.5
        private const val TWO_ZONES = 0.85
        private const val STRENGTH_VARIETY = 0.4
        private const val GENERAL_VARIETY = 0.6
        private const val NO_HEAVY_GENERAL = 0.6
        private const val CONFLICT_COST = 0.2
        private const val FULL_PROGRESSION_SHARE = 0.8
        private const val COMFORTABLE_SESSION = 75
        private const val LONG_SESSION = 90
        private const val TOO_LONG_SESSION = 150
        private const val LONG_EDGE = 0.8

        /** Abs and calves recover fast and are often trained daily; they are left out of recovery. */
        private val RECOVERY_GROUPS = MuscleGroup.majors - setOf(MuscleGroup.ABS, MuscleGroup.CALVES)
    }
}
