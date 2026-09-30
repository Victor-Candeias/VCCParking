package pt.vcc.parking.domain

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
