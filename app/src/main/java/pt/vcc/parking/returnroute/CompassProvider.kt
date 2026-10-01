package pt.vcc.parking.returnroute

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Azimute do dispositivo, em graus a partir do norte (`vp-09-return-route`).
 *
 * E uma interface para que o ViewModel possa ser testado sem sensores: numa JVM
 * o `SensorManager` nao existe, e o modo bussola e precisamente a parte que tem
 * de funcionar sem rede e sem servicos.
 */
interface CompassProvider {

    /**
     * `false` em dispositivos sem sensor de rotacao. O ecra esconde a seta nesse
     * caso, mas continua a mostrar a distancia.
     */
    val isAvailable: Boolean

    /** Emissoes continuas enquanto houver quem recolha; para ao cancelar. */
    fun azimuthDegrees(): Flow<Float>
}

/**
 * Implementacao sobre `TYPE_ROTATION_VECTOR`.
 *
 * A spec escolhe este sensor e nao o magnetometro em bruto: o Android ja funde
 * bussola, acelerometro e giroscopio, e o resultado treme muito menos dentro de
 * um parque, onde a estrutura metalica distorce o campo magnetico.
 */
class SensorCompassProvider(
    context: Context,
    private val smoothingFactor: Float = DEFAULT_SMOOTHING_FACTOR,
) : CompassProvider {

    private val appContext = context.applicationContext

    private val sensorManager: SensorManager? =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationSensor: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    override val isAvailable: Boolean get() = rotationSensor != null

    override fun azimuthDegrees(): Flow<Float> = callbackFlow {
        val manager = sensorManager
        val sensor = rotationSensor
        if (manager == null || sensor == null) {
            close()
            return@callbackFlow
        }

        val smoothing = AngleSmoothing(smoothingFactor)
        val rotationMatrix = FloatArray(MATRIX_SIZE)
        val remappedMatrix = FloatArray(MATRIX_SIZE)
        val orientation = FloatArray(ORIENTATION_SIZE)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                // Em paisagem os eixos do sensor acompanham o dispositivo e nao
                // o ecra; sem este remapeamento a seta ficaria um quarto de
                // volta ao lado assim que o telemovel fosse virado.
                val (axisX, axisY) = displayAxes()
                SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
                SensorManager.getOrientation(remappedMatrix, orientation)

                val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                trySend(smoothing.next(azimuth))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

        // Liberta o sensor quando o ecra deixa de recolher — e o que impede a
        // bussola de continuar a gastar bateria depois do `onPause`.
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()

    /** O «para cima» do ecra muda com a rotacao da janela. */
    @Suppress("DEPRECATION")
    private fun displayAxes(): Pair<Int, Int> {
        val rotation = (appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?.defaultDisplay
            ?.rotation
            ?: Surface.ROTATION_0

        return when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
    }

    companion object {
        private const val MATRIX_SIZE = 9
        private const val ORIENTATION_SIZE = 3

        /**
         * Quanto de cada leitura nova entra no valor apresentado. Mais alto
         * responde depressa mas treme; mais baixo fica suave mas atrasado.
         */
        const val DEFAULT_SMOOTHING_FACTOR = 0.15f
    }
}

/**
 * Filtro passa-baixo para angulos.
 *
 * Nao se pode suavizar graus directamente: entre 359 e 1 a media aritmetica da
 * 180, ou seja, a seta daria meia volta sempre que atravessasse o norte. A
 * suavizacao e feita sobre o seno e o cosseno, onde essa descontinuidade nao
 * existe, e so depois se volta ao angulo.
 */
internal class AngleSmoothing(private val factor: Float) {

    private var smoothedSin = 0.0
    private var smoothedCos = 0.0
    private var started = false

    fun next(degrees: Float): Float {
        val radians = Math.toRadians(degrees.toDouble())

        if (started) {
            smoothedSin += factor * (sin(radians) - smoothedSin)
            smoothedCos += factor * (cos(radians) - smoothedCos)
        } else {
            smoothedSin = sin(radians)
            smoothedCos = cos(radians)
            started = true
        }

        val smoothed = Math.toDegrees(atan2(smoothedSin, smoothedCos))
        return (((smoothed % FULL_TURN) + FULL_TURN) % FULL_TURN).toFloat()
    }

    private companion object {
        const val FULL_TURN = 360.0
    }
}
