package pt.vcc.parking.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de dados da seccao 17. As migracoes sao destrutivas de proposito: a
 * tabela e cache descartavel, nao fonte de verdade, e recriar e mais barato do
 * que manter migracoes para dados que o Overpass volta a fornecer.
 */
@Database(entities = [ParkingEntity::class], version = 1, exportSchema = false)
abstract class ParkingDatabase : RoomDatabase() {

    abstract fun parkingDao(): ParkingDao

    companion object {
        const val NAME = "vcc-parking.db"

        @Volatile
        private var instance: ParkingDatabase? = null

        fun getInstance(context: Context): ParkingDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): ParkingDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ParkingDatabase::class.java,
                NAME,
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
