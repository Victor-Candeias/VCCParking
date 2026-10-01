package pt.vcc.parking.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pt.vcc.parking.VccParkingApplication
import pt.vcc.parking.data.repository.ParkingRepository
import pt.vcc.parking.data.repository.ParkingResult
import pt.vcc.parking.domain.GeoDistance
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.domain.sortedByDistance
import pt.vcc.parking.domain.withDistanceFrom
import pt.vcc.parking.location.UserLocation

/**
 * Traduz o [ParkingRepository] para os estados da seccao 26 do documento do MVP.
 *
 * A localizacao continua a ser gerida pelo `LocationViewModel` de `vp-02-location`;
 * este ViewModel apenas recebe a posicao ja obtida atraves de [onUserLocation].
 */
class ParkingViewModel(private val repository: ParkingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ParkingUiState>(ParkingUiState.Idle)
    val uiState: StateFlow<ParkingUiState> = _uiState.asStateFlow()

    private val _radiusMeters = MutableStateFlow(SearchRadius.DEFAULT_METERS)
    val radiusMeters: StateFlow<Int> = _radiusMeters.asStateFlow()

    private var userLocation: UserLocation? = null
    private var searchCenter: SearchCenter? = null
    private var searchJob: Job? = null

    /**
     * Uma nova leitura do GPS nao relanca a pesquisa por omissao: se o utilizador
     * tiver usado «Procurar nesta area» noutro ponto do mapa, uma correcao de
     * poucos metros devolveria o ecra para junto dele sem o ter pedido.
     */
    fun onUserLocation(location: UserLocation) {
        val previous = userLocation
        userLocation = location

        val moved = previous != null && GeoDistance.betweenMeters(
            startLatitude = previous.latitude,
            startLongitude = previous.longitude,
            endLatitude = location.latitude,
            endLongitude = location.longitude,
        ) >= SIGNIFICANT_MOVE_METERS

        if (searchCenter == null || moved) {
            search(location.latitude, location.longitude)
        }
    }

    /** Seccao 20: trocar o raio repete a pesquisa no mesmo centro. */
    fun onRadiusSelected(radiusMeters: Int) {
        if (_radiusMeters.value == radiusMeters) return
        _radiusMeters.value = radiusMeters

        val center = searchCenter ?: return
        search(center.latitude, center.longitude)
    }

    /** Seccao 19: pesquisa explicita na area visivel, ignorando a janela de frescura. */
    fun searchArea(latitude: Double, longitude: Double) {
        search(latitude, longitude, forceRefresh = true)
    }

    /** «Tentar novamente» da seccao 27. */
    fun retry() {
        val center = searchCenter
            ?: userLocation?.let { SearchCenter(it.latitude, it.longitude) }
            ?: return
        search(center.latitude, center.longitude, forceRefresh = true)
    }

    private fun search(latitude: Double, longitude: Double, forceRefresh: Boolean = false) {
        searchCenter = SearchCenter(latitude, longitude)
        searchJob?.cancel()
        _uiState.value = ParkingUiState.Loading

        searchJob = viewModelScope.launch {
            val result = repository.parkingNear(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = _radiusMeters.value,
                forceRefresh = forceRefresh,
            )
            _uiState.value = result.toUiState()
        }
    }

    private fun ParkingResult.toUiState(): ParkingUiState = when (this) {
        is ParkingResult.Success -> ParkingUiState.Success(
            parking = parking.relativeToUser(),
            fromCache = fromCache,
            updatedAtMillis = updatedAtMillis,
        )

        is ParkingResult.Failure -> {
            Log.w(TAG, "Pesquisa falhou apos ${attempts.size} tentativa(s)")
            ParkingUiState.Error
        }
    }

    /**
     * O repositorio mede a distancia a partir do ponto pesquisado. Numa pesquisa
     * por area esse ponto nao e o utilizador, e a lista da seccao 23 apresenta a
     * distancia como se fosse ate ele; por isso e recalculada aqui.
     */
    private fun List<Parking>.relativeToUser(): List<Parking> {
        val location = userLocation ?: return this
        return withDistanceFrom(location.latitude, location.longitude).sortedByDistance()
    }

    private data class SearchCenter(val latitude: Double, val longitude: Double)

    companion object {
        private const val TAG = "ParkingViewModel"

        /** Abaixo disto a posicao mudou menos do que o erro tipico do GPS urbano. */
        private const val SIGNIFICANT_MOVE_METERS = 200.0

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY],
                )
                ParkingViewModel((application as VccParkingApplication).parkingRepository)
            }
        }
    }
}
