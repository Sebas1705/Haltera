package es.sebas1705.haltera.feature.session.ui

import es.sebas1705.haltera.core.common.mvi.MVIBaseViewModel
import es.sebas1705.haltera.core.datastore.LoadStepStore
import es.sebas1705.haltera.core.datastore.SettingsRepository
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.Prescription
import es.sebas1705.haltera.data.models.RoutineSlot
import es.sebas1705.haltera.data.models.SessionExercise
import es.sebas1705.haltera.data.models.SetLog
import es.sebas1705.haltera.data.models.SlotTarget
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.data.models.WorkoutSession
import es.sebas1705.haltera.data.repository.SessionRepository
import es.sebas1705.haltera.feature.session.load.LoadSteps
import es.sebas1705.haltera.feature.session.load.Prefill
import es.sebas1705.haltera.feature.session.load.WarmupRounding
import es.sebas1705.haltera.feature.session.load.toInput
import es.sebas1705.haltera.feature.session.platform.LiveSessionIndicator
import es.sebas1705.haltera.feature.session.timer.RestTimerController
import es.sebas1705.haltera.feature.session.timer.TimerSnapshot
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
    private val loadSteps: LoadStepStore,
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

    /** Editing a session saved earlier: "Save" goes back instead of showing the summary again. */
    private var editingSaved = false

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
                val editor = SetEditor(
                    cardId = card.id,
                    setId = set.id,
                    draft = Prefill.shownInEditor(
                        set.toInput(),
                        card.trackingType,
                        card.loadType,
                        currentState.settings.barWeightGrams,
                    ),
                    weightStep = loadSteps.step(card.variantId, plates = false),
                    plateStep = loadSteps.step(card.variantId, plates = true),
                )
                updateState { it.copy(editor = editor) }
            }
            is SessionIntent.ChangeDraft -> updateState { it.copy(editor = it.editor?.copy(draft = intent.draft)) }
            SessionIntent.SaveDraft -> currentState.editor?.let { saveDraft(it) }
            is SessionIntent.SetLoadStep -> setLoadStep(intent.step, intent.plates)
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
                if (nothingLogged()) emptyLog() else save()
            } else {
                updateState { it.copy(dialog = SessionDialog.ConfirmFinish) }
            }
            SessionIntent.ConfirmFinish -> finish()
            SessionIntent.Save -> if (nothingLogged()) emptyLog() else save()
            SessionIntent.RequestLeave -> if (nothingLogged()) emptyLog() else updateState { it.copy(closed = true) }
            SessionIntent.RequestDiscard -> {
                val dialog = if (isLogMode()) SessionDialog.ConfirmDelete else SessionDialog.ConfirmDiscard
                updateState { it.copy(dialog = dialog) }
            }
            SessionIntent.ConfirmDiscard -> discard()
            SessionIntent.DismissDialog -> {
                searchJob?.cancel()
                updateState { it.copy(dialog = null) }
            }
            is SessionIntent.OpenStatistics -> openLink(intent.cardId) { card, exerciseId ->
                ExerciseLink.Statistics(exerciseId, card.variantId)
            }
            is SessionIntent.OpenDescription -> openLink(intent.cardId) { _, exerciseId ->
                ExerciseLink.Description(exerciseId)
            }
            SessionIntent.LinkOpened -> updateState { it.copy(link = null) }
            else -> Unit
        }
    }

    /**
     * Asks the screen to open another tab's screen for a card's exercise. Only navigation: the session, its sets,
     * the rest timer and the notification are left as they are, and back returns here.
     */
    private fun openLink(cardId: String, link: (ExerciseCard, String) -> ExerciseLink) {
        val card = currentState.cards.firstOrNull { it.id == cardId } ?: return
        val exerciseId = card.exerciseId ?: return
        updateState { it.copy(link = link(card, exerciseId)) }
    }

    // region Session stream

    private suspend fun onSession(session: WorkoutSession?) {
        if (session == null) {
            updateState { it.copy(loading = false, missing = true) }
            return
        }
        val log = logMode ?: (!session.inProgress || session.startedAt < now() - LOG_MODE_AFTER_MS).also {
            logMode = it
            editingSaved = isEditOfSaved(session)
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
            exerciseId = variant?.exerciseId,
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
        val loadType = variant?.loadType ?: LoadType.NONE
        val rows = Prefill.slotRows(
            slot = slot,
            trackingType = tracking,
            loadType = loadType,
            suggestion = catalog.suggestion(slot, variantId),
            last = catalog.lastPerformance(variantId),
            warmupRounding = warmupRounding(variantId, loadType),
        )
        rows.forEach { sessions.logSet(cardId, it) }
    }

    private fun warmupRounding(variantId: String, loadType: LoadType): WarmupRounding {
        val user = settings.settings.value
        return WarmupRounding(
            stepGrams = LoadSteps.stepGrams(
                loadType,
                user.unit,
                user.availablePlatesGrams,
                override = loadSteps.step(variantId, plates = false),
            ),
            barGrams = user.barWeightGrams,
            plates = user.availablePlatesGrams,
        )
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

    /**
     * Saves the edited set. A changed set counts as edited by the user, and the pending working rows below it
     * that still follow it take its new load and reps ([Prefill.followers]).
     */
    private suspend fun saveDraft(editor: SetEditor) {
        val (card, original) = findSet(editor.setId) ?: run {
            updateState { it.copy(editor = null) }
            return
        }
        val changed = original.toInput() != editor.draft
        updateState {
            it.copy(editor = null, editedSetIds = if (changed) it.editedSetIds + editor.setId else it.editedSetIds)
        }
        sessions.updateSet(editor.setId, editor.draft)
        val edited = currentState.editedSetIds
        val warmups = Prefill.warmupFollowers(
            card.sets,
            original,
            editor.draft,
            edited,
            card.loadType,
            warmupRounding(card.variantId, card.loadType),
        )
        (Prefill.followers(card.sets, original, editor.draft, edited) + warmups).forEach { (id, input) ->
            sessions.updateSet(id, input)
        }
    }

    /** Remembers the stepper increment of the edited card's variant (null: back to the default). */
    private fun setLoadStep(step: Long?, plates: Boolean) {
        val editor = currentState.editor ?: return
        val card = currentState.cards.firstOrNull { it.id == editor.cardId } ?: return
        val value = step?.takeIf { it > 0 }
        loadSteps.setStep(card.variantId, plates, value)
        updateState { state ->
            state.copy(
                editor = state.editor?.let { if (plates) it.copy(plateStep = value) else it.copy(weightStep = value) }
            )
        }
    }

    private suspend fun addSet(cardId: String) {
        val card = currentState.cards.firstOrNull { it.id == cardId } ?: return
        val last = catalog.lastPerformance(card.variantId)
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
        val last = catalog.lastPerformance(variantId)
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
        sessions.dropIncomplete(sessionId)
        sessions.finish(sessionId)
        updateState { it.copy(busy = false) }
    }

    /**
     * Log mode "Save": drops the rows never completed (and the exercises left empty) and shows the summary,
     * or goes back when an already saved session was edited. A saved session keeps its end time (the times
     * are edited from Progress); one never ended gets the default duration. Nothing live (timer,
     * notification) is touched: another session may be running.
     */
    private suspend fun save() {
        if (leaving) return
        leaving = true
        updateState { it.copy(dialog = null, busy = true) }
        sessions.dropIncomplete(sessionId)
        val session = sessions.getSession(sessionId)
        if (session == null) {
            updateState { it.copy(busy = false, missing = true) }
            return
        }
        if (session.inProgress) sessions.finish(sessionId, endedAt = session.startedAt + DEFAULT_LOG_DURATION_MS)
        val saved = sessions.getSession(sessionId) ?: session
        summaryRequested = true
        if (editingSaved) {
            updateState { it.copy(busy = false, endedAt = saved.endedAt, closed = true) }
            return
        }
        val summary = buildSummary(saved, catalog.slots(saved.routineId), currentState.cards)
        updateState { it.copy(busy = false, endedAt = saved.endedAt, summary = summary) }
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

    /** No completed set on any exercise: a session logged after the fact holds nothing yet. */
    private fun nothingLogged(): Boolean = currentState.cards.none { card -> card.sets.any { it.completed } }

    /** Log mode with nothing logged: an empty session is not kept as a logged one (QA alpha.5). */
    private fun emptyLog() = updateState { it.copy(dialog = SessionDialog.EmptyLog) }

    /**
     * A saved session that already holds completed sets is being edited ("Edit sets"); a session logged after
     * the fact is saved empty on creation and filled here.
     */
    private fun isEditOfSaved(session: WorkoutSession): Boolean =
        !session.inProgress && session.exercises.any { e -> e.sets.any { it.completed } }

    private suspend fun buildSummary(session: WorkoutSession, slots: Map<String, RoutineSlot>, cards: List<ExerciseCard>): FinishSummary {
        return SummaryBuilder.build(
            session = session,
            slots = slots,
            cards = cards.associate { it.id to SummaryBuilder.Card(it.name, it.trackingType, it.loadType) },
            dayOf = catalog::localDay,
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
