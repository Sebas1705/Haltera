package es.sebas1705.ironlog.data.local.catalog

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Settings → Credits shows copies bundled in composeApp; they must match their sources. */
class BundledCreditsTest {
    private val files = File("../../composeApp/src/commonMain/composeResources/files")

    @Test
    fun `bundled attribution matches catalog ATTRIBUTION md`() {
        assertEquals(File("../../catalog/ATTRIBUTION.md").readText(), files.resolve("ATTRIBUTION.md").readText())
    }

    @Test
    fun `bundled foods attribution matches nutrition ATTRIBUTION md`() {
        assertEquals(
            File("../../nutrition/ATTRIBUTION.md").readText(),
            files.resolve("FOODS_ATTRIBUTION.md").readText()
        )
    }

    @Test
    fun `bundled licence matches LICENSE`() {
        assertEquals(File("../../LICENSE").readText(), files.resolve("LICENSE.txt").readText())
    }
}
