package pt.vcc.parking.domain.model

import java.util.concurrent.TimeUnit

/**
 * Lembrete associado a um estacionamento ativo (`vp-11-reminders`).
 *
 * Vive separado de [ParkedCar] porque e opcional: guardar o carro continua a
 * funcionar sem prazo nenhum, e juntar os dois campos obrigaria a tabela do
 * estacionamento a carregar colunas vazias na esmagadora maioria dos registos.
 *
 * [dismissed] existe para o aviso nao voltar depois de o utilizador o fechar:
 * reavisar de um prazo que ele ja viu transforma a funcionalidade em ruido.
 */
data class ParkingReminder(
    val parkedCarId: Long,
    /** `null` quando o utilizador so quer o lembrete periodico, sem prazo pago. */
    val expiresAtMillis: Long? = null,
    val warnBeforeMillis: Long = DEFAULT_WARN_BEFORE_MILLIS,
    /** `null` desliga o lembrete «ainda estacionado». */
    val recurringEveryMillis: Long? = null,
    val dismissed: Boolean = false,
) {

    /** Instante do aviso antecipado; `null` sem prazo definido. */
    val warnAtMillis: Long?
        get() = expiresAtMillis?.let { it - warnBeforeMillis }

    val hasDeadline: Boolean get() = expiresAtMillis != null

    /** Negativo depois de o prazo passar, para a UI poder mostrar o atraso. */
    fun remainingMillis(nowMillis: Long): Long? = expiresAtMillis?.let { it - nowMillis }

    fun isExpired(nowMillis: Long): Boolean =
        expiresAtMillis != null && nowMillis >= expiresAtMillis

    /** Nada para agendar: sem prazo, sem repeticao, ou ja dispensado. */
    val isSilent: Boolean
        get() = dismissed || (expiresAtMillis == null && recurringEveryMillis == null)

    /** Prolongar nao mexe no aviso ja dispensado — passa a haver prazo novo a avisar. */
    fun extendedBy(extraMillis: Long, nowMillis: Long): ParkingReminder {
        val base = expiresAtMillis?.coerceAtLeast(nowMillis) ?: nowMillis
        return copy(expiresAtMillis = base + extraMillis, dismissed = false)
    }

    companion object {
        /** Quinze minutos chegam para andar ate ao parquimetro sem avisar cedo demais. */
        val DEFAULT_WARN_BEFORE_MILLIS: Long = TimeUnit.MINUTES.toMillis(15)

        /** Prolongamento rapido oferecido na notificacao. */
        val EXTENSION_MILLIS: Long = TimeUnit.MINUTES.toMillis(30)
    }
}
