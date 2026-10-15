package es.sebas1705.haltera.domain.usecases.naming

import es.sebas1705.haltera.data.models.Equipment
import es.sebas1705.haltera.data.models.ExerciseVariant
import es.sebas1705.haltera.data.models.LoadType
import es.sebas1705.haltera.data.models.LocalizedText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExerciseNamingTest {
    private val latPulldown = LocalizedText("Lat Pulldown", "Jalón al pecho")
    private val wideGrip = LocalizedText("Wide grip", "Agarre ancho")
    private val equipmentText: (Equipment) -> String = { it.name.lowercase() }

    @Test
    fun titleIsTheExerciseNameEvenWhenTheVariantHasItsOwnName() {
        assertEquals("Lat Pulldown", ExerciseNaming.title(latPulldown, wideGrip, "en"))
        assertEquals("Jalón al pecho", ExerciseNaming.title(latPulldown, wideGrip, "es"))
    }

    @Test
    fun titleFallsBackToTheVariantNameOnlyWithoutAnExercise() {
        assertEquals("Wide grip", ExerciseNaming.title(null, wideGrip, "en"))
        assertNull(ExerciseNaming.title(null, null, "en"))
    }

    @Test
    fun labelIsTheVariantNameElseTheEquipment() {
        val named = ExerciseVariant(
            "lat-pulldown.wide-grip",
            "lat-pulldown",
            Equipment.CABLE,
            LoadType.MACHINE,
            wideGrip
        )
        val plain = ExerciseVariant("bench-press.barbell", "bench-press", Equipment.BARBELL, LoadType.BARBELL)
        assertEquals(VariantLabel.Named(wideGrip), ExerciseNaming.variantLabel(named))
        assertEquals(VariantLabel.OfEquipment(Equipment.BARBELL), ExerciseNaming.variantLabel(plain))
        assertEquals("Agarre ancho", ExerciseNaming.variantLabel(named).text("es", equipmentText))
        assertEquals("barbell", ExerciseNaming.variantLabel(plain).text("en", equipmentText))
    }

    @Test
    fun siblingVariantsGetDistinctLabels() {
        val labels = listOf(
            wideGrip,
            LocalizedText("Close grip", "Agarre cerrado"),
            LocalizedText("V-bar", "Con barra en V"),
        ).map { ExerciseNaming.variantLabel(it, Equipment.CABLE).text("en", equipmentText) }
        assertEquals(listOf("Wide grip", "Close grip", "V-bar"), labels)
    }
}
