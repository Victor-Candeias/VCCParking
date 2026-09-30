---
maestru: "0.4"
type: work-spec
id: vp-04-domain-spec
title: "Especificação do modelo de domínio, filtros e distância"
template: implementation-plan-v1
work-item: vcc-parking-mvp-android/vp-04-domain
owner: developer
created: 2026-09-30
---

# Especificação do modelo de domínio, filtros e distância

## Overview

Preenche o pacote `domain` deixado por `vp-01-foundation` com o modelo interno `Parking`,
a conversão dos elementos Overpass devolvidos por `vp-03-overpass`, o filtro de parques
privados e o cálculo e ordenação por distância. Cobre as secções 8, 9, 10 e 21 do
documento do MVP e os critérios «Calcula distância», «Ordena por distância» e
«Exclui `access=private`» da secção 30.

Consome `OverpassElement` (`vp-03-overpass`) e a posição de `vp-02-location`, e é
pré-requisito de `vp-05-cache`, que persiste `Parking` em Room, e de `vp-06-ui`, que
apresenta a lista e o detalhe.

### Âmbito

| Dentro | Fora |
|---|---|
| Modelo `Parking` e leitura das tags da secção 9 | Persistência e entidades Room (`vp-05-cache`) |
| Filtro `access=private` da secção 10 | `ParkingRepository` e fallback para cache (`vp-05-cache`) |
| Distância e ordenação da secção 21 | Formatação de distância e textos (`vp-06-ui`) |

## Implementation

### Phase 1: Modelo e conversão

#### Step 1.1: Modelo `Parking`

O modelo replica a secção 8 do documento do MVP. Todos os campos derivados de tags são
opcionais porque, pela secção 9, uma tag ausente significa **informação indisponível** e
nunca `false`; a UI da secção 24 mostra apenas o que existe.

| Decisão | Valor | Motivo |
|---|---|---|
| `id` derivado | `"<osmType>/<osmId>"` | O `id` OSM só é único dentro do tipo; `vp-05-cache` precisa de chave estável |
| `isPrivate` | `access` igual a `private` | Secção 10: só o valor explícito exclui o parque |
| `distanceMeters` | `Double?`, por omissão `null` | Só fica preenchido quando há posição do utilizador |

#### Step 1.2: Conversão dos elementos Overpass

`OverpassElement` já resolve `lat`/`lon` e `center` (`vp-03-overpass`). A conversão
descarta os elementos sem coordenadas ou com coordenadas fora de `-90..90` / `-180..180`,
para que o cálculo de distância nunca receba entradas inválidas.

| Tag OSM | Campo | Nota |
|---|---|---|
| `name`, `parking`, `operator`, `access`, `fee` | `name`, `parkingType`, `operator`, `access`, `fee` | Texto limpo; vazio equivale a ausente |
| `opening_hours`, `zone`, `zone:colour` | `openingHours`, `zone`, `zoneColour` | — |
| `capacity`, `capacity:disabled` | `capacity`, `disabledCapacity` | Valores não numéricos (`yes`) ou negativos ficam a `null` |
| `phone` ou `contact:phone` | `phone` | `contact:*` é a alternativa comum no OSM |
| `website` ou `contact:website` | `website` | — |

| Action | File | Details |
|---|---|---|
| Create | `.../domain/model/Parking.kt` | Modelo interno da secção 8 |
| Create | `.../data/remote/ParkingMapper.kt` | `OverpassElement` para `Parking` e leitura das tags |
| Delete | `.../domain/model/.gitkeep` | Marcador substituído pelo código real |

Verificação da fase 1:

- [x] `node`, `way` com `center` e `relation` com `center` convertem
- [x] Elementos sem coordenadas ou com coordenadas inválidas são descartados
- [x] Um parque sem `name`, `capacity` ou `fee` converte com campos a `null`

### Phase 2: Distância e filtros

#### Step 2.1: Distância

A secção 21 sugere `Location.distanceBetween`, mas essa API pertence ao SDK Android e,
com `unitTests.isReturnDefaultValues = true`, devolveria zero nos testes unitários. O
cálculo fica em Kotlin puro, pelo mesmo motivo que levou `vp-02-location` a criar
`UserLocation` em vez de expor `android.location.Location`.

| Decisão | Valor | Motivo |
|---|---|---|
| Fórmula | Haversine | Erro abaixo de 0,5% face ao elipsoide WGS84, irrelevante para os raios da secção 20 |
| Raio da Terra | 6 371 008,8 m | Raio médio WGS84 |
| Coordenadas inválidas | `IllegalArgumentException` | Mesma regra de `OverpassQuery`; a conversão já as filtrou |

#### Step 2.2: Filtro e ordenação

A ordem segue o fluxo da secção 29: converter, filtrar privados, calcular distância e
ordenar. Filtrar antes de calcular evita distâncias para parques que não serão mostrados.

| Operação | Comportamento |
|---|---|
| `excludePrivate` | Remove apenas `access=private`; outros valores mantêm-se |
| `withDistanceFrom` | Preenche `distanceMeters` a partir da posição do utilizador |
| `sortedByDistance` | Crescente, com `distanceMeters` a `null` no fim |
| `nearestFrom` | Encadeia as três operações pela ordem da secção 29 |

| Action | File | Details |
|---|---|---|
| Create | `.../domain/GeoDistance.kt` | Distância em metros entre dois pontos |
| Create | `.../domain/ParkingFilters.kt` | Filtro de privados, distância e ordenação |

Verificação da fase 2:

- [x] `access=private` não aparece no resultado; `access=customers` aparece
- [x] Distâncias conhecidas ficam dentro de 0,5% do valor esperado
- [x] Parques sem distância ficam no fim da lista

### Phase 3: Validação

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/java/pt/vcc/parking/data/remote/ParkingMapperTest.kt` | Tipos OSM, tags ausentes e coordenadas inválidas |
| Create | `app/src/test/java/pt/vcc/parking/domain/GeoDistanceTest.kt` | Distâncias conhecidas, simetria e validação |
| Create | `app/src/test/java/pt/vcc/parking/domain/ParkingFiltersTest.kt` | Filtro, distância e ordenação |
| Run | `gradlew.bat :app:testDebugUnitTest` | Executa os testes unitários |

Verificação da fase 3:

- [x] Os cenários «Parque privado», «Dados incompletos» e «Way/relation» da secção 31

## Riscos

| Risco | Detalhe | Mitigação |
|---|---|---|
| Desvio face a `Location.distanceBetween` | A fórmula esférica difere do elipsoide | Erro abaixo de 0,5% nos raios até 50 km da secção 20 |
| Tags com formatos inesperados | `capacity=yes` ou valores negativos | Convertidos para `null` em vez de zero, para não inventar informação |
| `access` com outros valores | `customers`, `permissive`, `no` | Secção 10: só `private` exclui; os restantes exigem análise futura |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/parking/domain/model/Parking.kt` | Create | Modelo interno da secção 8 |
| `app/src/main/java/pt/vcc/parking/domain/model/.gitkeep` | Delete | Marcador do pacote já preenchido |
| `app/src/main/java/pt/vcc/parking/domain/GeoDistance.kt` | Create | Distância entre dois pontos em metros |
| `app/src/main/java/pt/vcc/parking/domain/ParkingFilters.kt` | Create | Filtro de privados, distância e ordenação |
| `app/src/main/java/pt/vcc/parking/data/remote/ParkingMapper.kt` | Create | Conversão de `OverpassElement` em `Parking` |
| `app/src/test/java/pt/vcc/parking/data/remote/ParkingMapperTest.kt` | Create | Testes da conversão e das tags |
| `app/src/test/java/pt/vcc/parking/domain/GeoDistanceTest.kt` | Create | Testes da distância |
| `app/src/test/java/pt/vcc/parking/domain/ParkingFiltersTest.kt` | Create | Testes do filtro e da ordenação |
