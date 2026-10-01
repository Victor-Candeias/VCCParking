package pt.vcc.parking.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de dados da seccao 17, agora partilhada com `vp-08-park-save`.
 *
 * A tabela `parking` continua a ser cache descartavel do Overpass, mas
 * `parked_car` nao e: um registo perdido e informacao do utilizador perdida.
 * Por isso a atualizacao de versao deixou de ser destrutiva e passou a ter
 * [MIGRATION_1_2] explicita. A destruicao so se mantem na descida de versao,
 * onde nao ha migracao possivel.
 */
@Database(
    entities = [ParkingEntity::class, ParkedCarEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class ParkingDatabase : RoomDatabase() {

    abstract fun parkingDao(): ParkingDao

    abstract fun parkedCarDao(): ParkedCarDao

    companion object {
        const val NAME = "vcc-parking.db"

        /**
         * Acrescenta `parked_car` sem tocar na tabela de parques existente.
         *
         * O DDL tem de corresponder exatamente ao que o Room espera de
         * [ParkedCarEntity], incluindo o nome do indice, porque o Room valida a
         * estrutura real da base de dados ao abri-la.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `${ParkedCarEntity.TABLE}` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`latitude` REAL NOT NULL, " +
                        "`longitude` REAL NOT NULL, " +
                        "`accuracyMeters` REAL, " +
                        "`parkedAtMillis` INTEGER NOT NULL, " +
                        "`endedAtMillis` INTEGER, " +
                        "`note` TEXT, " +
                        "`photoUri` TEXT, " +
                        "`osmType` TEXT, " +
                        "`osmId` INTEGER)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_${ParkedCarEntity.TABLE}_endedAtMillis` " +
                        "ON `${ParkedCarEntity.TABLE}` (`endedAtMillis`)",
                )
            }
        }

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
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
