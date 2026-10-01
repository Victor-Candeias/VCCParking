package pt.vcc.parking.domain

import java.time.LocalDateTime
import pt.vcc.parking.domain.model.Parking
import pt.vcc.parking.location.UserLocation

/** Seccao 10: exclui apenas os parques explicitamente privados. */
fun List<Parking>.excludePrivate(): List<Parking> = filterNot { it.isPrivate }

/** Seccao 21: preenche `distanceMeters` a partir da posicao do utilizador. */
fun List<Parking>.withDistanceFrom(latitude: Double, longitude: Double): List<Parking> =
    map { parking ->
        parking.copy(
            distanceMeters = GeoDistance.betweenMeters(
                startLatitude = latitude,
                startLongitude = longitude,
                endLatitude = parking.latitude,
                endLongitude = parking.longitude,
            ),
        )
    }

/** Seccao 23: distancia crescente, com os parques sem distancia no fim. */
fun List<Parking>.sortedByDistance(): List<Parking> =
    sortedWith(compareBy(nullsLast()) { it.distanceMeters })

/**
 * Fluxo da seccao 29: filtrar privados, calcular distancia e ordenar. Filtrar
 * primeiro evita calcular distancias para parques que nao serao mostrados.
 */
fun List<Parking>.nearestFrom(latitude: Double, longitude: Double): List<Parking> =
    excludePrivate()
        .withDistanceFrom(latitude, longitude)
        .sortedByDistance()

fun List<Parking>.nearestFrom(location: UserLocation): List<Parking> =
    nearestFrom(location.latitude, location.longitude)

/**
 * Filtros de `vp-12-rich-details`.
 *
 * A regra que atravessa todos: **informacao em falta nunca exclui**. A cobertura
 * do OSM e irregular e, em muitas zonas, filtrar por `fee=no` eliminaria a
 * maioria dos parques apenas por nao terem a tag — a lista ficaria vazia e o
 * utilizador concluiria que nao ha parques, o que e falso.
 *
 * Um filtro com valor `null` esta desligado e deixa tudo passar.
 */
data class ParkingFilter(
    /** `true` mostra apenas os explicitamente gratuitos. */
    val freeOnly: Boolean = false,
    val openNowOnly: Boolean = false,
    val coveredOnly: Boolean = false,
    val disabledSpacesOnly: Boolean = false,
    val chargingSpacesOnly: Boolean = false,
    /** Altura do veiculo em metros; exclui so quem declara um limite menor. */
    val vehicleHeightMeters: Double? = null,
) {

    val isActive: Boolean
        get() = freeOnly || openNowOnly || coveredOnly || disabledSpacesOnly ||
            chargingSpacesOnly || vehicleHeightMeters != null

    /** Numero de filtros ligados, para o resumo no ecra principal. */
    val activeCount: Int
        get() = listOf(
            freeOnly,
            openNowOnly,
            coveredOnly,
            disabledSpacesOnly,
            chargingSpacesOnly,
            vehicleHeightMeters != null,
        ).count { it }

    fun cleared(): ParkingFilter = ParkingFilter()
}

/**
 * Separa a lista em parques que cumprem o filtro e parques sem informacao.
 *
 * Sao dois grupos e nao um porque esconder os parques sem dados daria uma
 * resposta errada — «nao ha nada aqui» — quando a resposta certa e «o OSM nao
 * sabe». A UI mostra-os numa seccao a parte, com o convite a corrigir no OSM.
 */
data class FilteredParking(
    val matching: List<Parking> = emptyList(),
    val unknown: List<Parking> = emptyList(),
) {
    val isEmpty: Boolean get() = matching.isEmpty() && unknown.isEmpty()
    val total: Int get() = matching.size + unknown.size
}

/**
 * Aplica [filter] a [now], separando os parques sem informacao suficiente.
 *
 * Um parque so vai para `unknown` se falhar o filtro **por falta de dados**; se
 * a informacao existe e diz que nao cumpre, e simplesmente excluido.
 */
fun List<Parking>.applyFilter(
    filter: ParkingFilter,
    now: LocalDateTime = LocalDateTime.now(),
): FilteredParking {
    if (!filter.isActive) return FilteredParking(matching = this)

    val matching = mutableListOf<Parking>()
    val unknown = mutableListOf<Parking>()

    forEach { parking ->
        when (parking.matches(filter, now)) {
            true -> matching += parking
            false -> Unit
            null -> unknown += parking
        }
    }

    return FilteredParking(matching, unknown)
}

/**
 * `true` cumpre, `false` nao cumpre, `null` nao ha informacao que chegue.
 *
 * Basta um criterio sem informacao para o parque inteiro ser incerto: dizer que
 * cumpre com base nos criterios que por acaso tem tags seria afirmar o que nao
 * se sabe.
 */
private fun Parking.matches(filter: ParkingFilter, now: LocalDateTime): Boolean? {
    var incomplete = false

    fun check(condition: Boolean?): Boolean? = when (condition) {
        null -> {
            incomplete = true
            null
        }

        false -> false
        true -> true
    }

    if (filter.freeOnly && check(isFree) == false) return false
    if (filter.coveredOnly && check(covered) == false) return false
    if (filter.disabledSpacesOnly && check(hasDisabledSpaces) == false) return false
    if (filter.chargingSpacesOnly && check(hasChargingSpaces) == false) return false
    if (filter.openNowOnly && check(isOpenAt(now)) == false) return false

    filter.vehicleHeightMeters?.let { height ->
        // A altura e o unico criterio em que a falta de informacao nao torna o
        // parque incerto: sem limite declarado, o carro passa ate prova em
        // contrario, e a esmagadora maioria dos veiculos passa mesmo.
        if (ParkingRestrictions.of(this).excludesVehicleOf(height)) return false
    }

    return if (incomplete) null else true
}

/** `null` quando o horario esta ausente ou usa sintaxe nao suportada. */
private fun Parking.isOpenAt(now: LocalDateTime): Boolean? =
    when (OpeningHours.parse(openingHours, now)) {
        is OpeningHours.AlwaysOpen, is OpeningHours.Open -> true
        is OpeningHours.Closed -> false
        is OpeningHours.Unknown -> null
    }
