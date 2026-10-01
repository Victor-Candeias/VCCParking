package pt.vcc.parking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pt.vcc.parking.ui.ParkingRoute
import pt.vcc.parking.ui.theme.VccParkingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VccParkingTheme {
                ParkingRoute()
            }
        }
    }
}
