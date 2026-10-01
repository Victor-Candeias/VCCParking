package pt.vcc.parking.data.local

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quanto tempo o historico guarda um registo terminado (`vp-10-history`).
 *
 * O historico e um registo de deslocacoes: guardar tudo para sempre por
 * omissao seria decidir pelo utilizador. Noventa dias chegam para rever habitos
 * sem acumular anos de movimentos, e quem quiser mais ou menos escolhe.
 */
enum class HistoryRetention(val days: Int?) {
    Days30(30),
    Days90(90),
    Year(365),
    Forever(null),
    ;

    companion object {
        val Default: HistoryRetention = Days90

        fun fromName(name: String?): HistoryRetention =
            entries.firstOrNull { it.name == name } ?: Default
    }
}

/**
 * Preferencia de retencao do historico.
 *
 * E uma interface para o repositorio poder ser testado sem Android: a escolha
 * do utilizador e uma regra de negocio, o sitio onde fica guardada nao e.
 */
interface HistorySettings {

    val retention: Flow<HistoryRetention>

    suspend fun setRetention(retention: HistoryRetention)
}

/**
 * Implementacao sobre `SharedPreferences`.
 *
 * Uma unica preferencia nao justifica trazer o DataStore para o projeto, pela
 * mesma razao que `ParkingRoute` nao traz uma biblioteca de navegacao. O
 * [MutableStateFlow] basta porque a app e o unico processo a escrever.
 */
class SharedPreferencesHistorySettings(context: Context) : HistorySettings {

    private val preferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private val state = MutableStateFlow(
        HistoryRetention.fromName(preferences.getString(KEY_RETENTION, null)),
    )

    override val retention: Flow<HistoryRetention> = state.asStateFlow()

    override suspend fun setRetention(retention: HistoryRetention) {
        preferences.edit().putString(KEY_RETENTION, retention.name).apply()
        state.value = retention
    }

    private companion object {
        const val NAME = "history"
        const val KEY_RETENTION = "retention"
    }
}
