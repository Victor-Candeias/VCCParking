package pt.vcc.parking.reminder

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Canais de notificacao de `vp-11-reminders`.
 *
 * Um canal em falta faz o Android descartar a notificacao em silencio — nao ha
 * excecao nem aviso —, por isso a existencia e a importancia de cada canal sao
 * verificadas no dispositivo e nao assumidas.
 *
 * Os canais so existem a partir do Android 8; abaixo disso nao ha nada a testar.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class ReminderNotificationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun createChannels() {
        ReminderNotifications.createChannels(context)
    }

    @Test
    fun theActiveParkingChannelIsQuiet() {
        val channel = channel(ReminderNotifications.CHANNEL_ACTIVE)

        assertNotNull("O canal do estacionamento a decorrer devia existir", channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel?.importance)
    }

    @Test
    fun theDeadlineChannelInterrupts() {
        val channel = channel(ReminderNotifications.CHANNEL_DEADLINE)

        assertNotNull("O canal do fim do prazo devia existir", channel)
        // O fim do prazo custa dinheiro ao utilizador: tem de interromper.
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel?.importance)
    }

    @Test
    fun theChannelsAreSeparateSoEachCanBeSilencedOnItsOwn() {
        assertNotEquals(
            ReminderNotifications.CHANNEL_ACTIVE,
            ReminderNotifications.CHANNEL_DEADLINE,
        )
    }

    private fun channel(id: String) =
        context.getSystemService<NotificationManager>()?.getNotificationChannel(id)
}
