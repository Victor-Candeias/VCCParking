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
 *
 * A versao 3 acrescenta `parking_reminder` (`vp-11-reminders`) e a versao 4
 * alarga a cache de parques com as tags de `vp-12-rich-details`, ambas com a
 * mesma regra: migracao explicita, sem apagar nada.
 */
@Database(
    entities = [ParkingEntity::class, ParkedCarEntity::class, ParkingReminderEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class ParkingDatabase : RoomDatabase() {

    abstract fun parkingDao(): ParkingDao

    abstract fun parkedCarDao(): ParkedCarDao

    abstract fun parkingReminderDao(): ParkingReminderDao

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

        /**
         * Acrescenta `parking_reminder` para `vp-11-reminders`.
         *
         * O DDL replica exatamente o que o Room gera para
         * [ParkingReminderEntity] — incluindo o `INTEGER NOT NULL` do booleano
         * `dismissed` — porque a estrutura real e validada na abertura.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `${ParkingReminderEntity.TABLE}` (" +
                        "`parkedCarId` INTEGER NOT NULL, " +
                        "`expiresAtMillis` INTEGER, " +
                        "`warnBeforeMillis` INTEGER NOT NULL, " +
                        "`recurringEveryMillis` INTEGER, " +
                        "`dismissed` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`parkedCarId`))",
                )
            }
        }

        /**
         * Acrescenta as colunas de `vp-12-rich-details` a cache dos parques.
         *
         * Sao `ALTER TABLE ... ADD COLUMN` e nao uma recriacao da tabela:
         * apagar a cache obrigaria toda a gente a uma pesquisa nova com rede no
         * primeiro arranque apos a atualizacao. Todas as colunas sao opcionais,
         * pelo que as linhas antigas ficam validas sem valor por omissao — que
         * seria sempre errado, porque `null` aqui significa «o OSM nao diz».
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "`charge` TEXT",
                    "`feeConditional` TEXT",
                    "`maxHeightMeters` REAL",
                    "`maxStay` TEXT",
                    "`condition` TEXT",
                    "`supervised` INTEGER",
                    "`covered` INTEGER",
                    "`chargingCapacity` INTEGER",
                    "`parentCapacity` INTEGER",
                    "`paymentMethods` TEXT",
                ).forEach { column ->
                    db.execSQL("ALTER TABLE `${ParkingEntity.TABLE}` ADD COLUMN $column")
                }
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
