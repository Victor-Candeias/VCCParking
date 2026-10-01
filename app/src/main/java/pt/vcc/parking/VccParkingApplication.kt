package pt.vcc.parking

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.osmdroid.config.Configuration
import pt.vcc.parking.data.local.ParkingDatabase
import pt.vcc.parking.data.local.PrivateParkedPhotoStore
import pt.vcc.parking.data.local.RoomParkingCache
import pt.vcc.parking.data.local.SharedPreferencesHistorySettings
import pt.vcc.parking.data.remote.OverpassClient
import pt.vcc.parking.data.remote.RouteClient
import pt.vcc.parking.data.repository.ParkedCarRepository
import pt.vcc.parking.data.repository.ParkingHistoryRepository
import pt.vcc.parking.data.repository.ParkingRepository
import pt.vcc.parking.data.repository.ReminderRepository
import pt.vcc.parking.data.repository.ReturnRouteRepository
import pt.vcc.parking.reminder.AlarmReminderScheduler
import pt.vcc.parking.reminder.ReminderCoordinator
import pt.vcc.parking.reminder.ReminderNotifications
import pt.vcc.parking.reminder.ReminderScheduler

class VccParkingApplication : Application() {

    /**
     * Trabalho que nao pertence a nenhum ecra: os recetores de `vp-11-reminders`
     * correm fora de qualquer `ViewModel` e precisam de um `scope` que viva o
     * tempo do processo.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: ParkingDatabase by lazy { ParkingDatabase.getInstance(this) }

    val parkingRepository: ParkingRepository by lazy {
        ParkingRepository(
            remote = OverpassClient.create(),
            cache = RoomParkingCache(database.parkingDao()),
        )
    }

    val parkedCarRepository: ParkedCarRepository by lazy {
        ParkedCarRepository(database.parkedCarDao())
    }

    /** Historico de `vp-10-history`; partilha a tabela do estacionamento ativo. */
    val parkingHistoryRepository: ParkingHistoryRepository by lazy {
        ParkingHistoryRepository(
            dao = database.parkedCarDao(),
            settings = SharedPreferencesHistorySettings(this),
            photos = PrivateParkedPhotoStore(this),
        )
    }

    val returnRouteRepository: ReturnRouteRepository by lazy {
        ReturnRouteRepository(RouteClient.create())
    }

    /** Lembretes de `vp-11-reminders`; partilham a base de dados do estacionamento. */
    val reminderRepository: ReminderRepository by lazy {
        ReminderRepository(
            reminders = database.parkingReminderDao(),
            parkedCars = database.parkedCarDao(),
        )
    }

    val reminderScheduler: ReminderScheduler by lazy { AlarmReminderScheduler(this) }

    val reminderNotifications: ReminderNotifications by lazy { ReminderNotifications(this) }

    private val reminderCoordinator: ReminderCoordinator by lazy {
        ReminderCoordinator(
            parkedCars = parkedCarRepository,
            reminders = reminderRepository,
            scheduler = reminderScheduler,
            notifications = reminderNotifications,
        )
    }

    override fun onCreate() {
        super.onCreate()
        configureOsmdroid()
        ReminderNotifications.createChannels(this)
        reminderCoordinator.start(applicationScope)
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
