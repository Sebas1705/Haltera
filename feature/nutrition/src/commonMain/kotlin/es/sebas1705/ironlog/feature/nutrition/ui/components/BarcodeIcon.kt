package es.sebas1705.ironlog.feature.nutrition.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A closed polygon through [points] (x, y pairs). */
private fun PathBuilder.polygon(vararg points: Float) {
    moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
    close()
}

/** A barcode glyph for the scan action (material-icons-core has none): bars between four corner brackets. */
internal val BarcodeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Barcode",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black)) {
        listOf(5f to 1f, 7f to 2f, 10f to 1f, 12f to 2f, 15f to 1f, 17f to 2f).forEach { (x, w) ->
            polygon(x, 7f, x + w, 7f, x + w, 17f, x, 17f)
        }
        polygon(2f, 2f, 7f, 2f, 7f, 4f, 4f, 4f, 4f, 7f, 2f, 7f)
        polygon(22f, 2f, 17f, 2f, 17f, 4f, 20f, 4f, 20f, 7f, 22f, 7f)
        polygon(2f, 22f, 7f, 22f, 7f, 20f, 4f, 20f, 4f, 17f, 2f, 17f)
        polygon(22f, 22f, 17f, 22f, 17f, 20f, 20f, 20f, 20f, 17f, 22f, 17f)
    }.build()
}
