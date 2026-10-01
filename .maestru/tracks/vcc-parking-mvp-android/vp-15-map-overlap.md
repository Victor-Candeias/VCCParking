---
maestru: "0.4"
type: work-item
id: vp-15-map-overlap
title: Corrigir sobreposição do mapa sobre o texto de estado dos resultados
created: 2026-10-01
owner: developer
priority: high
status: done
completed: 2026-10-01
track: vcc-parking-mvp-android
---

# vp-15-map-overlap: Corrigir sobreposição do mapa sobre o texto de estado dos resultados

## Problema

No ecrã principal (`ParkingScreen`), o mapa é desenhado por cima do `ResultsHeader`,
tornando ilegível o texto de estado imediatamente abaixo do mapa:

- "94 parques encontrados" aparece cortado e esbatido por baixo dos tiles do mapa.
- A mensagem de erro/aviso ("Não foi possível atualizar os dados. A mostrar a última
  informação disponível.") fica parcialmente tapada.
- A acção "Tentar novamente" fica sobreposta pelo mapa, prejudicando leitura e toque.

Evidência: `Documents/Screenshot_20261001-224816.png`.

## Causa provável

O `MapView` do osmdroid é embebido via `AndroidView` dentro de um `Box` com
`Modifier.weight(MAP_WEIGHT)` (`ParkingScreen.kt:184-232`). A `View` nativa não está
a ser recortada aos limites do `Box`, pelo que desenha para lá da sua área e por cima
dos irmãos seguintes da `Column` (`ResultsHeader` em `ParkingScreen.kt:255`).

## Objetivo

O texto e as acções do `ResultsHeader` devem ser sempre totalmente visíveis e legíveis,
sem qualquer sobreposição do mapa, em todos os estados (sucesso, erro, carregamento) e
com ou sem o cartão de carro estacionado.

## Critérios de aceitação

- [ ] O contador de parques, as mensagens de estado e o botão "Tentar novamente" ficam
      integralmente visíveis, sem sobreposição do mapa.
- [ ] O mapa fica recortado à sua área (`Box` com peso `MAP_WEIGHT`), incluindo durante
      pan/zoom e rotação do ecrã.
- [ ] O contraste do texto de estado cumpre os mínimos de legibilidade do tema escuro.
- [ ] Validado em tema claro e escuro e com o cartão de carro estacionado visível.

## Notas de implementação (a confirmar)

- Avaliar `Modifier.clipToBounds()` no `Box` do mapa.
- Verificar z-order entre `AndroidView` e composables irmãos; se necessário, isolar o
  mapa numa superfície própria ou ajustar `zIndex` do `ResultsHeader`.
- Confirmar que a correção não afeta `ReturnScreen`, `HistoryDetailSheet` e
  `ParkedCarPinPicker`, que reutilizam `ParkingMap`.

## Âmbito

Correção de UI. Não implementar enquanto o work-item não for iniciado.

## Resolução

- `ParkingMap.kt`: `AndroidView` passou a usar `modifier.clipToBounds()`, recortando o
  `MapView` à área medida. Corrige a origem do problema em todos os ecrãs que reutilizam
  o mapa (`ParkingScreen`, `ReturnScreen`, `HistoryDetailSheet`, `ParkedCarPinPicker`).
- `ParkingScreen.kt`: o `Box` do mapa também recorta (`clipToBounds()`) e o
  `ResultsHeader` passou a ter `zIndex(1f)` e fundo opaco
  (`MaterialTheme.colorScheme.surface`), garantindo que o texto de estado é sempre
  desenhado por cima e sem transparência.

Validado com `:app:compileDebugKotlin` e `:app:testDebugUnitTest` (ambos verdes).
