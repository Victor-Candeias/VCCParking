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
 * Rede de seguranca da `vp-08-park-save`: a tabela `parked_car` guarda dados do
 * utilizador, por isso a subida de versao deixou de poder apagar a base de
 * dados. Este teste corre [ParkingDatabase.MIGRATION_1_2] sobre um ficheiro v1
 * real e confirma que nada se perde pelo caminho.
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

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
