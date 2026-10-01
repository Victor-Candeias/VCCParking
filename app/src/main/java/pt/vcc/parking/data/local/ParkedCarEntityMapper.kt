package pt.vcc.parking.data.local

import pt.vcc.parking.domain.model.ParkedCar

/**
 * Conversao entre a tabela `parked_car` e o modelo de dominio.
 *
 * Ao contrario de `ParkingEntityMapper`, aqui nenhum campo fica pelo caminho:
 * a tabela e a fonte de verdade do registo e nao uma cache de dados externos.
 */
fun ParkedCar.toEntity(): ParkedCarEntity = ParkedCarEntity(
    id = id,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    parkedAtMillis = parkedAtMillis,
    endedAtMillis = endedAtMillis,
    note = note,
    photoUri = photoUri,
    osmType = osmType,
    osmId = osmId,
)

fun ParkedCarEntity.toParkedCar(): ParkedCar = ParkedCar(
    id = id,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    parkedAtMillis = parkedAtMillis,
    endedAtMillis = endedAtMillis,
    note = note,
    photoUri = photoUri,
    osmType = osmType,
    osmId = osmId,
)

fun List<ParkedCarEntity>.toParkedCars(): List<ParkedCar> = map { it.toParkedCar() }
