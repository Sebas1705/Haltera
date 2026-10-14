package es.sebas1705.ironlog.data.local.catalog

import kotlinx.serialization.Serializable

// Shape of composeApp/.../files/catalog.json, produced by scripts/catalog (docs/plans/2026-10-14-f1b-catalog-pipeline.md).

@Serializable
internal data class CatalogDto(
    val catalogVersion: Int,
    val contentHash: String,
    val exercises: List<ExerciseDto>,
    val deprecatedIds: List<String> = emptyList(),
)

@Serializable
internal data class TextDto(val en: String, val es: String)

@Serializable
internal data class InstructionsDto(val en: List<String>, val es: List<String>)

@Serializable
internal data class ExerciseDto(
    val id: String,
    val name: TextDto,
    val category: String,
    val trackingType: String,
    val level: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: InstructionsDto,
    val variants: List<VariantDto>,
)

@Serializable
internal data class VariantDto(
    val id: String,
    val equipment: String,
    val loadType: String,
    val name: TextDto? = null,
    val instructions: InstructionsDto? = null,
    val frames: List<String> = emptyList(),
)
