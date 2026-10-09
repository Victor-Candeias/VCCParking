---
maestru: "0.4"
type: work-item
id: vp-17-auto-list-limit
title: Corrigir Android Auto sem resposta por lista de parques demasiado grande
created: 2026-10-09
owner: developer
priority: critical
status: done
completed: 2026-10-09
track: vcc-parking-post-mvp
---

# vp-17-auto-list-limit: Corrigir Android Auto sem resposta por lista de parques demasiado grande

## Sintoma

Ao abrir o VCC Parking no Android Auto, o ecrã fica em carregamento e o host
mostra «A app VCC Parking não está a responder».

## Causa

Reproduzido no Pixel 10a com o Desktop Head Unit (Android Auto 17.7, Car API 8):

- `ConstraintManager.getContentLimit(CONTENT_LIMIT_TYPE_PLACE_LIST)` devolve `1000`;
- a pesquisa de 2 km devolveu 280 parques, todos enviados no `PlaceListMapTemplate`;
- cada linha serializada ocupa ~9,4 KB, logo o template tinha ~2,6 MB;
- o envio ao host falha com `TransactionTooLargeException: data parcel size 2641212 bytes`
  (limite do Binder ≈ 1 MB) e o host fica à espera do template para sempre.

`vp-16-android-auto` assumiu que o limite do host seria «poucas dezenas»; nos hosts
recentes não é.

## Correção

O número de linhas passa a ser o menor entre o limite do host e um teto fixo da app
(`CarPlaceLabel.MAX_PLACES = 20`, ~190 KB), mantendo o mínimo de uma linha.

## Verificação

- [x] Testes de `CarPlaceLabelTest` cobrem o teto
- [x] `gradlew.bat :app:testDebugUnitTest` e `:app:assembleDebug` verdes
- [x] No DHU a lista abre sem `TransactionTooLargeException`
