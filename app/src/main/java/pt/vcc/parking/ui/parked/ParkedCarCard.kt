package pt.vcc.parking.ui.parked

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.ParkedCar
import pt.vcc.parking.ui.theme.VccParkingTheme

/**
 * Cartao do estacionamento ativo (`vp-08-park-save`).
 *
 * A precisao so e mencionada quando e ma: repetir «±4 m» a cada leitura boa so
 * acrescenta ruido, mas esconder «±120 m» faria o utilizador confiar num ponto
 * que pode estar a um quarteirao de distancia.
 */
@Composable
fun ParkedCarCard(
    parkedCar: ParkedCar,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
    onAdjustOnMap: () -> Unit = {},
    onEnd: () -> Unit = {},
) {
    var confirmingEnd by remember { mutableStateOf(false) }
    val nowMillis = rememberNowMillis()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.parked_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = parkedSinceLabel(parkedCar.parkedAtMillis, nowMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!parkedCar.isAccurate) {
                Text(
                    text = stringResource(
                        R.string.parked_accuracy_warning,
                        parkedCar.accuracyMeters?.roundToInt() ?: 0,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            parkedCar.note?.let { note ->
                Text(text = note, style = MaterialTheme.typography.bodyMedium)
            }

            parkedCar.photoUri?.let { photoUri ->
                ParkedCarPhoto(
                    photoUri = photoUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PHOTO_HEIGHT)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onEdit) {
                    Text(stringResource(R.string.parked_action_edit))
                }
                TextButton(onClick = onAdjustOnMap) {
                    Text(stringResource(R.string.parked_action_adjust))
                }
                TextButton(onClick = { confirmingEnd = true }) {
                    Text(stringResource(R.string.parked_action_end))
                }
            }
        }
    }

    // Terminar nao tem desfazer visivel no ecra principal, por isso confirma-se.
    if (confirmingEnd) {
        AlertDialog(
            onDismissRequest = { confirmingEnd = false },
            title = { Text(stringResource(R.string.parked_end_confirm_title)) },
            text = { Text(stringResource(R.string.parked_end_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingEnd = false
                        onEnd()
                    },
                ) {
                    Text(stringResource(R.string.parked_end_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingEnd = false }) {
                    Text(stringResource(R.string.parked_action_cancel))
                }
            },
        )
    }
}

/**
 * Relogio que avanca sozinho, para «há 3 minutos» nao ficar congelado enquanto
 * o ecra esta aberto. O passo e de um minuto porque e a menor unidade mostrada.
 */
@Composable
internal fun rememberNowMillis(periodMillis: Long = TICK_PERIOD_MILLIS): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(periodMillis) {
        while (true) {
            delay(periodMillis)
            now = System.currentTimeMillis()
        }
    }

    return now
}

/** Tempo decorrido na maior unidade que ainda e informativa. */
@Composable
internal fun parkedSinceLabel(parkedAtMillis: Long, nowMillis: Long): String {
    val elapsedMillis = (nowMillis - parkedAtMillis).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
    val hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis)
    val days = TimeUnit.MILLISECONDS.toDays(elapsedMillis)

    return when {
        minutes < 1 -> stringResource(R.string.parked_since_now)
        hours < 1 -> pluralStringResource(
            R.plurals.parked_since_minutes,
            minutes.toInt(),
            minutes.toInt(),
        )

        days < 1 -> pluralStringResource(R.plurals.parked_since_hours, hours.toInt(), hours.toInt())
        else -> pluralStringResource(R.plurals.parked_since_days, days.toInt(), days.toInt())
    }
}

/**
 * Fotografia do local, lida do armazenamento privado da app.
 *
 * A imagem e reduzida antes de entrar em memoria: uma fotografia de camara
 * ocupa dezenas de megabytes descomprimida e o cartao mostra-a com poucos
 * centimetros de altura.
 */
@Composable
internal fun ParkedCarPhoto(
    photoUri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, photoUri) {
        value = withContext(Dispatchers.IO) { context.decodeScaled(photoUri.toUri()) }
    }

    val bitmap = image ?: return

    Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.parked_photo_description),
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}

private fun Context.decodeScaled(uri: Uri, maxWidthPixels: Int = MAX_PHOTO_WIDTH_PIXELS): ImageBitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        // Sem largura conhecida nao ha como escolher a reducao, e o ciclo abaixo
        // nunca terminaria.
        if (bounds.outWidth <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxWidthPixels) sampleSize *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?.asImageBitmap()
    }.getOrNull()

private const val TICK_PERIOD_MILLIS = 60_000L
private const val MAX_PHOTO_WIDTH_PIXELS = 1_080
private val PHOTO_HEIGHT = 160.dp

@Preview(showBackground = true)
@Composable
private fun ParkedCarCardPreview() {
    VccParkingTheme {
        ParkedCarCard(
            parkedCar = ParkedCar(
                id = 1,
                latitude = 38.7336,
                longitude = -9.1447,
                accuracyMeters = 85f,
                parkedAtMillis = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(42),
                note = "Piso -2, lugar 134",
            ),
        )
    }
}
