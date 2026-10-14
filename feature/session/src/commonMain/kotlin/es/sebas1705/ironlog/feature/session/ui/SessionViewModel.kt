package es.sebas1705.ironlog.feature.session.ui

import es.sebas1705.ironlog.core.common.mvi.MVIBaseViewModel
import es.sebas1705.ironlog.core.datastore.SettingsRepository
import es.sebas1705.ironlog.data.models.LoadType
import es.sebas1705.ironlog.data.models.Prescription
import es.sebas1705.ironlog.data.models.RoutineSlot
import es.sebas1705.ironlog.data.models.SessionExercise
import es.sebas1705.ironlog.data.models.SetLog
import es.sebas1705.ironlog.data.models.SlotTarget
import es.sebas1705.ironlog.data.models.TrackingType
import es.sebas1705.ironlog.data.models.WorkoutSession
import es.sebas1705.ironlog.data.repository.SessionRepository
import es.sebas1705.ironlog.feature.session.load.Prefill
import es.sebas1705.ironlog.feature.session.load.toInput
import es.sebas1705.ironlog.feature.session.platform.LiveSessionIndicator
import es.sebas1705.ironlog.feature.session.timer.RestTimerController
import es.sebas1705.ironlog.feature.session.timer.TimerSnapshot
import kotlinx.coroutines.Job

/**
 * The session screen (spec §5). Everything it shows is the database (`observeSession`) or the persisted
 * timer, so it survives rotation and process death; the ViewModel only keeps caches and dialog state.
 *
 * Two modes, derived from the session when it is first loaded (the `SessionRoute(sessionId)` contract
 * stays as it is): a **live** session (in progress, started less than [LOG_MODE_AFTER_MS] ago) runs the
 * rest timer, notification and keep-screen-on and ends with "Finish"; a session already saved or started
 * longer ago is in **log mode** ([SessionState.logMode]): the same editor without any of that, ending
 * with "Save", which keeps the saved end time (or sets one for a stale session) and shows the summary.
 */
@Suppress("TooManyFunctions", "LongParameterList") // One handler per session operation.
class SessionViewModel(
    private val sessionId: String,
    private val sessions: SessionRepository,
    private val catalog: SessionCatalog,
    private val settings: SettingsRepository,
    private val timer: RestTimerController,
    private val indicator: LiveSessionIndicator,
    private val now: () -> Long = ::systemNow,
) : MVIBaseViewModel<SessionState, SessionIntent>(
    SessionState(sessionId = sessionId, settings = settings.settings.value)
) {

    /** Exercises whose prescribed rows were already created (or are being created) in this process. */
    private val seeded = mutableSetOf<String>()
    private var language = DEFAULT_LANGUAGE
    private var indicatorShown = false
    private var summaryRequested = false
    private var leaving = false
    private var searchJob: Job? = null

    /** Log mode, decided on the first load (null until then). */
    private var logMode: Boolean? = null

    init {
        execute { settings.settings.collect { s -> updateState { it.copy(settings = s) } } }
        execute {
            timer.state.collect { t ->
                val mine = if (t.sessionId == sessionId && logMode != true) t else TimerSnapshot()
                updateState { it.copy(timer = mine) }
            }
        }
        execute { sessions.observeSession(sessionId).collect { onSession(it) } }
    }

    override fun intentHandler(intent: SessionIntent): Job = execute {
        when (intent) {
            is SessionIntent.SetLanguage -> {
                language = intent.language
                syncLabel()
            }
            is SessionIntent.ToggleSet -> toggleSet(intent.setId)
            is SessionIntent.StartTimedSet -> startTimedSet(intent.setId)
            SessionIntent.StopTimedSet -> stopTimedSet()
            is SessionIntent.EditSet -> findSet(intent.setId)?.let { (card, set) ->
                updateState { it.copy(editor = SetEditor(card.id, set.id, set.toInput())) }
            }
            is SessionIntent.ChangeDraft -> updateState { it.copy(editor = it.editor?.copy(draft = intent.draft)) }
            SessionIntent.SaveDraft -> currentState.editor?.let { editor ->
                updateState { it.copy(editor = null) }
                sessions.updateSet(editor.setId, editor.draft)
            }
            SessionIntent.DeleteEditedSet -> currentState.editor?.let { editor ->
                updateState { it.copy(editor = null) }
                sessions.deleteSet(editor.setId)
            }
            SessionIntent.CloseEditor -> updateState { it.copy(editor = null) }
            is SessionIntent.AddSet -> addSet(intent.cardId)
            is SessionIntent.ChooseVariant -> chooseVariant(intent.cardId, intent.variantId)
            is SessionIntent.RemoveExercise -> sessions.removeExercise(intent.cardId)
            is SessionIntent.MoveExercise -> moveExercise(intent.cardId, intent.delta)
            else -> handleSessionIntent(intent)
        }
    }

    private suspend fun handleSessionIntent(intent: SessionIntent) {
        when (intent) {
            SessionIntent.OpenAddExercise -> {
                updateState { it.copy(dialog = SessionDialog.AddExercise, picker = ExercisePicker()) }
                search("")
            }
            is SessionIntent.SearchExercises -> search(intent.query)
            is SessionIntent.AddExercise -> addExercise(intent.variantId)
            is SessionIntent.AdjustRest -> timer.adjustRest(intent.deltaSeconds)
            SessionIntent.SkipRest -> timer.skip()
            SessionIntent.RequestFinish -> if (isLogMode()) {
                save()
            } else {
                updateState { it.copy(dialog = SessionDialog.ConfirmFinish) }
            }
            SessionIntent.ConfirmFinish -> finish()
            SessionIntent.Save -> save()
            SessionIntent.RequestDiscard -> {
                val dialog = if (isLogMode()) SessionDialog.ConfirmDelete else SessionDialog.ConfirmDiscard
                updateState { it.copy(dialog = dialog) }
            }
            SessionIntent.ConfirmDiscard -> discard()
            SessionIntent.DismissDialog -> {
                searchJob?.cancel()
                updateState { it.copy(dialog = null) }
            }
            else -> Unit
        }
    }

    // region Session stream

    private suspend fun onSession(session: WorkoutSession?) {
        if (session == null) {
            updateState { it.copy(loading = false, missing = true) }
            return
        }
        val log = logMode ?: (!session.inProgress || session.startedAt < now() - LOG_MODE_AFTER_MS).also {
            logMode = it
        }
        catalog.sessionStartedAt = session.startedAt
        val slots = catalog.slots(session.routineId)
        val cards = session.exercises.sortedBy { it.position }.map { buildCard(it, slots[it.slotId]) }
        updateState {
            it.copy(
                loading = false,
                missing = false,
                dayName = session.dayName,
                startedAt = session.startedAt,
                endedAt = session.endedAt,
                logMode = log,
                timer = if (log) TimerSnapshot() else it.timer,
                cards = cards,
            )
        }
        if (log) {
            // Logging after the fact: prescribed rows are prefilled until the user saves.
            if (!leaving) seedMissingRows(cards)
        } else if (session.inProgress) {
            if (!indicatorShown && !leaving) {
                indicatorShown = true
                indicator.show(sessionId)
            }
            if (!leaving) seedMissingRows(cards)
            syncLabel()
        } else if (!summaryRequested) {
            summaryRequested = true
            if (timer.state.value.sessionId == sessionId) timer.clear()
            val summary = buildSummary(session, slots, cards)
            updateState { it.copy(summary = summary) }
        }
    }

    private suspend fun buildCard(exercise: SessionExercise, slot: RoutineSlot?): ExerciseCard {
        val variant = catalog.variant(exercise.variantId)
        val base = variant?.let { catalog.exercise(it.exerciseId) }
        val options = (slot?.target as? SlotTarget.Flexible)?.let { target ->
            catalog.allowedVariants(target).map { v ->
                VariantOption(v.id, ExerciseName(base?.name, v.name, v.equipment), v.loadType, catalog.lastLoad(v.id))
            }
        }.orEmpty()
        return ExerciseCard(
            id = exercise.id,
            position = exercise.position,
            variantId = exercise.variantId,
            slotId = exercise.slotId,
            name = ExerciseName(base?.name, variant?.name, variant?.equipment),
            trackingType = base?.trackingType ?: TrackingType.WEIGHT_REPS,
            loadType = variant?.loadType ?: LoadType.NONE,
            slot = slot,
            suggestion = slot?.let { catalog.suggestion(it, exercise.variantId) },
            sets = exercise.sets.sortedBy { it.position },
            variantOptions = options,
        )
    }

    /** Creates the prescribed rows (not completed) of routine exercises that have none yet. */
    private fun seedMissingRows(cards: List<ExerciseCard>) {
        cards.filter { it.slot != null && it.sets.isEmpty() && it.id !in seeded }.forEach { card ->
            seeded += card.id
            execute { seedSlotRows(card.id, card.variantId, requireNotNull(card.slot)) }
        }
    }

    private suspend fun seedSlotRows(cardId: String, variantId: String, slot: RoutineSlot) {
        val variant = catalog.variant(variantId)
        val tracking = variant?.let { catalog.exercise(it.exerciseId) }?.trackingType ?: TrackingType.WEIGHT_REPS
        val rows = Prefill.slotRows(
            slot = slot,
            trackingType = tracking,
            loadType = variant?.loadType ?: LoadType.NONE,
            suggestion = catalog.suggestion(slot, variantId),
            last = catalog.lastPerformance(slot.id, variantId),
        )
        rows.forEach { sessions.logSet(cardId, it) }
    }

    private fun syncLabel() {
        val state = currentState
        if (!state.live || leaving) return
        // Also claims the timer for this session, which keeps the Android notification alive.
        val card = state.cards.firstOrNull { it.id == state.currentCardId } ?: state.cards.lastOrNull()
        timer.setLabel(sessionId, card?.name?.plain(language).orEmpty())
    }

    // endregion

    // region Sets

    private suspend fun toggleSet(setId: String) {
        val (card, set) = findSet(setId) ?: return
        val completed = !set.completed
        sessions.updateSet(set.id, set.toInput().copy(completed = completed))
        if (completed && !isLogMode()) startRestAfter(card, set.id)
    }

    private fun startRestAfter(card: ExerciseCard, setId: String) {
        val (seconds, labelCard) = restAfter(card, setId)
        timer.startRest(sessionId, labelCard.name.plain(language), seconds)
    }

    /** Rest after completing [setId] of [card] (spec §5: supersets rest after the round) and the next card. */
    private fun restAfter(card: ExerciseCard, setId: String): Pair<Int, ExerciseCard> {
        val cards = currentState.cards
        val pending = card.sets.filter { !it.completed && it.id != setId }
        val next = cards.getOrNull(cards.indexOfFirst { it.id == card.id } + 1)
        val group = card.slot?.supersetGroup
        val midRound = group != null && next != null && next.slot?.supersetGroup == group && !next.done
        val seconds = when {
            midRound -> 0
            pending.isEmpty() -> card.slot?.restAfterS ?: RoutineSlot.DEFAULT_REST_S
            else -> card.slot?.restBetweenSetsS ?: RoutineSlot.DEFAULT_REST_S
        }
        val labelCard = if ((pending.isEmpty() || midRound) && next != null) next else card
        return seconds to labelCard
    }

    private suspend fun startTimedSet(setId: String) {
        val (card, set) = findSet(setId) ?: return
        val work = set.durationS ?: (card.prescription as? Prescription.Duration)?.seconds ?: DEFAULT_WORK_S
        sessions.updateSet(set.id, set.toInput().copy(durationS = work, completed = true))
        if (isLogMode()) return // Logged after the fact: the target duration, no countdown.
        val (rest, _) = restAfter(card, set.id)
        timer.startTimed(sessionId, card.name.plain(language), set.id, work, rest)
    }

    private suspend fun stopTimedSet() {
        val setId = currentState.timer.setId ?: return
        val worked = timer.finishWorkNow() ?: return
        val set = findSet(setId)?.second ?: return
        if (worked <= 0) {
            timer.skip()
            sessions.updateSet(setId, set.toInput().copy(completed = false))
        } else {
            sessions.updateSet(setId, set.toInput().copy(durationS = worked, completed = true))
        }
    }

    private suspend fun addSet(cardId: String) {
        val card = currentState.cards.firstOrNull { it.id == cardId } ?: return
        val last = catalog.lastPerformance(card.slotId, card.variantId)
        sessions.logSet(card.id, Prefill.extraRow(card.sets, card.prescription, card.trackingType, card.loadType, last))
    }

    private fun findSet(setId: String): Pair<ExerciseCard, SetLog>? = currentState.cards.firstNotNullOfOrNull { card ->
        card.sets.firstOrNull { it.id == setId }?.let { card to it }
    }

    // endregion

    // region Exercises

    /**
     * Flexible slot: switches the variant. Before any set is completed the prefilled rows are rebuilt for
     * the new variant (its own history and suggestion); afterwards the logged sets are kept.
     */
    private suspend fun chooseVariant(cardId: String, variantId: String) {
        val card = currentState.cards.firstOrNull { it.id == cardId } ?: return
        if (card.variantId == variantId) return
        seeded += cardId
        sessions.substituteExercise(cardId, variantId)
        if (!card.started && card.slot != null) {
            card.sets.forEach { sessions.deleteSet(it.id) }
            seedSlotRows(cardId, variantId, card.slot)
        }
    }

    private suspend fun moveExercise(cardId: String, delta: Int) {
        val cards = currentState.cards
        val index = cards.indexOfFirst { it.id == cardId }
        if (index < 0) return
        val target = (index + delta).coerceIn(0, cards.lastIndex)
        if (target != index) sessions.moveExercise(cardId, target)
    }

    private fun search(query: String) {
        updateState { it.copy(picker = it.picker.copy(query = query)) }
        searchJob?.cancel()
        searchJob = execute {
            catalog.search(query, language).collect { results ->
                updateState { it.copy(picker = it.picker.copy(results = results)) }
            }
        }
    }

    /** Adds an exercise at the end with a few rows prefilled from its last performance. */
    private suspend fun addExercise(variantId: String) {
        searchJob?.cancel()
        updateState { it.copy(dialog = null) }
        val id = sessions.addExercise(sessionId, variantId)
        seeded += id
        val variant = catalog.variant(variantId)
        val tracking = variant?.let { catalog.exercise(it.exerciseId) }?.trackingType ?: TrackingType.WEIGHT_REPS
        val loadType = variant?.loadType ?: LoadType.NONE
        val last = catalog.lastPerformance(null, variantId)
        val rows = mutableListOf<SetLog>()
        repeat(AD_HOC_SETS) {
            val input = Prefill.extraRow(rows, null, tracking, loadType, last)
            val setId = sessions.logSet(id, input)
            rows += SetLog(setId, id, it, input.kind, input.reps, input.loadGrams, input.loadDetail, input.durationS, input.distanceM)
        }
    }

    // endregion

    // region Finish

    /** Drops the rows never completed, ends the session and shows the summary. */
    private suspend fun finish() {
        if (leaving) return
        leaving = true
        updateState { it.copy(dialog = null, busy = true, finishedHere = true) }
        timer.clear()
        indicator.hide()
        dropUncompletedSets()
        sessions.finish(sessionId)
        updateState { it.copy(busy = false) }
    }

    /**
     * Log mode "Save": drops the rows never completed and shows the summary. A saved session keeps its end
     * time (the times are edited from Progress); one never ended gets the default duration. Nothing live
     * (timer, notification) is touched: another session may be running.
     */
    private suspend fun save() {
        if (leaving) return
        leaving = true
        updateState { it.copy(dialog = null, busy = true) }
        dropUncompletedSets()
        val session = sessions.getSession(sessionId)
        if (session == null) {
            updateState { it.copy(busy = false, missing = true) }
            return
        }
        if (session.inProgress) sessions.finish(sessionId, endedAt = session.startedAt + DEFAULT_LOG_DURATION_MS)
        val saved = sessions.getSession(sessionId) ?: session
        summaryRequested = true
        val summary = buildSummary(saved, catalog.slots(saved.routineId), currentState.cards)
        updateState { it.copy(busy = false, endedAt = saved.endedAt, summary = summary) }
    }

    private suspend fun dropUncompletedSets() {
        sessions.getSession(sessionId)?.exercises.orEmpty()
            .flatMap { it.sets }
            .filterNot { it.completed }
            .forEach { sessions.deleteSet(it.id) }
    }

    /** Discards a live session, or deletes a session in log mode (after the confirmation). */
    private suspend fun discard() {
        if (leaving) return
        leaving = true
        updateState { it.copy(dialog = null, discarded = true) }
        if (!isLogMode()) {
            timer.clear()
            indicator.hide()
        }
        sessions.deleteSession(sessionId)
    }

    private fun isLogMode(): Boolean = logMode == true

    private suspend fun buildSummary(session: WorkoutSession, slots: Map<String, RoutineSlot>, cards: List<ExerciseCard>): FinishSummary {
        val names = cards.associate { it.id to it.name }
        return SummaryBuilder.build(
            session = session,
            slots = slots,
            names = names,
            loadTypes = cards.associate { it.id to it.loadType },
            previous = { variantId -> catalog.previousHistory(variantId, session.id) },
            increase = { slot, variantId -> catalog.earnedIncrease(slot, variantId, session.id) },
        )
    }

    // endregion

    companion object {
        /** An in-progress session started longer ago than this opens in log mode (it was left unfinished). */
        const val LOG_MODE_AFTER_MS = 12 * 60 * 60 * 1_000L

        /** End time given to a session saved in log mode that was never ended. */
        const val DEFAULT_LOG_DURATION_MS = 60 * 60 * 1_000L

        private const val DEFAULT_LANGUAGE = "en"
        private const val DEFAULT_WORK_S = 30
        private const val AD_HOC_SETS = 3
    }
}
