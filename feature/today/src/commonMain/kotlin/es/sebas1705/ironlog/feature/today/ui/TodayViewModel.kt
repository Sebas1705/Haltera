package es.sebas1705.ironlog.feature.today.ui

import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.datastore.SettingsRepository
import es.sebas1705.ironlog.core.units.Numbers
import es.sebas1705.ironlog.core.units.Weights
import es.sebas1705.ironlog.data.models.DayKind
import es.sebas1705.ironlog.data.models.Equipment
import es.sebas1705.ironlog.data.models.LocalizedText
import es.sebas1705.ironlog.data.models.RoutineSlot
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.repository.BodyWeightRepository
import es.sebas1705.ironlog.data.repository.ExerciseRepository
import es.sebas1705.ironlog.data.repository.RoutineRepository
import es.sebas1705.ironlog.data.repository.SessionRepository
import es.sebas1705.ironlog.core.ui.components.PastSessionForm
import es.sebas1705.ironlog.domain.usecases.session.LogPastSession
import es.sebas1705.ironlog.domain.usecases.session.ObserveTodayPlan
import es.sebas1705.ironlog.domain.usecases.session.PastSessionTarget
import kotlinx.coroutines.flow.first
import es.sebas1705.ironlog.domain.usecases.session.TodayPlan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Today tab (spec §7): resume the session in progress, the active routine's next day (with the rest-day
 * hint and a day picker), a free session, the week strip and the latest body weight.
 */
@Suppress("LongParameterList", "TooManyFunctions") // One dependency per data source; one handler per intent.
internal class TodayViewModel(
    observeTodayPlan: ObserveTodayPlan,
    private val sessions: SessionRepository,
    private val routines: RoutineRepository,
    private val exercises: ExerciseRepository,
    private val bodyWeight: BodyWeightRepository,
    private val settings: SettingsRepository,
    private val clock: TodayClock,
    private val logPastSession: LogPastSession,
) : MVIBaseViewModel<TodayState, TodayIntent>(TodayState(unit = settings.settings.value.unit)) {

    private val effectChannel = Channel<TodayEffect>(Channel.BUFFERED)
    val effects: Flow<TodayEffect> = effectChannel.receiveAsFlow()

    /** Slot names by target, resolved once per target. */
    private val names = mutableMapOf<SlotTarget, ResolvedName>()

    init {
        val week = Week.rangeFor(clock.now(), clock.timeZone)
        updateState { it.copy(week = Week.summarize(week, emptyList())) }
        execute {
            observeTodayPlan().collectLatest { plan ->
                val ui = plan?.let { toUi(it) }
                updateState { it.copy(planLoaded = true, plan = ui) }
            }
        }
        execute {
            sessions.observeInProgress().collect { session ->
                val ui = session?.let { InProgressUi(it.id, it.startedAt, it.dayName) }
                updateState { it.copy(inProgress = ui) }
            }
        }
        execute {
            sessions.observeSessionsBetween(week.startMillis, week.endMillis).collect { list ->
                val summary = Week.summarize(week, list)
                updateState { it.copy(week = summary) }
            }
        }
        execute { bodyWeight.observeLatest().collect { entry -> updateState { it.copy(latestWeight = entry) } } }
        execute { settings.settings.collect { s -> updateState { it.copy(unit = s.unit) } } }
    }

    override fun intentHandler(intent: TodayIntent): Job = execute {
        when (intent) {
            TodayIntent.ResumeSession -> currentState.inProgress?.let {
                effectChannel.send(
                    TodayEffect.OpenSession(it.sessionId)
                )
            }
            TodayIntent.StartNextDay -> currentState.plan?.let { plan ->
                plan.next?.let { next -> start { sessions.startFromRoutineDay(plan.routineId, next.dayIndex) } }
            }
            is TodayIntent.StartDay -> currentState.plan?.let { plan ->
                updateState { it.copy(dayPickerOpen = false) }
                start { sessions.startFromRoutineDay(plan.routineId, intent.dayIndex) }
            }
            TodayIntent.StartFree -> start { sessions.startFree() }
            TodayIntent.OpenDayPicker -> updateState { it.copy(dayPickerOpen = it.plan != null) }
            TodayIntent.DismissDayPicker -> updateState { it.copy(dayPickerOpen = false) }
            is TodayIntent.ChooseDay -> chooseDay(intent.dayIndex)
            TodayIntent.OpenWeightDialog -> openWeightDialog()
            is TodayIntent.WeightInputChanged -> updateState { state ->
                state.copy(weightDialog = state.weightDialog?.copy(input = intent.text, invalid = false))
            }
            is TodayIntent.WeightDateChanged -> updateState { state ->
                state.copy(
                    weightDialog = state.weightDialog?.let {
                        it.copy(dateUtcMillis = intent.dateUtcMillis.coerceAtMost(it.maxDateUtcMillis))
                    },
                )
            }
            TodayIntent.SaveWeight -> saveWeight()
            TodayIntent.DismissWeightDialog -> updateState { it.copy(weightDialog = null) }
            TodayIntent.OpenLogPast -> openLogPast()
            TodayIntent.DismissLogPast -> updateState { it.copy(logPast = null) }
            is TodayIntent.ConfirmLogPast -> confirmLogPast(intent.form)
            TodayIntent.ErrorShown -> updateState { it.copy(error = null) }
        }
    }

    private suspend fun start(create: suspend () -> String) {
        if (currentState.starting) return
        updateState { it.copy(starting = true) }
        val result = attempt(create)
        updateState { it.copy(starting = false, error = if (result == null) TodayError.START_FAILED else it.error) }
        result?.let { effectChannel.send(TodayEffect.OpenSession(it)) }
    }

    /** Opens "Log past session" on today, preselecting the active routine's next day. */
    private suspend fun openLogPast() {
        val options = attempt { LogPastSession.options(routines.observeRoutines().first()) }.orEmpty()
        val plan = currentState.plan
        val nextIndex = options.indexOfFirst {
            it.target.routineId == plan?.routineId && it.target.dayIndex == plan.next?.dayIndex
        }
        val choice = if (nextIndex >= 0) nextIndex.toString() else LogPastUi.FREE_CHOICE
        val now = clock.now()
        val today = PastSessionTime.localDate(now, clock.timeZone)
        val form = PastSessionTime.newForm(
            today,
            now,
            clock.timeZone,
            LogPastSession.DEFAULT_DURATION_MINUTES,
            choice,
        )
        updateState { it.copy(logPast = LogPastUi(form, options, PastSessionTime.utcMillis(today))) }
    }

    private suspend fun confirmLogPast(form: PastSessionForm) {
        val dialog = currentState.logPast ?: return
        updateState { it.copy(logPast = null) }
        val target = form.choiceKey?.toIntOrNull()?.let { dialog.options.getOrNull(it)?.target }
            ?: PastSessionTarget.Free
        start {
            logPastSession(
                target,
                PastSessionTime.startMillis(form, clock.timeZone),
                PastSessionTime.durationMillis(form),
            )
        }
    }

    private suspend fun chooseDay(dayIndex: Int) {
        val plan = currentState.plan ?: return
        updateState { it.copy(dayPickerOpen = false) }
        if (attempt { routines.setNextDayIndex(plan.routineId, dayIndex) } == null) {
            updateState { it.copy(error = TodayError.SAVE_FAILED) }
        }
    }

    /** Opens "Log body weight" dated today; any earlier date can be picked. */
    private fun openWeightDialog() {
        val today = PastSessionTime.todayUtcMillis(clock.now(), clock.timeZone)
        updateState { it.copy(weightDialog = WeightDialogUi(dateUtcMillis = today, maxDateUtcMillis = today)) }
    }

    private suspend fun saveWeight() {
        val dialog = currentState.weightDialog ?: return
        val grams = parseWeight(dialog.input)?.let { Weights.toGrams(it, currentState.unit) }
        if (grams == null || grams <= 0) {
            updateState { it.copy(weightDialog = dialog.copy(invalid = true)) }
            return
        }
        val measuredAt = PastSessionTime.measuredAt(dialog.dateUtcMillis, clock.now(), clock.timeZone)
        val saved = attempt { bodyWeight.add(grams, measuredAt) }
        updateState {
            if (saved == null) it.copy(error = TodayError.SAVE_FAILED) else it.copy(weightDialog = null)
        }
    }

    private suspend fun toUi(plan: TodayPlan): PlanUi {
        val routine = plan.routine
        val next = plan.next?.let { next ->
            NextDayUi(
                dayIndex = next.dayIndex,
                dayCount = routine.days.size,
                dayName = next.day.name,
                planSaysRest = next.planSaysRest,
                exercises = next.day.slots.sortedBy { it.position }.map { slotUi(it) },
            )
        }
        return PlanUi(
            routineId = routine.id,
            routineName = routine.name,
            next = next,
            days = routine.days.sortedBy { it.position }.mapIndexed { index, day ->
                DayOptionUi(index = index, name = day.name, isRest = day.kind == DayKind.REST)
            },
        )
    }

    private suspend fun slotUi(slot: RoutineSlot): SlotUi {
        val resolved = names[slot.target] ?: resolve(slot.target).also { names[slot.target] = it }
        return SlotUi(
            slotId = slot.id,
            name = resolved.name,
            variantName = resolved.variantName,
            equipment = resolved.equipment,
            flexible = slot.target is SlotTarget.Flexible,
            sets = slot.sets,
            prescription = slot.prescription,
        )
    }

    private suspend fun resolve(target: SlotTarget): ResolvedName = when (target) {
        is SlotTarget.Fixed -> {
            // Title = the exercise; the variant's own name goes under it (ExerciseNaming).
            val variant = attempt { exercises.getVariant(target.variantId) }
            val exercise = variant?.let { attempt { exercises.getExercise(it.exerciseId) }?.name }
            ResolvedName(exercise ?: variant?.name, variant?.name, variant?.equipment)
        }
        is SlotTarget.Flexible -> ResolvedName(attempt { exercises.getExercise(target.exerciseId) }?.name, null, null)
    }

    private data class ResolvedName(val name: LocalizedText?, val variantName: LocalizedText?, val equipment: Equipment?)

    companion object {
        /** Upper bound for a body weight entry in either unit; anything above is a typo. */
        private const val MAX_BODY_WEIGHT = 1_000.0

        /** A positive decimal with a dot or a comma, up to [MAX_BODY_WEIGHT]; null otherwise. */
        fun parseWeight(text: String): Double? =
            Numbers.parse(text)?.takeIf { it > 0 && it <= MAX_BODY_WEIGHT }

        /** Runs [block]; a failure (other than cancellation) becomes null. */
        private suspend fun <T> attempt(block: suspend () -> T): T? =
            runCatching { block() }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }
}
