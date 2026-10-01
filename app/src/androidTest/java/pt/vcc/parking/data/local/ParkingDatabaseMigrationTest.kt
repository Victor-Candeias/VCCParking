package pt.vcc.parking.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rede de seguranca das migracoes com dados do utilizador: tanto a `parked_car`
 * de `vp-08-park-save` como a `parking_reminder` de `vp-11-reminders` guardam
 * informacao que nao pode ser apagada numa subida de versao, e a cache de
 * `vp-12-rich-details` poupa uma pesquisa com rede. Cada teste corre a migracao
 * sobre um ficheiro real da versao anterior.
 */
@RunWith(AndroidJUnit4::class)
class ParkingDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ParkingDatabase::class.java,
    )

    @Test
    fun migratingFromOneToTwoKeepsCachedParkingsAndCreatesParkedCar() {
        helper.createDatabase(DB_NAME, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO `parking` (" +
                    "`osmType`, `osmId`, `latitude`, `longitude`, `name`, `parkingType`, " +
                    "`capacity`, `operator`, `access`, `fee`, `openingHours`, " +
                    "`disabledCapacity`, `zone`, `zoneColour`, `phone`, `website`, `updatedAt`" +
                    ") VALUES (" +
                    "'node', 1, 38.7, -9.1, 'Parque Teste', 'surface', " +
                    "40, NULL, 'yes', 'no', NULL, " +
                    "2, NULL, NULL, NULL, NULL, 1000)",
            )
        }

        val v2 = helper.runMigrationsAndValidate(
            DB_NAME,
            2,
            true,
            ParkingDatabase.MIGRATION_1_2,
        )

        v2.query("SELECT `name`, `capacity` FROM `parking` WHERE `osmId` = 1").use { cursor ->
            assertTrue("A linha em cache devia sobreviver a migracao", cursor.moveToFirst())
            assertEquals("Parque Teste", cursor.getString(0))
            assertEquals(40, cursor.getInt(1))
            assertEquals(1, cursor.count)
        }

        v2.query("SELECT COUNT(*) FROM `${ParkedCarEntity.TABLE}`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        v2.close()
    }

    /**
     * `vp-11-reminders` acrescentou a tabela dos lembretes. O estacionamento
     * guardado na v2 tem de continuar la: perder o carro para ganhar um prazo
     * seria um mau negocio.
     */
    @Test
    fun migratingFromTwoToThreeKeepsParkedCarsAndCreatesReminders() {
        helper.createDatabase(DB_NAME, 2).use { v2 ->
            v2.execSQL(
                "INSERT INTO `${ParkedCarEntity.TABLE}` (" +
                    "`latitude`, `longitude`, `accuracyMeters`, `parkedAtMillis`, " +
                    "`endedAtMillis`, `note`, `photoUri`, `osmType`, `osmId`" +
                    ") VALUES (38.7, -9.1, 5.0, 1000, NULL, 'Piso -2', NULL, NULL, NULL)",
            )
        }

        val v3 = helper.runMigrationsAndValidate(
            DB_NAME,
            3,
            true,
            ParkingDatabase.MIGRATION_2_3,
        )

        v3.query("SELECT `id`, `note` FROM `${ParkedCarEntity.TABLE}`").use { cursor ->
            assertTrue("O estacionamento devia sobreviver a migracao", cursor.moveToFirst())
            assertEquals(1, cursor.count)
            assertEquals("Piso -2", cursor.getString(1))
        }

        v3.query("SELECT COUNT(*) FROM `${ParkingReminderEntity.TABLE}`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        v3.close()
    }

    /**
     * `vp-12-rich-details` acrescentou dez colunas a cache dos parques. A
     * migracao usa `ALTER TABLE` em vez de recriar a tabela precisamente para
     * nao obrigar a uma pesquisa com rede no primeiro arranque apos a
     * atualizacao — o que so vale se os dados antigos sobreviverem mesmo.
     */
    @Test
    fun migratingFromThreeToFourKeepsCachedParkingsAndAddsNewColumns() {
        helper.createDatabase(DB_NAME, 3).use { v3 ->
            v3.execSQL(
                "INSERT INTO `parking` (" +
                    "`osmType`, `osmId`, `latitude`, `longitude`, `name`, `parkingType`, " +
                    "`capacity`, `operator`, `access`, `fee`, `openingHours`, " +
                    "`disabledCapacity`, `zone`, `zoneColour`, `phone`, `website`, `updatedAt`" +
                    ") VALUES (" +
                    "'way', 7, 38.7, -9.1, 'Parque Antigo', 'underground', " +
                    "80, NULL, 'yes', 'yes', '24/7', " +
                    "4, NULL, NULL, NULL, NULL, 2000)",
            )
        }

        val v4 = helper.runMigrationsAndValidate(
            DB_NAME,
            4,
            true,
            ParkingDatabase.MIGRATION_3_4,
        )

        v4.query(
            "SELECT `name`, `capacity`, `charge`, `covered`, `paymentMethods` " +
                "FROM `parking` WHERE `osmId` = 7",
        ).use { cursor ->
            assertTrue("A linha em cache devia sobreviver a migracao", cursor.moveToFirst())
            assertEquals(1, cursor.count)
            assertEquals("Parque Antigo", cursor.getString(0))
            assertEquals(80, cursor.getInt(1))
            // As colunas novas ficam vazias: a app nao pode inventar o que o
            // OSM ainda nao lhe deu.
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
        }

        v4.close()
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
