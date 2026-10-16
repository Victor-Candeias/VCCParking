---
maestru: "0.4"
type: work-item
id: vp-18-map-first-ui
title: Redesenhar o ecrã principal centrado no mapa e na posição do utilizador
created: 2026-10-16
owner: developer
priority: high
status: done
completed: 2026-10-16
track: vcc-parking-post-mvp
specs:
  - vp-18-map-first-ui-spec
---

# vp-18-map-first-ui: Redesenhar o ecrã principal centrado no mapa e na posição do utilizador

## Problema

O ecrã principal (`ParkingScreen`) está demasiado focado na lista de parques. A
aplicação deve estar orientada para o estacionamento na posição atual do utilizador,
com o mapa como elemento principal.

## Objetivo

- O mapa ocupa o ecrã inteiro por omissão.
- O mapa mostra a posição atual do utilizador e faz zoom (aproxima) a essa posição.
- Os parques são apresentados num painel inferior que se puxa para cima
  (bottom sheet), deixando o mapa visível.
- Com o painel aberto, o ecrã fica dividido: metade com o mapa e metade com a lista
  de parques.

## Critérios de aceitação

- [x] Ao abrir a app, o mapa ocupa todo o ecrã (painel de parques recolhido, apenas
      com uma pega/resumo visível).
- [x] O mapa centra e aproxima na posição atual do utilizador, com marcador de
      posição visível.
- [x] Ao puxar o painel para cima, a lista de parques fica com ~50% da altura do
      ecrã e o mapa com os restantes ~50%.
- [x] O painel pode voltar a ser recolhido, devolvendo o ecrã inteiro ao mapa.
- [x] As funcionalidades existentes (carro estacionado, estados de
      carregamento/erro, "Tentar novamente") continuam acessíveis e legíveis.
- [x] Sem regressões em `ReturnScreen`, `HistoryDetailSheet` e `ParkedCarPinPicker`,
      que reutilizam `ParkingMap`.

## Notas de implementação (a confirmar)

- Avaliar `BottomSheetScaffold` (Material 3) com estados recolhido / meio ecrã.
- Rever o layout atual baseado em `Column` com `Modifier.weight(MAP_WEIGHT)`.
- Usar a localização já disponível (vp-02) para centrar e definir o nível de zoom.
- Manter o recorte do mapa introduzido em vp-15 (`clipToBounds()`).

## Âmbito

Alteração de UI/UX.

## Resolução

Detalhe em [vp-18-map-first-ui-spec](../../specs/vcc-parking-post-mvp/vp-18-map-first-ui-spec.md).

- `ParkingScreen.kt`: `BottomSheetScaffold` com mapa em ecrã inteiro; o painel recolhido
  mostra o resumo (carro + estado da pesquisa) e, aberto, ocupa metade do ecrã com raio,
  filtros e lista. O mapa é redimensionado até ao topo do painel para a posição do
  utilizador continuar visível. Botão «Centrar na minha posição» e «Tentar novamente»
  sempre visível em erro.
- `ParkingMap.kt` / `MapViewLifecycle.kt`: parâmetros `zoom` e `recenterRequest`;
  `USER_ZOOM = 18` só no ecrã principal (os outros ecrãs mantêm `DEFAULT_ZOOM`).
- `ParkedCarSummary.kt`: resumo compacto do carro com «Voltar ao carro»; o cartão completo
  abre numa folha modal com todas as acções.
- `SearchRadius.kt`: o raio inicial da pesquisa no telemóvel passou de 1 km para 500 m
  (o Android Auto mantém 2 km e a query Overpass mantém o seu valor por omissão).

Validado com `:app:testDebugUnitTest` e `:app:assembleDebug` (verdes) e no emulador
`Pixel_10a`: painel recolhido, meio ecrã, recolher de novo, recentrar após arrastar o
mapa, carro estacionado (resumo e detalhe) e estado de erro.
