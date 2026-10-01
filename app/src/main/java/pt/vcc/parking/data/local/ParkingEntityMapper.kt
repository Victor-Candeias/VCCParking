package pt.vcc.parking.data.local

import pt.vcc.parking.domain.model.Parking

/**
 * Conversao entre a tabela da seccao 17 e o modelo da seccao 8.
 *
 * `distanceMeters` nao atravessa a cache: a entidade nao o guarda e o modelo
 * devolvido vem sem distancia, que e recalculada a partir da posicao atual.
 */
fun Parking.toEntity(updatedAtMillis: Long): ParkingEntity = ParkingEntity(
    osmType = osmType,
    osmId = osmId,
    latitude = latitude,
    longitude = longitude,
    name = name,
    parkingType = parkingType,
    capacity = capacity,
    operator = operator,
    access = access,
    fee = fee,
    openingHours = openingHours,
    disabledCapacity = disabledCapacity,
    zone = zone,
    zoneColour = zoneColour,
    phone = phone,
    website = website,
    updatedAt = updatedAtMillis,
)

fun ParkingEntity.toParking(): Parking = Parking(
    osmId = osmId,
    osmType = osmType,
    latitude = latitude,
    longitude = longitude,
    name = name,
    parkingType = parkingType,
    capacity = capacity,
    operator = operator,
    access = access,
    fee = fee,
    openingHours = openingHours,
    disabledCapacity = disabledCapacity,
    zone = zone,
    zoneColour = zoneColour,
    phone = phone,
    website = website,
)

fun List<Parking>.toEntities(updatedAtMillis: Long): List<ParkingEntity> =
    map { it.toEntity(updatedAtMillis) }

fun List<ParkingEntity>.toParking(): List<Parking> = map { it.toParking() }
