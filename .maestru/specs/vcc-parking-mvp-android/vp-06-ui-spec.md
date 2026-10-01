---
maestru: "0.4"
type: work-spec
id: vp-06-ui-spec
title: "Especificação da UI e experiência do MVP"
template: implementation-plan-v1
work-item: vcc-parking-mvp-android/vp-06-ui
owner: developer
created: 2026-09-30
---

# Especificação da UI e experiência do MVP

## Overview

Liga as camadas já concluídas (`vp-02-location`, `vp-03-overpass`, `vp-04-domain`,
`vp-05-cache`) ao ecrã que o utilizador vê. Implementa o ecrã principal da secção 22,
a lista da secção 23, o detalhe da secção 24, a navegação externa da secção 25, os
estados da secção 26 e as mensagens da secção 27 do documento do MVP.

Até aqui o `MainActivity` mostrava apenas as coordenadas obtidas por `vp-02-location`.
Passa a mostrar mapa, lista, detalhe e estados, mantendo a arquitetura
`UI → ViewModel → Repository` da secção 11.

## Implementation

### Phase 1: Estado e ViewModel

#### Step 1.1: `ParkingUiState`

A secção 26 define três estados. O `ParkingResult` de `vp-05-cache` já distingue
`Success(fromCache)` de `Failure`, por isso o estado da UI é uma tradução direta,
acrescentando `Idle` para o arranque antes de haver localização.

| Estado | Significado |
|---|---|
| `Idle` | Ainda não há localização; nada foi pesquisado |
| `Loading` | Pesquisa em curso |
| `Success` | Lista pronta, com `fromCache` e `updatedAtMillis` |
| `Error` | Overpass falhou e não havia cache utilizável |

A secção 27 pede mensagens diferentes com e sem cache. O repositório devolve
`fromCache = true` em dois casos distintos — cache fresca (secção 18, sem chamada à
rede) e recurso à cache após falha. O estado expõe `isStale(now)`, que compara a idade
com `ParkingRepository.FRESHNESS_WINDOW_MILLIS`: acima da janela, a cache só pode ter
sido servida por falha de atualização, pelo que se mostra a mensagem de falha com
«Tentar novamente»; abaixo, mostra-se apenas a idade dos dados. Assim evita-se alargar
o contrato de `vp-05-cache` e nunca se afirma uma falha que não aconteceu.

#### Step 1.2: `ParkingViewModel`

| Ação | Efeito |
|---|---|
| `onUserLocation(location)` | Guarda a posição de referência e pesquisa na primeira vez ou após deslocação relevante |
| `onRadiusSelected(metros)` | Troca o raio da secção 20 e repete a pesquisa no último centro |
| `searchArea(lat, lon)` | «Procurar nesta área» da secção 19, com `forceRefresh` |
| `retry()` | Repete a última pesquisa após erro |

Uma nova leitura da localização só relança a pesquisa se o utilizador se tiver
deslocado mais de 200 m, para que uma atualização de GPS não descarte um
«Procurar nesta área» feito noutro ponto do mapa.

As distâncias são recalculadas com `withDistanceFrom`/`sortedByDistance` de
`vp-04-domain` a partir da posição do utilizador, e não do centro pesquisado. Sem isto,
uma pesquisa noutra zona do mapa mostraria distâncias medidas a partir dessa zona, o
que a lista da secção 23 apresentaria como se fossem distâncias ao utilizador.

### Phase 2: Mapa

O `osmdroid` já era dependência desde `vp-01-foundation`. O risco registado nessa spec
— projeto arquivado — foi reavaliado: a versão 6.1.20 funciona, não há alternativa sem
chave de API no MVP e a troca por MapLibre fica para depois do MVP.

| Action | File | Details |
|---|---|---|
| Create | `ui/map/MapViewLifecycle.kt` | `MapView` lembrado e ligado ao ciclo de vida (`onResume`, `onPause`, `onDetach`) |
| Create | `ui/map/ParkingMap.kt` | `AndroidView` com marcadores dos parques e da posição do utilizador |
| Modify | `VccParkingApplication.kt` | `Configuration.load` e `userAgentValue` próprio |

A política de tiles do OSM exige um `userAgentValue` identificável e atribuição visível;
o primeiro é configurado no arranque, o segundo é desenhado sobre o mapa.

O centro do mapa é devolvido ao ecrã por um `MapListener`, para que «Procurar nesta
área» não precise de conhecer tipos do osmdroid fora de `ui/map`. O mapa só se centra
automaticamente na primeira localização obtida; depois disso, o utilizador manda.

### Phase 3: Lista, detalhe e navegação

| Action | File | Details |
|---|---|---|
| Create | `ui/list/ParkingList.kt` | Lista ordenada por distância (secção 23) |
| Create | `ui/details/ParkingDetailsSheet.kt` | Detalhe em `ModalBottomSheet` (secção 24) |
| Create | `ui/details/ExternalNavigation.kt` | `Intent` `geo:` (secção 25) |
| Create | `ui/ParkingFormat.kt` | Tradução de tags OSM e formatação de distâncias |

A secção 24 proíbe inventar dados: cada campo só aparece quando a tag existe. O
`ParkingFormat` traduz os valores conhecidos de `fee`, `access` e `parking` e, perante
um valor desconhecido, mostra o valor OSM em bruto em vez de o descartar ou adivinhar.

A navegação externa pode não ter aplicação instalada; `openExternalNavigation` devolve
`false` nesse caso e o ecrã mostra um `Snackbar` em vez de rebentar com
`ActivityNotFoundException`. O `<queries>` do manifesto é necessário para a visibilidade
de pacotes a partir do Android 11.

### Phase 4: Ecrã principal e estados de localização

O ecrã segue o esboço da secção 22: barra de título, mapa com «Procurar nesta área»,
contagem de resultados, seletor de raio e lista.

Os estados de localização de `vp-02-location` deixam de viver no `MainActivity` e
passam para `ui/LocationStatus.kt`, com o pedido de permissão em `ui/ParkingRoute.kt`.
O `MainActivity` fica apenas com o tema e a rota. Sem localização disponível, o ecrã
mostra o estado da localização em vez do mapa: pesquisar sem posição não é possível.

Verificação:

- [x] `:app:testDebugUnitTest` passa, incluindo os testes novos do `ParkingViewModel`
- [x] `:app:assembleDebug` conclui com sucesso
- [x] `:app:assembleRelease` conclui com sucesso, incluindo `lintVitalRelease`

## Riscos

| Risco | Detalhe | Mitigação |
|---|---|---|
| osmdroid arquivado | Sem manutenção desde 2024 | Isolado em `ui/map`; a restante UI não depende dele |
| Tiles do OSM | Uso excessivo pode levar a bloqueio | `userAgentValue` próprio e pesquisa só por ação explícita (secção 19) |
| Cache fresca vs. falha | O repositório usa `fromCache` para os dois casos | `isStale` distingue pela idade, sem alterar `vp-05-cache` |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/parking/ui/ParkingUiState.kt` | Create | Estados da secção 26 |
| `app/src/main/java/pt/vcc/parking/ui/ParkingViewModel.kt` | Create | Pesquisa, raio e recálculo de distâncias |
| `app/src/main/java/pt/vcc/parking/ui/SearchRadius.kt` | Create | Opções de raio da secção 20 |
| `app/src/main/java/pt/vcc/parking/ui/ParkingFormat.kt` | Create | Formatação e tradução de tags OSM |
| `app/src/main/java/pt/vcc/parking/ui/ParkingRoute.kt` | Create | Ligação entre localização, ViewModel e ecrã |
| `app/src/main/java/pt/vcc/parking/ui/ParkingScreen.kt` | Create | Ecrã principal da secção 22 |
| `app/src/main/java/pt/vcc/parking/ui/LocationStatus.kt` | Create | Estados de localização movidos do `MainActivity` |
| `app/src/main/java/pt/vcc/parking/ui/map/MapViewLifecycle.kt` | Create | `MapView` ligado ao ciclo de vida |
| `app/src/main/java/pt/vcc/parking/ui/map/ParkingMap.kt` | Create | Mapa com marcadores |
| `app/src/main/java/pt/vcc/parking/ui/list/ParkingList.kt` | Create | Lista da secção 23 |
| `app/src/main/java/pt/vcc/parking/ui/details/ParkingDetailsSheet.kt` | Create | Detalhe da secção 24 |
| `app/src/main/java/pt/vcc/parking/ui/details/ExternalNavigation.kt` | Create | Navegação externa da secção 25 |
| `app/src/main/java/pt/vcc/parking/MainActivity.kt` | Modify | Passa a alojar `ParkingRoute` |
| `app/src/main/java/pt/vcc/parking/VccParkingApplication.kt` | Modify | Configuração do osmdroid |
| `app/src/main/AndroidManifest.xml` | Modify | `<queries>` para o esquema `geo` |
| `app/src/main/res/values/strings.xml` | Modify | Textos da UI, rótulos e traduções de tags |
| `app/src/main/res/drawable/ic_map_parking.xml` | Create | Marcador de parque |
| `app/src/main/res/drawable/ic_map_user.xml` | Create | Marcador da posição do utilizador |
| `app/src/test/java/pt/vcc/parking/ui/ParkingViewModelTest.kt` | Create | Testes dos estados e do recálculo de distâncias |
| `app/src/main/java/pt/vcc/parking/ui/map/.gitkeep` | Delete | Pacote deixou de estar vazio |
| `app/src/main/java/pt/vcc/parking/ui/list/.gitkeep` | Delete | Pacote deixou de estar vazio |
| `app/src/main/java/pt/vcc/parking/ui/details/.gitkeep` | Delete | Pacote deixou de estar vazio |
