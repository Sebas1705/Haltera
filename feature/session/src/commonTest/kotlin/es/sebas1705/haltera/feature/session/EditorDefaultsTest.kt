package es.sebas1705.haltera.feature.session

import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.SetInput
import es.sebas1705.haltera.data.models.TrackingType
import es.sebas1705.haltera.feature.session.load.LoadDetail
import es.sebas1705.haltera.feature.session.load.Prefill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The set editor's draft holds the load it shows, so saving never drops a displayed default (QA alpha.5). */
class EditorDefaultsTest {
    private val bar = 20_000L
    private val empty = SetInput(reps = 8, completed = false)

    private fun shown(input: SetInput, loadType: LoadType, tracking: TrackingType = TrackingType.WEIGHT_REPS) =
        Prefill.shownInEditor(input, tracking, loadType, bar)

    @Test
    fun `a barbell set without a load shows and keeps the bar`() {
        val draft = shown(empty, LoadType.BARBELL)
        assertEquals(bar, draft.loadGrams)
        assertEquals(LoadDetail.Barbell(bar), LoadDetail.decode(draft.loadDetail))
        assertEquals(8, draft.reps)
    }

    @Test
    fun `a band shows level 1 and a machine by stack position plate 1`() {
        val band = shown(empty, LoadType.BAND)
        assertEquals(1L, band.loadGrams)
        assertEquals(LoadDetail.Band, LoadDetail.decode(band.loadDetail))
        val plate = shown(empty.copy(loadDetail = LoadDetail.encode(LoadDetail.MachinePlate)), LoadType.MACHINE)
        assertEquals(1L, plate.loadGrams)
    }

    @Test
    fun `zero shown for dumbbells, machine weight and bodyweight is no load`() {
        assertNull(shown(empty, LoadType.DUMBBELL).loadGrams)
        assertNull(shown(empty, LoadType.MACHINE).loadGrams)
        assertNull(shown(empty, LoadType.BODYWEIGHT, TrackingType.BODYWEIGHT_REPS).loadGrams)
    }

    @Test
    fun `an entered or prefilled load and untracked loads are left alone`() {
        val prefilled = empty.copy(loadGrams = 60_000, loadDetail = LoadDetail.encode(LoadDetail.Barbell(15_000)))
        assertEquals(prefilled, shown(prefilled, LoadType.BARBELL))
        val dumbbell = empty.copy(loadGrams = 22_500)
        assertEquals(dumbbell, shown(dumbbell, LoadType.DUMBBELL))
        assertEquals(empty, shown(empty, LoadType.BARBELL, TrackingType.REPS))
        assertEquals(empty, shown(empty, LoadType.NONE))
    }
}
