package pt.vcc.parking.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * Liga o host ao primeiro ecra projetado.
 *
 * O ecra raiz e sempre a lista de parques: quem liga a app no carro esta a
 * conduzir e quer saber onde parar. O carro ja estacionado fica a um toque, na
 * `ActionStrip`, porque e a pergunta seguinte e nao a primeira.
 */
class VccParkingSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen = ParkingListCarScreen(carContext)
}
