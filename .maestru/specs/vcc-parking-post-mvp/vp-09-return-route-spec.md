---
maestru: "0.4"
type: work-spec
id: vp-09-return-route-spec
title: "Especificação do regresso ao carro"
template: implementation-plan-v1
work-item: vcc-parking-post-mvp/vp-09-return-route
owner: developer
created: 2026-10-01
---

# Especificação do regresso ao carro

## Overview

Fecha o ciclo aberto por `vp-08-park-save`: depois de guardar onde estacionou, o
utilizador precisa de voltar lá. Acrescenta direção, distância e rota pedonal até ao
estacionamento ativo, com um modo de orientação simples que funciona sem rede.

Depende de `vp-08-park-save`, que fornece o registo e a posição do carro, e reutiliza o
`LocationProvider` de `vp-02-location`. Motivado pelo work-item `vp-09-return-route`.

### Âmbito

| Dentro | Fora |
|---|---|
| Direção e distância em linha reta (bússola) | Guardar o estacionamento (`vp-08-park-save`) |
| Rota pedonal com um serviço de routing | Navegação por voz ou turn-by-turn completa |
| Mapa com a posição do utilizador e do carro | Rotas de automóvel |
| Abertura em app de navegação externa | Navegação em interiores (parques multipiso) |
| Funcionamento sem rede (modo bússola) | — |

A navegação em interiores fica de fora: exige dados de planta do edifício que o OSM
raramente tem. A nota de piso/lugar de `vp-08-park-save` é a resposta do MVP a esse
problema.

## Implementation

### Phase 1: Modo bússola (sem rede)

Este modo é a base e não depende de serviços externos. Se o routing falhar, o utilizador
fica sempre com direção e distância.

#### Step 1.1: Direção e distância

A distância reutiliza `GeoDistance` de `vp-04-domain`. O rumo (`bearing`) é calculado
entre a posição atual e a do carro.

| Decisão | Valor | Motivo |
|---|---|---|
| Fonte do norte | `TYPE_ROTATION_VECTOR` | Mais estável que magnetómetro + acelerómetro em bruto |
| Suavização | Filtro passa-baixo | Evita a seta a tremer com o ruído do sensor |
| Atualização da posição | 1 a 5 s a pé | Equilíbrio entre resposta e bateria |
| Sem sensor de rotação | Esconde a seta, mostra a distância | Nem todos os dispositivos têm bússola fiável |

| Action | File | Details |
|---|---|---|
| Create | `.../domain/GeoBearing.kt` | Rumo entre duas coordenadas |
| Create | `.../returnroute/CompassProvider.kt` | Azimute a partir do sensor de rotação |
| Create | `.../ui/returnroute/ReturnCompass.kt` | Seta, distância e precisão |
| Create | `app/src/main/res/drawable/ic_return_arrow.xml` | Seta rodada pelo rumo relativo |

Verificação da fase 1:

- [x] A seta aponta para o carro ao rodar o dispositivo
- [x] Sem sensor de rotação, a distância continua a ser mostrada
- [x] Os sensores são libertados em `onPause`

### Phase 2: Rota pedonal

#### Step 2.1: Serviço de routing

A escolha segue o critério já usado em `vp-03-overpass`: sem chave de API e com
fallback entre instâncias.

| Opção | Chave | Perfil a pé | Nota |
|---|---|---|---|
| OSRM (instância pública) | Não | Sim | Mais simples; instâncias públicas sem garantia |
| GraphHopper | Sim no serviço alojado | Sim | Plano gratuito limitado |
| Valhalla (Stadia/FOSSGIS) | Depende da instância | Sim | Bom perfil pedonal |

A decisão fica registada na spec de implementação, mas o código depende de uma interface
`RouteService`, para que a troca de fornecedor não toque na UI — o mesmo padrão de
`OverpassService`.

#### Step 2.2: Degradação

| Situação | Comportamento |
|---|---|
| Routing com sucesso | Desenha a linha da rota e mostra distância e tempo |
| Routing indisponível | Mantém o modo bússola e avisa discretamente |
| Sem rede | Modo bússola sem tentar a chamada |
| Distância inferior a 50 m | Mostra «Está a chegar» em vez de rota |

| Action | File | Details |
|---|---|---|
| Create | `.../domain/model/WalkingRoute.kt` | Rota e pontos no domínio |
| Create | `.../data/remote/RouteService.kt` | Contrato de routing pedonal |
| Create | `.../data/remote/RouteDto.kt` | Desserialização da resposta |
| Create | `.../data/remote/RouteApi.kt` | Chamada Retrofit com `@Url` |
| Create | `.../data/remote/RouteEndpoints.kt` | Instâncias por ordem de preferência |
| Create | `.../data/remote/RouteClient.kt` | Chamada, timeouts e fallback |
| Create | `.../data/repository/ReturnRouteRepository.kt` | Rota com degradação para bússola |

Verificação da fase 2:

- [x] Routing indisponível não bloqueia o ecrã
- [x] A rota é redesenhada quando o utilizador se desvia
- [x] As coordenadas não são registadas no log

### Phase 3: Ecrã de regresso

| Action | File | Details |
|---|---|---|
| Create | `.../returnroute/ReturnViewModel.kt` | Posição, rumo, rota e estados |
| Create | `.../returnroute/ReturnUiState.kt` | Estados: sem carro, bússola, rota, chegou |
| Create | `.../ui/returnroute/ReturnScreen.kt` | Mapa, bússola, distância e nota do local |
| Create | `.../ui/map/ReturnRouteOverlay.kt` | Linha da rota sobre o mapa |
| Modify | `.../ui/map/ParkingMap.kt` | Desenha a linha da rota recebida |
| Modify | `.../ui/ParkingScreen.kt` | Entrada «Voltar ao carro» |
| Modify | `.../ui/parked/ParkedCarCard.kt` | Botão principal do regresso |
| Modify | `.../ui/ParkingRoute.kt` | Alterna entre o mapa e o regresso |
| Modify | `.../VccParkingApplication.kt` | Expõe o `ReturnRouteRepository` |
| Modify | `.../ui/details/ExternalNavigation.kt` | Intent `geo:` com modo a pé |
| Modify | `app/src/main/AndroidManifest.xml` | `<queries>` para `google.navigation` |
| Modify | `app/src/main/res/values/strings.xml` | Textos do ecrã |

O ecrã mostra sempre a nota e a fotografia guardadas em `vp-08-park-save`: num parque
grande, «piso -2, lugar 134» resolve o problema melhor do que qualquer rota.

Verificação da fase 3:

- [x] Sem estacionamento ativo, o ecrã explica e oferece guardar
- [x] A abertura em app externa degrada com `Snackbar` se não existir
- [x] O ecrã continua utilizável em modo avião

### Phase 4: Validação

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/java/pt/vcc/parking/domain/GeoBearingTest.kt` | Rumo em pontos conhecidos |
| Create | `app/src/test/java/pt/vcc/parking/data/remote/RouteClientTest.kt` | Erros, timeout e fallback |
| Create | `app/src/test/java/pt/vcc/parking/returnroute/ReturnViewModelTest.kt` | Degradação e chegada |
| Create | `app/src/test/java/pt/vcc/parking/returnroute/AngleSmoothingTest.kt` | Suavização à volta do norte |
| Run | `gradlew.bat :app:testDebugUnitTest` | Testes unitários |
| Run | `gradlew.bat :app:assembleDebug` | Compilação |

## Desvios de implementação

| Decisão da spec | O que foi feito | Motivo |
|---|---|---|
| Pacote `.../return/` | `.../returnroute/` e `.../ui/returnroute/` | `return` é palavra reservada em Kotlin |
| `ReturnRouteOverlay.kt` em `ui/return/` | `ui/map/ReturnRouteOverlay.kt` | O osmdroid não sai do pacote `ui.map`, regra já em vigor em `ParkingMap.kt` |
| Quatro estados separados | `Locating`, `NoParkedCar` e `Guiding` | Bússola, rota e chegada só diferem em dois campos de `Guiding`; separá-las repetiria tudo o resto |
| «Sem rede, modo bússola sem tentar a chamada» | Tenta e desiste ao fim de 10 s | Não há sinal fiável de «sem rede» antes do pedido; o `RouteClient` falha depressa e o ecrã nunca fica bloqueado |
| Instância de routing | `routing.openstreetmap.de/routed-foot`, depois `router.project-osrm.org` | Só a primeira tem perfil pedonal; a segunda fica como último recurso |

## Riscos

| Risco | Detalhe | Mitigação |
|---|---|---|
| Bússola imprecisa | Interferência magnética em parques | Suavização e distância sempre visível |
| Instâncias públicas de routing | Podem limitar ou desaparecer | Interface `RouteService` e degradação para bússola |
| Consumo de bateria | Sensores e GPS contínuos | Atualização apenas com o ecrã de regresso visível |
| Privacidade | A rota revela origem e destino | Sem chave pessoal, sem registo de coordenadas |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/parking/domain/GeoBearing.kt` | Create | Cálculo do rumo |
| `app/src/main/java/pt/vcc/parking/domain/model/WalkingRoute.kt` | Create | Rota pedonal no domínio |
| `app/src/main/java/pt/vcc/parking/returnroute/CompassProvider.kt` | Create | Azimute do sensor |
| `app/src/main/java/pt/vcc/parking/data/remote/RouteService.kt` | Create | Contrato de routing |
| `app/src/main/java/pt/vcc/parking/data/remote/RouteDto.kt` | Create | DTO da rota |
| `app/src/main/java/pt/vcc/parking/data/remote/RouteApi.kt` | Create | Chamada Retrofit |
| `app/src/main/java/pt/vcc/parking/data/remote/RouteEndpoints.kt` | Create | Instâncias de routing |
| `app/src/main/java/pt/vcc/parking/data/remote/RouteClient.kt` | Create | Cliente com fallback |
| `app/src/main/java/pt/vcc/parking/data/repository/ReturnRouteRepository.kt` | Create | Rota com degradação |
| `app/src/main/java/pt/vcc/parking/returnroute/ReturnViewModel.kt` | Create | Estado do regresso |
| `app/src/main/java/pt/vcc/parking/returnroute/ReturnUiState.kt` | Create | Estados da UI |
| `app/src/main/java/pt/vcc/parking/ui/returnroute/ReturnScreen.kt` | Create | Ecrã de regresso |
| `app/src/main/java/pt/vcc/parking/ui/returnroute/ReturnCompass.kt` | Create | Seta e distância |
| `app/src/main/java/pt/vcc/parking/ui/map/ReturnRouteOverlay.kt` | Create | Linha da rota |
| `app/src/main/res/drawable/ic_return_arrow.xml` | Create | Seta do modo bússola |
| `app/src/main/java/pt/vcc/parking/ui/map/ParkingMap.kt` | Modify | Desenha a rota recebida |
| `app/src/main/java/pt/vcc/parking/ui/ParkingScreen.kt` | Modify | Entrada para o regresso |
| `app/src/main/java/pt/vcc/parking/ui/ParkingRoute.kt` | Modify | Alternância entre ecrãs |
| `app/src/main/java/pt/vcc/parking/ui/parked/ParkedCarCard.kt` | Modify | Botão «Voltar ao carro» |
| `app/src/main/java/pt/vcc/parking/VccParkingApplication.kt` | Modify | Repositório da rota |
| `app/src/main/java/pt/vcc/parking/ui/details/ExternalNavigation.kt` | Modify | Modo a pé |
| `app/src/main/AndroidManifest.xml` | Modify | `<queries>` de navegação |
| `app/src/main/res/values/strings.xml` | Modify | Textos do ecrã |
| `app/src/test/java/pt/vcc/parking/domain/GeoBearingTest.kt` | Create | Testes do rumo |
| `app/src/test/java/pt/vcc/parking/data/remote/RouteClientTest.kt` | Create | Testes do cliente |
| `app/src/test/java/pt/vcc/parking/returnroute/ReturnViewModelTest.kt` | Create | Testes do ViewModel |
| `app/src/test/java/pt/vcc/parking/returnroute/AngleSmoothingTest.kt` | Create | Testes da suavização |
