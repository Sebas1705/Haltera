package es.sebas1705.haltera.feature.session.load

import es.sebas1705.haltera.core.units.PlateCalculator
import es.sebas1705.haltera.core.units.WeightUnit
import es.sebas1705.haltera.core.units.Weights
import es.sebas1705.haltera.data.models.LoadType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What was entered for a load (spec §3.3 `loadDetail`), so it is shown back exactly as typed. Stored as
 * JSON in [es.sebas1705.haltera.data.models.SetLog.loadDetail]; the normalised value stays in `loadGrams`.
 */
@Serializable
sealed interface LoadDetail {
    /** Total load on a barbell of [barGrams] (plates per side are derived from the user's inventory). */
    @Serializable
    @SerialName("barbell")
    data class Barbell(val barGrams: Long) : LoadDetail

    /** Machine by stack position: `loadGrams` holds the plate number. */
    @Serializable
    @SerialName("machinePlate")
    data object MachinePlate : LoadDetail

    /** Machine in weight units. */
    @Serializable
    @SerialName("machineWeight")
    data object MachineWeight : LoadDetail

    /** Bodyweight with added (positive `loadGrams`) or assisting (negative) load. */
    @Serializable
    @SerialName("bodyweight")
    data class Bodyweight(val assisted: Boolean) : LoadDetail

    /** Band: `loadGrams` holds the level. */
    @Serializable
    @SerialName("band")
    data object Band : LoadDetail

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Lenient: unknown or broken JSON reads as null. */
        @Suppress("SwallowedException") // A broken detail only loses the display hint, never the load.
        fun decode(value: String?): LoadDetail? = value?.let {
            try {
                json.decodeFromString(serializer(), it)
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        fun encode(detail: LoadDetail?): String? = detail?.let { json.encodeToString(serializer(), it) }
    }
}

/** Whether a machine load is a stack position ("plate #7") rather than a weight. */
fun isMachinePlate(detail: String?): Boolean = LoadDetail.decode(detail) is LoadDetail.MachinePlate

/** Stepper increments per load type, in the user's unit (spec §3.2 defaults). */
object LoadSteps {
    private const val KG_DUMBBELL = 2_000L
    private const val KG_MACHINE = 5_000L
    private const val KG_BODYWEIGHT = 2_500L
    private const val KG_BARBELL = 2_500L
    private const val LB_SMALL = 5.0
    private const val LB_MACHINE = 10.0

    /** Whether the user can choose the step for this load type (band levels always move by one). */
    fun customizable(loadType: LoadType): Boolean = loadType != LoadType.BAND && loadType != LoadType.NONE

    /**
     * Grams added or removed by one tap of the load stepper (1 for plate numbers and band levels). A positive
     * [override] (the step the user chose for the variant) replaces the default where a step can be chosen.
     */
    fun stepGrams(
        loadType: LoadType,
        unit: WeightUnit,
        plates: List<Long>,
        machinePlate: Boolean = false,
        override: Long? = null,
    ): Long = override?.takeIf { it > 0 && customizable(loadType) } ?: defaultStep(loadType, unit, plates, machinePlate)

    /** The step for [loadType] when the user chose none (spec §3.2 defaults, plate logic for barbells). */
    fun defaultStep(loadType: LoadType, unit: WeightUnit, plates: List<Long>, machinePlate: Boolean = false): Long =
        when (loadType) {
            LoadType.BARBELL -> PlateCalculator.smallestIncrement(plates).takeIf { it > 0 }
                ?: if (unit == WeightUnit.KG) KG_BARBELL else Weights.lbToGrams(LB_SMALL)
            LoadType.DUMBBELL -> if (unit == WeightUnit.KG) KG_DUMBBELL else Weights.lbToGrams(LB_SMALL)
            LoadType.MACHINE -> when {
                machinePlate -> 1
                unit == WeightUnit.KG -> KG_MACHINE
                else -> Weights.lbToGrams(LB_MACHINE)
            }
            LoadType.BODYWEIGHT -> if (unit == WeightUnit.KG) KG_BODYWEIGHT else Weights.lbToGrams(LB_SMALL)
            LoadType.BAND -> 1
            LoadType.NONE -> 0
        }

    /** Applies [steps] taps to [current], bounded per load type (spec §8: invalid input is impossible). */
    @Suppress("LongParameterList")
    fun step(
        current: Long?,
        steps: Int,
        loadType: LoadType,
        unit: WeightUnit,
        plates: List<Long>,
        barGrams: Long,
        machinePlate: Boolean = false,
        override: Long? = null,
    ): Long {
        val step = stepGrams(loadType, unit, plates, machinePlate, override)
        val base = current ?: if (loadType == LoadType.BARBELL) barGrams else 0
        val next = base + steps * step
        return when (loadType) {
            LoadType.BARBELL -> next.coerceAtLeast(barGrams)
            LoadType.BODYWEIGHT -> next
            LoadType.MACHINE -> if (machinePlate) next.coerceAtLeast(1) else next.coerceAtLeast(0)
            LoadType.BAND -> next.coerceIn(1, MAX_BAND_LEVEL)
            else -> next.coerceAtLeast(0)
        }
    }

    const val MAX_BAND_LEVEL = 10L
}
