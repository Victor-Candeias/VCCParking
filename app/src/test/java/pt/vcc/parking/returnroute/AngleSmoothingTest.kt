package pt.vcc.parking.returnroute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AngleSmoothingTest {

    @Test
    fun `the first reading is shown as it is`() {
        assertEquals(42f, AngleSmoothing(FACTOR).next(42f), TOLERANCE)
    }

    @Test
    fun `repeated readings converge to the measured angle`() {
        val smoothing = AngleSmoothing(FACTOR)
        smoothing.next(0f)

        repeat(200) { smoothing.next(90f) }

        assertEquals(90f, smoothing.next(90f), 0.5f)
    }

    /**
     * O caso que justifica suavizar seno e cosseno: a media aritmetica entre 350
     * e 10 graus daria 180, ou seja, a seta apontaria para tras ao atravessar o
     * norte.
     */
    @Test
    fun `crossing north does not send the arrow backwards`() {
        val smoothing = AngleSmoothing(FACTOR)
        smoothing.next(350f)

        val smoothed = smoothing.next(10f)

        assertTrue("Esperava um angulo perto do norte, veio $smoothed", smoothed > 340f || smoothed < 20f)
    }

    @Test
    fun `the result is always inside a single turn`() {
        val smoothing = AngleSmoothing(FACTOR)

        listOf(-200f, 400f, 720f, -0.5f).forEach { reading ->
            val smoothed = smoothing.next(reading)
            assertTrue("Fora do intervalo: $smoothed", smoothed >= 0f && smoothed < 360f)
        }
    }

    private companion object {
        const val FACTOR = 0.15f
        const val TOLERANCE = 1e-3f
    }
}
