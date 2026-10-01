package pt.vcc.parking.domain

import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `vp-12-rich-details`: o interpretador so pode afirmar o que percebe.
 *
 * Metade destes testes verifica que casos reais sao lidos; a outra metade
 * verifica o inverso — que sintaxe fora do subconjunto suportado devolve
 * [OpeningHours.Unknown] em vez de um palpite. Afirmar «aberto» a quem encontra
 * o parque fechado e a unica falha verdadeiramente grave desta funcionalidade.
 */
class OpeningHoursTest {

    @Test
    fun `24-7 is always open`() {
        assertEquals(OpeningHours.AlwaysOpen, parse("24/7"))
    }

    @Test
    fun `missing value is unknown without raw text`() {
        assertEquals(OpeningHours.Unknown(), parse(null))
        assertEquals(OpeningHours.Unknown(), parse("   "))
    }

    @Test
    fun `simple weekday interval is open during business hours`() {
        // Quarta-feira, 10:00.
        val result = parse("Mo-Fr 08:00-20:00", WEDNESDAY_MORNING)

        assertEquals(OpeningHours.Open(LocalTime.of(20, 0)), result)
    }

    @Test
    fun `before opening reports the opening time`() {
        val result = parse("Mo-Fr 08:00-20:00", WEDNESDAY_MORNING.withHour(7))

        assertEquals(OpeningHours.Closed(LocalTime.of(8, 0)), result)
    }

    @Test
    fun `after closing has no reopening today`() {
        val result = parse("Mo-Fr 08:00-20:00", WEDNESDAY_MORNING.withHour(21))

        assertEquals(OpeningHours.Closed(), result)
    }

    @Test
    fun `a day outside the rule is closed`() {
        // Domingo nao esta em `Mo-Fr`.
        val result = parse("Mo-Fr 08:00-20:00", WEDNESDAY_MORNING.plusDays(4))

        assertEquals(OpeningHours.Closed(), result)
    }

    @Test
    fun `lunch break between two intervals is closed`() {
        val value = "Mo-Fr 08:00-12:00,14:00-20:00"

        assertEquals(
            OpeningHours.Closed(LocalTime.of(14, 0)),
            parse(value, WEDNESDAY_MORNING.withHour(13)),
        )
        assertEquals(
            OpeningHours.Open(LocalTime.of(12, 0)),
            parse(value, WEDNESDAY_MORNING.withHour(11)),
        )
    }

    @Test
    fun `a later rule overrides an earlier one`() {
        // Quarta esta em `Mo-Su` mas a regra seguinte fecha-a.
        val result = parse("Mo-Su 08:00-20:00; We off", WEDNESDAY_MORNING)

        assertEquals(OpeningHours.Closed(), result)
    }

    @Test
    fun `day ranges wrap around the week`() {
        // `Sa-Mo` sao sabado, domingo e segunda; quarta fica de fora.
        assertEquals(OpeningHours.Closed(), parse("Sa-Mo 08:00-20:00", WEDNESDAY_MORNING))
        assertTrue(parse("Sa-Mo 08:00-20:00", WEDNESDAY_MORNING.plusDays(3)) is OpeningHours.Open)
    }

    @Test
    fun `a day list is accepted`() {
        assertTrue(parse("Mo,We,Fr 08:00-20:00", WEDNESDAY_MORNING) is OpeningHours.Open)
        assertEquals(
            OpeningHours.Closed(),
            parse("Mo,We,Fr 08:00-20:00", WEDNESDAY_MORNING.plusDays(1)),
        )
    }

    @Test
    fun `hours without days apply every day`() {
        assertTrue(parse("08:00-20:00", WEDNESDAY_MORNING) is OpeningHours.Open)
        assertTrue(parse("08:00-20:00", WEDNESDAY_MORNING.plusDays(4)) is OpeningHours.Open)
    }

    @Test
    fun `midnight closing is accepted`() {
        assertTrue(parse("Mo-Su 08:00-24:00", WEDNESDAY_MORNING.withHour(23)) is OpeningHours.Open)
    }

    @Test
    fun `public holiday rules are not interpreted`() {
        assertEquals(
            OpeningHours.Unknown("Mo-Fr 08:00-20:00; PH off"),
            parse("Mo-Fr 08:00-20:00; PH off", WEDNESDAY_MORNING),
        )
    }

    @Test
    fun `conditional syntax is not interpreted`() {
        val value = "Mo-Fr 08:00-20:00 open \"by appointment\""

        assertEquals(OpeningHours.Unknown(value), parse(value, WEDNESDAY_MORNING))
    }

    @Test
    fun `week selectors are not interpreted`() {
        val value = "week 1-52/2 Mo-Fr 08:00-20:00"

        assertEquals(OpeningHours.Unknown(value), parse(value, WEDNESDAY_MORNING))
    }

    @Test
    fun `an overnight interval is not interpreted`() {
        // `20:00-06:00` atravessa a meia-noite e exigiria olhar para o dia
        // anterior; fora do subconjunto suportado.
        val value = "Mo-Fr 20:00-06:00"

        assertEquals(OpeningHours.Unknown(value), parse(value, WEDNESDAY_MORNING))
    }

    @Test
    fun `an unreadable rule invalidates the whole value`() {
        // A segunda regra e a que podia fechar o parque hoje: aceitar so a
        // primeira seria afirmar aberto sem base.
        val value = "Mo-Fr 08:00-20:00; Sa 2nd 09:00-13:00"

        assertEquals(OpeningHours.Unknown(value), parse(value, WEDNESDAY_MORNING))
    }

    @Test
    fun `garbage is unknown and keeps the original text`() {
        assertEquals(OpeningHours.Unknown("sempre aberto"), parse("sempre aberto"))
    }

    @Test
    fun `unknown never claims open or closed`() {
        val unknown = parse("Mo-Fr 08:00-20:00; PH off", WEDNESDAY_MORNING)

        assertTrue(unknown is OpeningHours.Unknown)
        assertNull((unknown as? OpeningHours.Open)?.closesAt)
    }

    private fun parse(value: String?, now: LocalDateTime = WEDNESDAY_MORNING) =
        OpeningHours.parse(value, now)

    private companion object {
        /** 2026-01-07 foi uma quarta-feira. */
        val WEDNESDAY_MORNING: LocalDateTime = LocalDateTime.of(2026, 1, 7, 10, 0)
    }
}
