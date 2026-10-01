package pt.vcc.parking.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Leitura parcial e honesta de `opening_hours` (`vp-12-rich-details`).
 *
 * A sintaxe do OSM e uma linguagem inteira — regras por feriado, por semana do
 * mes, por estacao do ano, com excecoes encadeadas. Implementa-la por completo
 * nao e razoavel aqui; implementa-la a medias e dizer «aberto» a quem vai
 * encontrar o parque fechado.
 *
 * A saida deste interpretador por isso distingue tres respostas e nao duas:
 * aberto, fechado e [Unknown]. Perante qualquer coisa que nao reconheca com
 * certeza, devolve [Unknown] e a UI mostra o texto original do OSM. Nao saber
 * e uma resposta aceitavel; errar nao e.
 */
sealed interface OpeningHours {

    /** Sempre aberto (`24/7`). */
    data object AlwaysOpen : OpeningHours

    /** Aberto agora; [closesAt] e `null` quando a regra nao diz quando fecha. */
    data class Open(val closesAt: LocalTime? = null) : OpeningHours

    /** Fechado agora; [opensAt] e `null` quando nao ha reabertura conhecida hoje. */
    data class Closed(val opensAt: LocalTime? = null) : OpeningHours

    /**
     * Sintaxe nao suportada ou ausente.
     *
     * [raw] e o valor original, para a UI o poder mostrar tal como esta em vez
     * de o esconder: o utilizador consegue interpretar `Mo-Fr 08:00-20:00` muito
     * melhor do que a app consegue garantir.
     */
    data class Unknown(val raw: String? = null) : OpeningHours

    companion object {

        /** Interpreta [value] para o instante [now]. */
        fun parse(value: String?, now: LocalDateTime): OpeningHours {
            val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return Unknown()
            if (raw.equals(ALWAYS, ignoreCase = true)) return AlwaysOpen

            val rules = raw.split(RULE_SEPARATOR)
                .map(String::trim)
                .filter(String::isNotEmpty)

            // Uma unica regra irreconhecivel invalida a resposta inteira: a que
            // nao foi percebida pode ser exatamente a que fecha o parque hoje.
            val parsed = rules.map { rule -> parseRule(rule) ?: return Unknown(raw) }

            val today = parsed.filter { it.appliesTo(now.dayOfWeek) }
            if (today.isEmpty()) return Closed()

            // A ultima regra do dia ganha: no OSM as regras seguintes sobrepoem
            // as anteriores, e e assim que `Mo-Su 08:00-20:00; We off` funciona.
            val applicable = today.last()
            val intervals = applicable.intervals
            if (intervals.isEmpty()) return Closed()

            val time = now.toLocalTime()
            intervals.firstOrNull { it.contains(time) }?.let { return Open(it.end) }

            return Closed(intervals.map { it.start }.filter { it > time }.minOrNull())
        }

        private const val ALWAYS = "24/7"
        private const val RULE_SEPARATOR = ";"
        private const val OFF = "off"
        private const val CLOSED = "closed"

        private val DAY_NAMES = mapOf(
            "mo" to DayOfWeek.MONDAY,
            "tu" to DayOfWeek.TUESDAY,
            "we" to DayOfWeek.WEDNESDAY,
            "th" to DayOfWeek.THURSDAY,
            "fr" to DayOfWeek.FRIDAY,
            "sa" to DayOfWeek.SATURDAY,
            "su" to DayOfWeek.SUNDAY,
        )

        /** `null` quando a regra usa sintaxe fora do subconjunto suportado. */
        private fun parseRule(rule: String): Rule? {
            val lower = rule.lowercase()

            // Feriados e regras condicionais precisam de dados que a app nao
            // tem; reconhece-las a meio seria pior do que nao as reconhecer.
            if (UNSUPPORTED_MARKERS.any { it in lower }) return null

            if (lower == OFF || lower == CLOSED) return Rule(ALL_DAYS, emptyList())

            // Os dias, quando existem, vem antes do primeiro espaco; sem eles a
            // regra comeca logo com uma hora e aplica-se a semana toda.
            val hasDays = lower.firstOrNull()?.isLetter() == true
            val separator = lower.indexOf(' ')

            val daysPart = if (!hasDays) "" else lower.substringBefore(' ')
            val rest = when {
                !hasDays -> lower
                separator < 0 -> ""
                else -> lower.substring(separator + 1).trim()
            }

            val days = if (daysPart.isEmpty()) ALL_DAYS else parseDays(daysPart) ?: return null

            if (rest == OFF || rest == CLOSED) return Rule(days, emptyList())
            if (rest.isEmpty()) return null

            val intervals = rest.split(INTERVAL_SEPARATOR)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { parseInterval(it) ?: return null }

            return Rule(days, intervals)
        }

        private fun parseDays(value: String): Set<DayOfWeek>? {
            val days = mutableSetOf<DayOfWeek>()

            value.split(INTERVAL_SEPARATOR).map(String::trim).filter(String::isNotEmpty)
                .forEach { token ->
                    if (RANGE_SEPARATOR in token) {
                        val (from, to) = token.split(RANGE_SEPARATOR, limit = 2)
                        val start = DAY_NAMES[from.trim()] ?: return null
                        val end = DAY_NAMES[to.trim()] ?: return null
                        days += daysBetween(start, end)
                    } else {
                        days += DAY_NAMES[token] ?: return null
                    }
                }

            return days.takeIf { it.isNotEmpty() }
        }

        /** Intervalos de dias dao a volta a semana: `Sa-Mo` sao tres dias, nao zero. */
        private fun daysBetween(start: DayOfWeek, end: DayOfWeek): List<DayOfWeek> {
            val span = (end.value - start.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
            return (0..span).map { offset -> start.plus(offset.toLong()) }
        }

        private fun parseInterval(value: String): Interval? {
            if (RANGE_SEPARATOR !in value) return null
            val (from, to) = value.split(RANGE_SEPARATOR, limit = 2)
            val start = parseTime(from.trim()) ?: return null
            val end = parseTime(to.trim()) ?: return null
            // Intervalos que atravessam a meia-noite exigiriam olhar para o dia
            // anterior; fora do subconjunto suportado.
            if (end <= start) return null
            return Interval(start, end)
        }

        private fun parseTime(value: String): LocalTime? {
            val parts = value.split(TIME_SEPARATOR)
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            // `24:00` e valido no OSM e significa o fim do dia.
            if (hour == HOURS_IN_DAY && minute == 0) return LocalTime.MAX
            if (hour !in 0..<HOURS_IN_DAY || minute !in 0..<MINUTES_IN_HOUR) return null
            return LocalTime.of(hour, minute)
        }

        private const val INTERVAL_SEPARATOR = ","
        private const val RANGE_SEPARATOR = "-"
        private const val TIME_SEPARATOR = ":"
        private const val DAYS_IN_WEEK = 7
        private const val HOURS_IN_DAY = 24
        private const val MINUTES_IN_HOUR = 60
        private val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()
        private val UNSUPPORTED_MARKERS = listOf("ph", "sh", "easter", "week", "@", "\"")

        private data class Rule(val days: Set<DayOfWeek>, val intervals: List<Interval>) {
            fun appliesTo(day: DayOfWeek): Boolean = day in days
        }

        private data class Interval(val start: LocalTime, val end: LocalTime) {
            fun contains(time: LocalTime): Boolean = time >= start && time < end
        }
    }
}
