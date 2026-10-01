package pt.vcc.parking.ui.parked

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.vcc.parking.R
import pt.vcc.parking.domain.model.ParkedCar

/**
 * Edicao da nota e da fotografia do estacionamento (`vp-08-park-save`).
 *
 * A captura da posicao nao se repete aqui: o utilizador ja esta longe do carro
 * quando se lembra de escrever «piso -2», e voltar a medir estragaria o registo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParkedCarEditSheet(
    parkedCar: ParkedCar,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    onSave: (note: String?, photoUri: String?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var note by rememberSaveable(parkedCar.id) { mutableStateOf(parkedCar.note.orEmpty()) }
    var photoUri by rememberSaveable(parkedCar.id) { mutableStateOf(parkedCar.photoUri) }
    var photoFailed by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult

        scope.launch {
            val copied = withContext(Dispatchers.IO) { context.copyToPrivateStorage(picked) }
            photoFailed = copied == null
            if (copied != null) {
                context.deletePrivatePhoto(photoUri)
                photoUri = copied
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.parked_edit_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.parked_edit_note_label)) },
                placeholder = { Text(stringResource(R.string.parked_edit_note_placeholder)) },
            )

            photoUri?.let { uri ->
                ParkedCarPhoto(
                    photoUri = uri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PHOTO_PREVIEW_HEIGHT)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            if (photoFailed) {
                Text(
                    text = stringResource(R.string.parked_edit_photo_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        photoFailed = false
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) {
                    Text(
                        stringResource(
                            if (photoUri == null) {
                                R.string.parked_edit_photo_add
                            } else {
                                R.string.parked_edit_photo_replace
                            },
                        ),
                    )
                }

                if (photoUri != null) {
                    TextButton(
                        onClick = {
                            context.deletePrivatePhoto(photoUri)
                            photoUri = null
                        },
                    ) {
                        Text(stringResource(R.string.parked_edit_photo_remove))
                    }
                }
            }

            Button(
                onClick = { onSave(note.ifBlank { null }, photoUri) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.parked_action_save))
            }
        }
    }
}

/**
 * Copia a imagem escolhida para o armazenamento privado da app.
 *
 * O URI devolvido pelo seletor pode deixar de ser legivel assim que o
 * utilizador apagar a foto da galeria ou revogar a permissao temporaria; o
 * registo ficaria com uma referencia morta. A copia e pequena e definitiva.
 */
private fun Context.copyToPrivateStorage(source: Uri): String? = runCatching {
    val directory = File(filesDir, PHOTO_DIRECTORY).apply { mkdirs() }
    val target = File(directory, "parked-${System.currentTimeMillis()}.jpg")

    val copied = contentResolver.openInputStream(source)?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    }

    if (copied == null) null else Uri.fromFile(target).toString()
}.getOrNull()

/** So apaga ficheiros que a propria app criou; um URI externo nao lhe pertence. */
private fun Context.deletePrivatePhoto(photoUri: String?) {
    val path = photoUri?.toUri()?.path ?: return
    val file = File(path)
    val directory = File(filesDir, PHOTO_DIRECTORY)

    if (file.parentFile == directory) file.delete()
}

private const val PHOTO_DIRECTORY = "parked"
private val PHOTO_PREVIEW_HEIGHT = 180.dp
