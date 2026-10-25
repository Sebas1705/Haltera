package es.sebas1705.haltera.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import es.sebas1705.haltera.core.ui.components.LogoGeometry
import es.sebas1705.haltera.core.ui.components.LogoRect
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * brand/tokens.json is the single source of the brand: scripts/brand.mjs generates the app icons
 * from it, so the theme colours and the logo drawn in Compose must say the same.
 */
class BrandTokensTest {
    private val json = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "brand/tokens.json") }
        .first { it.exists() }
        .readText()

    /** The `"name": "#RRGGBB"` pairs of one theme block of tokens.json. */
    private fun theme(name: String): Map<String, String> {
        val block = json.substringAfter("\"$name\": {").substringBefore("}")
        return colourPairs(block)
    }

    private fun colourPairs(block: String) = Regex("\"(\\w+)\": \"(#[0-9A-Fa-f]{6})\"").findAll(block)
        .associate { it.groupValues[1] to it.groupValues[2].uppercase() }

    private fun logoRects(name: String): List<LogoRect> {
        val block = json.substringAfter("\"logo\": {").substringAfter("\"$name\": [").substringBefore("]")
        val number = "(\\d+(?:\\.\\d+)?)"
        return Regex("\\{ \"x\": $number, \"y\": $number, \"w\": $number, \"h\": $number, \"r\": $number \\}")
            .findAll(block)
            .map { m -> m.groupValues.drop(1).map(String::toFloat).let { LogoRect(it[0], it[1], it[2], it[3], it[4]) } }
            .toList()
    }

    private fun Color.hex() = "#%06X".format(toArgb() and 0xFFFFFF)

    private fun BrandColors.byName() = mapOf(
        "accent" to accent, "onAccent" to onAccent, "accentText" to accentText, "onAccentText" to onAccentText,
        "accentContainer" to accentContainer, "onAccentContainer" to onAccentContainer, "graphite" to graphite,
        "background" to background, "onBackground" to onBackground, "surface" to surface, "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant, "onSurfaceVariant" to onSurfaceVariant, "outline" to outline,
        "outlineVariant" to outlineVariant, "success" to success, "warning" to warning, "error" to error,
        "prGold" to prGold, "prGoldContainer" to prGoldContainer, "onPrGoldContainer" to onPrGoldContainer,
    ).mapValues { it.value.hex() }

    @Test
    fun lightColoursMatchTheBrand() = assertEquals(theme("light"), LightBrandColors.byName())

    @Test
    fun darkColoursMatchTheBrand() = assertEquals(theme("dark"), DarkBrandColors.byName())

    @Test
    fun logoColoursMatchTheBrand() {
        val logo = colourPairs(json.substringAfter("\"logo\": {").substringBefore("\"bars\""))
        assertEquals(
            mapOf(
                "background" to LogoGeometry.background,
                "bar" to LogoGeometry.barColor,
                "beam" to LogoGeometry.beamColor,
                "plate" to LogoGeometry.plateColor
            ).mapValues { it.value.hex() },
            logo,
        )
    }

    @Test
    fun logoGeometryMatchesTheBrand() {
        assertEquals(logoRects("bars"), LogoGeometry.bars)
        assertEquals(logoRects("beams"), LogoGeometry.beams)
        assertEquals(logoRects("plates"), LogoGeometry.plates)
        assertEquals(LogoGeometry.GRID, Regex("\"grid\": (\\d+)").find(json)!!.groupValues[1].toFloat())
        assertEquals(LogoGeometry.CORNER, Regex("\"corner\": (\\d+)").find(json)!!.groupValues[1].toFloat())
    }
}
