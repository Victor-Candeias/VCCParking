package pt.vcc.parking.data.local

import android.content.Context
import androidx.core.net.toUri
import java.io.File

/**
 * Remocao das fotografias de `vp-08-park-save`.
 *
 * Apagar um registo tem de apagar tambem o ficheiro, caso contrario a app
 * acumula imagens orfas que o utilizador julga ter apagado. Fica atras de uma
 * interface porque o repositorio e testado em JVM, sem armazenamento Android.
 */
fun interface ParkedPhotoStore {

    suspend fun remove(photoUri: String)

    companion object {
        /** Util quando nao ha ficheiros a gerir, como nos testes. */
        val None: ParkedPhotoStore = ParkedPhotoStore { }
    }
}

/**
 * So apaga ficheiros que a propria app copiou para o armazenamento privado: um
 * URI externo pertence a galeria do utilizador e nao ao historico.
 */
class PrivateParkedPhotoStore(context: Context) : ParkedPhotoStore {

    private val directory = File(context.applicationContext.filesDir, PHOTO_DIRECTORY)

    override suspend fun remove(photoUri: String) {
        val path = photoUri.toUri().path ?: return
        val file = File(path)
        if (file.parentFile == directory) file.delete()
    }

    private companion object {
        /** O mesmo de `ParkedCarEditSheet`, que escreve as fotografias. */
        const val PHOTO_DIRECTORY = "parked"
    }
}
