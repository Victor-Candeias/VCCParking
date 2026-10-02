package pt.vcc.parking.car

import android.util.Log
import pt.vcc.parking.data.repository.ParkingRepository
import pt.vcc.parking.data.repository.ParkingResult
import pt.vcc.parking.location.LocationProvider
import pt.vcc.parking.location.LocationResult

/**
 * Obtem a posicao e procura os parques para a interface projetada.
 *
 * Existe separado do `Screen` pelo mesmo motivo que levou `vp-11-reminders` a
 * extrair o `ReminderPlan`: um `Screen` precisa de um `CarContext`, que nao
 * existe em JVM, e sem esta separacao a decisao de que estado mostrar so seria
 * verificavel com o carro ligado.
 */
class CarParkingLoader(
    private val location: LocationProvider,
    private val repository: ParkingRepository,
    private val radiusMeters: Int = CAR_RADIUS_METERS,
) {

    /**
     * [forceRefresh] serve a accao «Atualizar» da `ActionStrip` e ignora a
     * janela de frescura de `vp-05-cache`, tal como o «Procurar nesta area» do
     * telemovel.
     */
    suspend fun load(forceRefresh: Boolean = false): CarParkingUiState =
        when (val reading = location.currentLocation()) {
            is LocationResult.PermissionMissing -> CarParkingUiState.PermissionMissing

            is LocationResult.LocationDisabled,
            is LocationResult.Unavailable,
            -> CarParkingUiState.LocationUnavailable

            is LocationResult.Available -> search(
                latitude = reading.location.latitude,
                longitude = reading.location.longitude,
                forceRefresh = forceRefresh,
            )
        }

    private suspend fun search(
        latitude: Double,
        longitude: Double,
        forceRefresh: Boolean,
    ): CarParkingUiState {
        val result = repository.parkingNear(
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            forceRefresh = forceRefresh,
        )

        return when (result) {
            is ParkingResult.Success ->
                if (result.parking.isEmpty()) {
                    CarParkingUiState.Empty
                } else {
                    CarParkingUiState.Ready(
                        parking = result.parking,
                        latitude = latitude,
                        longitude = longitude,
                    )
                }

            is ParkingResult.Failure -> {
                Log.w(TAG, "Pesquisa falhou apos ${result.attempts.size} tentativa(s)")
                CarParkingUiState.Failure
            }
        }
    }

    companion object {
        private const val TAG = "CarParkingLoader"

        /**
         * Maior do que o raio por omissao do telemovel: quem conduz aceita
         * desviar-se mais do que quem procura a pe, e o host so mostra meia duzia
         * de linhas — um raio curto deixaria o ecra vazio em zonas pouco densas.
         */
        const val CAR_RADIUS_METERS = 2_000
    }
}
