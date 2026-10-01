package pt.vcc.parking

import android.app.Application
import org.osmdroid.config.Configuration
import pt.vcc.parking.data.local.ParkingDatabase
import pt.vcc.parking.data.local.RoomParkingCache
import pt.vcc.parking.data.remote.OverpassClient
import pt.vcc.parking.data.repository.ParkingRepository

class VccParkingApplication : Application() {

    val database: ParkingDatabase by lazy { ParkingDatabase.getInstance(this) }

    val parkingRepository: ParkingRepository by lazy {
        ParkingRepository(
            remote = OverpassClient.create(),
            cache = RoomParkingCache(database.parkingDao()),
        )
    }

    override fun onCreate() {
        super.onCreate()
        configureOsmdroid()
    }

    /**
     * O osmdroid tem de estar configurado antes do primeiro `MapView`.
     *
     * A politica de tiles do OpenStreetMap bloqueia clientes sem User-Agent
     * proprio, pelo que o valor por omissao da biblioteca nao serve. O `load`
     * tambem aponta a cache de tiles para o armazenamento privado da aplicacao,
     * evitando permissoes de armazenamento.
     */
    private fun configureOsmdroid() {
        val preferences = getSharedPreferences(OSMDROID_PREFERENCES, MODE_PRIVATE)
        Configuration.getInstance().apply {
            load(this@VccParkingApplication, preferences)
            userAgentValue = "VccParking/${BuildConfig.VERSION_NAME} (Android)"
        }
    }

    private companion object {
        const val OSMDROID_PREFERENCES = "osmdroid"
    }
}
