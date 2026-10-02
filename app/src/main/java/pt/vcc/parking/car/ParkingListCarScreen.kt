package pt.vcc.parking.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.location.FusedLocationProvider

/**
 * Ecra raiz projetado: mapa e lista dos parques proximos.
 *
 * `PlaceListMapTemplate` e o template da categoria POI. Da mapa e lista no mesmo
 * ecra sem a app desenhar nada, o que tambem significa que o osmdroid nao entra
 * aqui — os marcadores sao descritos ao host, que os coloca no seu proprio mapa.
 */
class ParkingListCarScreen(carContext: CarContext) : Screen(carContext) {

    private val loader = CarParkingLoader(
        location = FusedLocationProvider(carContext),
        repository = carContext.vccApplication.parkingRepository,
    )

    private var state: CarParkingUiState = CarParkingUiState.Loading
    private var loadJob: Job? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            /**
             * A procura e feita uma vez e nao em cada `onStart`: voltar do
             * detalhe repetiria a pesquisa e devolveria o ecra ao estado de
             * carregamento sem o condutor ter pedido nada.
             */
            override fun onCreate(owner: LifecycleOwner) = load(forceRefresh = false)

            override fun onDestroy(owner: LifecycleOwner) {
                loadJob?.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val builder = PlaceListMapTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_nearby_title))
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip())

        return when (val current = state) {
            is CarParkingUiState.Loading -> builder.setLoading(true).build()

            is CarParkingUiState.Ready -> builder
                // So aqui e seguro: houve leitura de posicao, logo ha permissao.
                .setCurrentLocationEnabled(true)
                .setAnchor(anchor(current))
                .setItemList(itemList(current.parking))
                .build()

            is CarParkingUiState.Empty -> builder.setEmpty(R.string.parking_empty)

            is CarParkingUiState.PermissionMissing ->
                builder.setEmpty(R.string.car_permission_missing)

            is CarParkingUiState.LocationUnavailable ->
                builder.setEmpty(R.string.car_location_unavailable)

            is CarParkingUiState.Failure -> builder.setEmpty(R.string.parking_error)
        }
    }

    /**
     * Uma lista vazia com mensagem, em vez de um `MessageTemplate`: mantem o
     * mapa e a `ActionStrip` no sitio, pelo que «Atualizar» e o carro estacionado
     * continuam ao alcance mesmo quando a pesquisa falha.
     */
    private fun PlaceListMapTemplate.Builder.setEmpty(messageId: Int): Template =
        setItemList(
            ItemList.Builder()
                .setNoItemsMessage(carContext.getString(messageId))
                .build(),
        ).build()

    private fun anchor(state: CarParkingUiState.Ready): Place =
        Place.Builder(CarLocation.create(state.latitude, state.longitude))
            .setMarker(PlaceMarker.Builder().build())
            .build()

    private fun itemList(parking: List<Parking>): ItemList {
        val builder = ItemList.Builder()
        visible(parking).forEachIndexed { index, item -> builder.addItem(row(index, item)) }
        return builder.build()
    }

    /**
     * Quantas linhas cabem e decisao do veiculo, nao da app: o mesmo APK corre
     * em ecras muito diferentes e o host e o unico que sabe qual e o limite.
     */
    private fun visible(parking: List<Parking>): List<Parking> {
        val manager = carContext.getCarService(ConstraintManager::class.java)
        val limit = manager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
        return CarPlaceLabel.limitedTo(parking, limit)
    }

    private fun row(index: Int, parking: Parking): Row {
        val builder = Row.Builder()
            .setTitle(carContext.parkingTitle(parking))
            .setBrowsable(true)
            .setOnClickListener { screenManager.push(ParkingDetailsCarScreen(carContext, parking)) }
            .setMetadata(
                Metadata.Builder()
                    .setPlace(
                        Place.Builder(CarLocation.create(parking.latitude, parking.longitude))
                            .setMarker(
                                PlaceMarker.Builder()
                                    // O numero liga o pino do mapa a linha da lista.
                                    .setLabel(CarPlaceLabel.markerLabel(index))
                                    .setColor(CarColor.BLUE)
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )

        carContext.parkingSubtitle(parking)?.let { builder.addText(it) }
        return builder.build()
    }

    private fun actionStrip(): ActionStrip = ActionStrip.Builder()
        .addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_action_parked_car))
                .setOnClickListener { screenManager.push(ParkedCarCarScreen(carContext)) }
                .build(),
        )
        .addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_action_refresh))
                .setOnClickListener { load(forceRefresh = true) }
                .build(),
        )
        .build()

    private fun load(forceRefresh: Boolean) {
        loadJob?.cancel()
        state = CarParkingUiState.Loading
        invalidate()

        loadJob = lifecycleScope.launch {
            state = loader.load(forceRefresh = forceRefresh)
            invalidate()
        }
    }
}
