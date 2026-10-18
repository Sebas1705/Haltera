package es.sebas1705.haltera.photos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoProcessingTest {
    @Test
    fun `small images are decoded whole, large ones at the largest power of two that keeps the target`() {
        assertEquals(1, PhotoProcessing.sampleSize(800, 600, 1600))
        assertEquals(1, PhotoProcessing.sampleSize(1600, 1200, 1600))
        assertEquals(1, PhotoProcessing.sampleSize(3199, 2000, 1600))
        assertEquals(2, PhotoProcessing.sampleSize(3200, 2400, 1600))
        assertEquals(2, PhotoProcessing.sampleSize(4032, 3024, 1600)) // a 12 MP phone photo
        assertEquals(4, PhotoProcessing.sampleSize(8000, 6000, 1600))
        assertEquals(4, PhotoProcessing.sampleSize(6000, 8000, 1600)) // portrait the same
        for (side in listOf(1601, 2000, 4032, 9000, 20_000)) {
            val sample = PhotoProcessing.sampleSize(side, side / 2, 1600)
            assertTrue(side / sample >= 1600, "$side decoded at 1/$sample stays at least the target")
        }
    }

    @Test
    fun `the stored size fits the long side and keeps the aspect ratio, never enlarging`() {
        assertEquals(800 to 600, PhotoProcessing.targetSize(800, 600, 1600))
        assertEquals(1600 to 1200, PhotoProcessing.targetSize(4032, 3024, 1600))
        assertEquals(1200 to 1600, PhotoProcessing.targetSize(3024, 4032, 1600))
        assertEquals(360 to 270, PhotoProcessing.targetSize(1600, 1200, 360))
        assertEquals(1600 to 1, PhotoProcessing.targetSize(10_000, 2, 1600)) // never zero
        assertEquals(1 to 1, PhotoProcessing.targetSize(1, 1, 360))
    }
}
