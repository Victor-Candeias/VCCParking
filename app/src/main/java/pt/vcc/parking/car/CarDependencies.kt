package pt.vcc.parking.car

import androidx.car.app.CarContext
import pt.vcc.parking.VccParkingApplication

/**
 * Acesso aos repositorios a partir do carro (`vp-16-android-auto`).
 *
 * A interface projetada e outra face da mesma aplicacao, e nao outra aplicacao:
 * partilha a base de dados, a cache e o estacionamento ativo. Guardar o carro no
 * ecra do veiculo tem de aparecer no telemovel sem mais nenhum passo.
 */
internal val CarContext.vccApplication: VccParkingApplication
    get() = applicationContext as VccParkingApplication
