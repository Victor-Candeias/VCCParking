---
maestru: "0.4"
type: work-spec
id: vp-18-map-first-ui-spec
title: "Especificação do ecrã principal centrado no mapa"
template: implementation-plan-v1
work-item: vcc-parking-post-mvp/vp-18-map-first-ui
owner: developer
created: 2026-10-16
---

# Especificação do ecrã principal centrado no mapa

## Overview

O ecrã principal (`ParkingScreen`) dividia o espaço fixo entre mapa (45%) e lista de
parques (55%). Para orientar a app ao estacionamento à volta do utilizador, o mapa passa
a ocupar o ecrã inteiro, centrado e aproximado na posição atual. A lista de parques fica
num painel inferior (bottom sheet) que, puxado para cima, divide o ecrã a meio.
Motivado pelo work-item `vp-18-map-first-ui`.

### Âmbito

| Dentro | Fora |
|---|---|
| Mapa em ecrã inteiro com painel recolhível | `ReturnScreen`, `HistoryDetailSheet`, `ParkedCarPinPicker` (mantêm o zoom atual) |
| Zoom de quarteirão e botão «centrar na minha posição» | Seguimento contínuo da posição (modo navegação) |
| Resumo compacto do carro estacionado no painel recolhido | Alterações ao modelo, ViewModel ou dados |

## Implementation

### Phase 1: Mapa

#### Step 1.1: Zoom e recentrar

- `ParkingMap` recebe `zoom` (zoom inicial ao centrar) e `recenterRequest` (contador;
  cada incremento anima o mapa para a posição do utilizador com `animateTo`).
- `USER_ZOOM = 18.0` em `MapViewLifecycle.kt`, usado só no ecrã principal; os restantes
  ecrãs continuam com `DEFAULT_ZOOM`. Com o osmdroid sem escalar tiles por DPI, o zoom 18
  mostra ~0,47 m/píxel em Lisboa: ~500 m de largura e ~1,1 km de altura num ecrã de
  1080x2424 com o painel recolhido (~550 m de altura com o painel a meio).

#### Step 1.2: Raio inicial

- `SearchRadius.DEFAULT_METERS` passa de 1 km para 500 m (primeira opção do seletor).
  Não afeta o Android Auto (`CAR_RADIUS_METERS`) nem `OverpassQuery.DEFAULT_RADIUS_METERS`.

### Phase 2: Layout com bottom sheet

#### Step 2.1: `BottomSheetScaffold`

- `ParkingContent` usa `BottomSheetScaffold` com `skipHiddenState = true` e estado
  inicial `PartiallyExpanded`.
- A altura do painel aberto é `SHEET_EXPANDED_FRACTION = 0.5` da área disponível: aberto,
  metade mapa e metade lista.
- O `sheetPeekHeight` é medido (`onSizeChanged`) a partir da pega e do bloco de resumo
  (carro + estado da pesquisa); `SHEET_PEEK_FALLBACK = 96.dp` até à primeira medição.
- O mapa é dimensionado na fase de layout até ao topo do painel
  (`sheetState.requireOffset()`), para o centro (a posição do utilizador) ficar sempre
  visível e o arrasto não recompor o ecrã a cada frame.
- O contentor é recortado (`clipToBounds`) para o painel recolhido não aparecer por trás
  da barra de navegação.

#### Step 2.2: Conteúdo do painel

- Recolhido: `ParkedCarSummary` (se houver carro) + `ResultsSummary` (contagem, estado,
  cache e «Tentar novamente» em erro).
- Aberto: acresce `SearchControls` (raio e filtros) e a lista (`ParkingResults`).
- O antigo `ResultsHeader` foi dividido em `ResultsSummary` e `SearchControls`.

### Phase 3: Carro estacionado

#### Step 3.1: Resumo + detalhe

- O `ParkedCarCard` completo ocupava quase meio ecrã e impedia o painel de abrir.
- `ParkedCarSummary`: ícone, «O seu carro», tempo decorrido, prazo do lembrete (se
  existir) e botão «Voltar ao carro». Tocar abre `ParkedCarDetailsSheet`, um
  `ModalBottomSheet` com o `ParkedCarCard` e todas as acções (lembrete, editar, corrigir
  no mapa, já saí).
- `ReminderStatus` passou a `internal` com parâmetro `style` para ser reutilizado.

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/parking/ui/ParkingScreen.kt` | Modify | Layout com `BottomSheetScaffold`, botão de recentrar, `ResultsSummary`/`SearchControls` |
| `app/src/main/java/pt/vcc/parking/ui/map/ParkingMap.kt` | Modify | Parâmetros `zoom` e `recenterRequest` |
| `app/src/main/java/pt/vcc/parking/ui/map/MapViewLifecycle.kt` | Modify | Constante `USER_ZOOM` |
| `app/src/main/java/pt/vcc/parking/ui/SearchRadius.kt` | Modify | Raio inicial de 500 m |
| `app/src/main/java/pt/vcc/parking/ui/parked/ParkedCarSummary.kt` | Create | Resumo compacto e folha de detalhe do carro |
| `app/src/main/java/pt/vcc/parking/ui/parked/ParkedCarCard.kt` | Modify | `ReminderStatus` reutilizável |
| `app/src/main/res/drawable/ic_my_location.xml` | Create | Ícone do botão «centrar na minha posição» |
| `app/src/main/res/values/strings.xml` | Modify | `parking_action_my_location`, `parked_action_details` |
