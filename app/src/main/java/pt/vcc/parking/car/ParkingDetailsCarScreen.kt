package pt.vcc.parking.car

import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.net.toUri
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.Parking

/**
 * Detalhe de um parque no ecra do carro (`vp-16-android-auto`).
 *
 * Mostra apenas o que decide a paragem. O detalhe completo de `vp-12-rich-details`
 * fica no telemovel: com o veiculo em andamento, uma lista longa de atributos e
 * leitura que o condutor nao pode fazer.
 */
class ParkingDetailsCarScreen(
    carContext: CarContext,
    private val parking: Parking,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val pane = Pane.Builder()
        detailRows().forEach { pane.addRow(it) }
        pane.addAction(navigateAction())

        return PaneTemplate.Builder(pane.build())
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.parkingTitle(parking))
                    .setStartHeaderAction(Action.BACK)
                    .build(),
            )
            .build()
    }

    /**
     * O `Pane` tem um limite de linhas anunciado pelo host. Se nada houver para
     * mostrar fica uma linha com a morada em coordenadas, porque um `Pane` sem
     * linhas nenhumas e rejeitado pelo template.
     */
    private fun detailRows(): List<Row> {
        val rows = listOfNotNull(
            parking.distanceMeters?.let {
                row(R.string.parking_label_distance, distanceSpan(it))
            },
            parking.capacity?.let {
                row(R.string.parking_label_capacity, carContext.getString(R.string.parking_capacity, it))
            },
            carContext.carFeeLabel(parking)?.let { row(R.string.parking_label_charge, it) },
            openingHoursLabel(parking)?.let { row(R.string.parking_label_opening_hours, it) },
        ).ifEmpty { listOf(row(R.string.parking_label_distance, coordinates())) }

        val manager = carContext.getCarService(ConstraintManager::class.java)
        val limit = manager.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PANE)
        return CarPlaceLabel.limitedTo(rows, limit)
    }

    private fun row(labelId: Int, value: CharSequence): Row = Row.Builder()
        .setTitle(carContext.getString(labelId))
        .addText(value)
        .build()

    private fun navigateAction(): Action = Action.Builder()
        .setTitle(carContext.getString(R.string.parking_action_navigate))
        .setFlags(Action.FLAG_PRIMARY)
        .setOnClickListener { navigate() }
        .build()

    /**
     * A navegacao nao e implementada: `ACTION_NAVIGATE` entrega o destino ao
     * host, que o abre na app de navegacao ja ativa no carro. Um `Intent`
     * normal abriria um seletor, que no veiculo nem sequer e apresentado.
     */
    private fun navigate() {
        val destination = "geo:${coordinates()}?q=${coordinates()}".toUri()

        try {
            carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, destination))
        } catch (error: RuntimeException) {
            CarToast.makeText(
                carContext,
                R.string.parking_navigation_unavailable,
                CarToast.LENGTH_LONG,
            ).show()
        }
    }

    private fun coordinates(): String = carCoordinates(parking.latitude, parking.longitude)
}
