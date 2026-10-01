package pt.vcc.parking

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import pt.vcc.parking.ui.ParkingRoute
import pt.vcc.parking.ui.theme.VccParkingTheme

class MainActivity : ComponentActivity() {

    /**
     * Pedido vindo da notificacao de `vp-11-reminders` para abrir o regresso ao
     * carro. E estado, e nao apenas leitura do `intent`, porque a activity e
     * `singleTop`: com a app ja aberta o pedido chega por [onNewIntent].
     */
    private var openReturn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openReturn = intent.wantsReturn()

        setContent {
            VccParkingTheme {
                ParkingRoute(
                    openReturn = openReturn,
                    onReturnOpened = { openReturn = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.wantsReturn()) openReturn = true
    }

    private fun Intent?.wantsReturn(): Boolean =
        this?.getBooleanExtra(EXTRA_OPEN_RETURN, false) == true

    companion object {
        const val EXTRA_OPEN_RETURN = "openReturn"
    }
}
