package es.sebas1705.ironlog.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Fork and knife for the Nutrition tab: the Material "restaurant" glyph (Apache 2.0), which material-icons-core
 * does not include.
 */
internal val NutritionIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Nutrition",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
        .addPath(
            pathData = addPathNodes(
                "M11,9H9V2H7v7H5V2H3v7c0,2.12 1.66,3.84 3.75,3.97V22h2.5v-9.03C11.34,12.84 13,11.12 13,9V2h-2V9z" +
                    "M16,6v8h2.5v8H21V2C18.24,2 16,4.24 16,6z",
            ),
            fill = SolidColor(Color.Black),
        )
        .build()
}
