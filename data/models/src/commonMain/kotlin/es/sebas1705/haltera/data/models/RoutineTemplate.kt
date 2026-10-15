package es.sebas1705.haltera.data.models

/**
 * A famous routine from the bundled catalogue (spec §6.3). Copying it creates an editable [Routine]
 * whose [Routine.origin] keeps [id] and [version].
 */
data class RoutineTemplate(
    val id: String,
    val version: Int,
    val name: LocalizedText,
    val description: LocalizedText,
    /** Who the programme is credited to, e.g. "Mark Rippetoe". */
    val credit: String,
    val level: Level,
    val days: List<TemplateDay>,
)

data class TemplateDay(
    val name: LocalizedText,
    val kind: DayKind = DayKind.TRAINING,
    val slots: List<SlotSpec> = emptyList(),
)
