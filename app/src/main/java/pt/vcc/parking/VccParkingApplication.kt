package pt.vcc.parking

import android.app.Application
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
}
