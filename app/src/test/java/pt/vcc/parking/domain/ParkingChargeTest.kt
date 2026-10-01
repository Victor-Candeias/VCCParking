package pt.vcc.parking.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.vcc.parking.domain.model.Parking

/**
 * `vp-12-rich-details`: preco lido do OSM sem adivinhacao.
 *
 * O que estes testes protegem e a fronteira entre «sei quanto custa» e «sei que
 * e pago mas nao quanto»: confundir as duas levaria a app a mostrar um valor
 * inventado a quem vai pagar no parquimetro.
 */
class ParkingChargeTest {

    @Test
    fun `fee no is free`() {
        assertEquals(ParkingCharge.Free, charge(fee = "no"))
    }

    @Test
    fun `fee yes with a readable charge gives the amount`() {
        val result = charge(fee = "yes", charge = "1.20 EUR/h")

        assertEquals(ParkingCharge.Priced(1.20, "EUR", "h"), result)
    }

    @Test
    fun `a comma decimal separator is accepted`() {
        assertEquals(
            ParkingCharge.Priced(1.20, "EUR", "h"),
            charge(fee = "yes", charge = "1,20 EUR/h"),
        )
    }

    @Test
    fun `a charge without a unit keeps the amount`() {
        assertEquals(
            ParkingCharge.Priced(2.0, "EUR"),
            charge(fee = "yes", charge = "2 EUR"),
        )
    }

    @Test
    fun `fee yes without a charge admits it does not know the amount`() {
        assertEquals(
            ParkingCharge.PricedUnknownAmount(null),
            charge(fee = "yes"),
        )
    }

    @Test
    fun `an unreadable charge keeps the original text`() {
        // «consultar tabela» nao e um preco, mas e o que esta no OSM e e mais
        // util ao utilizador do que o silencio.
        assertEquals(
            ParkingCharge.PricedUnknownAmount("consultar tabela"),
            charge(fee = "yes", charge = "consultar tabela"),
        )
    }

    @Test
    fun `an unknown currency is not interpreted as an amount`() {
        assertNull(ParkingCharge.parsePrice("1.20 euros/h"))
    }

    @Test
    fun `a negative amount is rejected`() {
        assertNull(ParkingCharge.parsePrice("-1.00 EUR/h"))
    }

    @Test
    fun `no fee tag at all is unknown`() {
        assertEquals(ParkingCharge.Unknown, charge())
    }

    @Test
    fun `a non boolean fee value is reported as is`() {
        // `fee=interval` existe no OSM e nao e sim nem nao.
        assertEquals(
            ParkingCharge.PricedUnknownAmount("interval"),
            charge(fee = "interval"),
        )
    }

    @Test
    fun `a conditional fee wins over the plain fee tag`() {
        // Dizer «pago» as 21:00 num parque gratuito a partir das 20:00 esta
        // errado, ainda que `fee=yes`.
        val result = charge(fee = "yes", feeConditional = "no @ (20:00-08:00)")

        assertEquals(ParkingCharge.Conditional("no @ (20:00-08:00)"), result)
    }

    @Test
    fun `free is never inferred from a missing tag`() {
        assertNull(parking().isFree)
        assertEquals(true, parking(fee = "no").isFree)
        assertEquals(false, parking(fee = "yes").isFree)
    }

    private fun charge(
        fee: String? = null,
        charge: String? = null,
        feeConditional: String? = null,
    ) = ParkingCharge.of(parking(fee, charge, feeConditional))

    private fun parking(
        fee: String? = null,
        charge: String? = null,
        feeConditional: String? = null,
    ) = Parking(
        osmId = 1,
        osmType = "node",
        latitude = LATITUDE,
        longitude = LONGITUDE,
        fee = fee,
        charge = charge,
        feeConditional = feeConditional,
    )

    private companion object {
        const val LATITUDE = 38.736946
        const val LONGITUDE = -9.142685
    }
}
