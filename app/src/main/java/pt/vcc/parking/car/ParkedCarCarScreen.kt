package pt.vcc.parking.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.ParkedCar
import pt.vcc.parking.location.FusedLocationProvider
import pt.vcc.parking.location.LocationResult

/**
 * O carro estacionado, projetado (`vp-16-android-auto`).
 *
 * E onde `vp-08-park-save` e `vp-09-return-route` ganham mais sentido no
 * veiculo: quem acaba de estacionar tem o ecra a frente e guarda a posicao com
 * um toque, sem pegar no telemovel. O registo e o mesmo nas duas interfaces.
 */
class ParkedCarCarScreen(carContext: CarContext) : Screen(carContext) {

    private val repository = carContext.vccApplication.parkedCarRepository
    private val location = FusedLocationProvider(carContext)

    private var parked: ParkedCar? = null
    private var loading = true

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) = reload()
        })
    }

    override fun onGetTemplate(): Template = when {
        loading -> MessageTemplate.Builder(carContext.getString(R.string.car_loading))
            .setHeader(header())
            .setLoading(true)
            .build()

        else -> parked?.let { activeTemplate(it) } ?: emptyTemplate()
    }

    private fun header(): Header = Header.Builder()
        .setTitle(carContext.getString(R.string.car_parked_title))
        .setStartHeaderAction(Action.BACK)
        .build()

    private fun emptyTemplate(): Template =
        MessageTemplate.Builder(carContext.getString(R.string.car_no_parked_car))
            .setHeader(header())
            .addAction(parkHereAction())
            .build()

    /**
     * Duas accoes e o maximo de um `Pane`, e as escolhidas sao as que o condutor
     * usa de facto: voltar ao carro e dizer que ja saiu. Editar a nota ou a
     * fotografia continua a ser trabalho para o telemovel.
     */
    private fun activeTemplate(car: ParkedCar): Template {
        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_parked_title))
                    .addText(carContext.parkedSinceLabel(car.parkedAtMillis, System.currentTimeMillis()))
                    .build(),
            )

        car.note?.let {
            pane.addRow(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.parked_edit_note_label))
                    .addText(it)
                    .build(),
            )
        }

        pane.addAction(returnAction(car))
        pane.addAction(endAction())

        return PaneTemplate.Builder(pane.build())
            .setHeader(header())
            .build()
    }

    private fun parkHereAction(): Action = Action.Builder()
        .setTitle(carContext.getString(R.string.parked_action_park_here))
        .setFlags(Action.FLAG_PRIMARY)
        .setOnClickListener { parkHere() }
        .build()

    private fun returnAction(car: ParkedCar): Action = Action.Builder()
        .setTitle(carContext.getString(R.string.return_action_guide_me))
        .setFlags(Action.FLAG_PRIMARY)
        .setOnClickListener { navigateTo(car) }
        .build()

    private fun endAction(): Action = Action.Builder()
        .setTitle(carContext.getString(R.string.parked_action_end))
        .setOnClickListener { endParking() }
        .build()

    /**
     * A posicao vem do GPS e nunca de um ponto marcado a mao: no carro nao ha
     * mapa onde arrastar um pino, e pedi-lo em andamento seria pior do que nao
     * guardar nada.
     */
    private fun parkHere() {
        lifecycleScope.launch {
            when (val reading = location.currentLocation()) {
                is LocationResult.Available -> {
                    parked = repository.park(
                        latitude = reading.location.latitude,
                        longitude = reading.location.longitude,
                        accuracyMeters = reading.location.accuracyMeters,
                    )
                    toast(R.string.car_parked_saved)
                    invalidate()
                }

                is LocationResult.PermissionMissing -> toast(R.string.car_permission_missing)

                is LocationResult.LocationDisabled,
                is LocationResult.Unavailable,
                -> toast(R.string.car_location_unavailable)
            }
        }
    }

    private fun endParking() {
        lifecycleScope.launch {
            if (repository.endActive()) toast(R.string.car_parked_ended)
            parked = null
            invalidate()
        }
    }

    /** Mesma delegacao do detalhe: o destino vai para a app de navegacao do carro. */
    private fun navigateTo(car: ParkedCar) {
        val coordinates = carCoordinates(car.latitude, car.longitude)

        try {
            carContext.startCarApp(
                Intent(CarContext.ACTION_NAVIGATE, "geo:$coordinates?q=$coordinates".toUri()),
            )
        } catch (error: RuntimeException) {
            toast(R.string.parking_navigation_unavailable)
        }
    }

    private fun reload() {
        lifecycleScope.launch {
            parked = repository.active()
            loading = false
            invalidate()
        }
    }

    private fun toast(messageId: Int) {
        CarToast.makeText(carContext, messageId, CarToast.LENGTH_LONG).show()
    }
}
