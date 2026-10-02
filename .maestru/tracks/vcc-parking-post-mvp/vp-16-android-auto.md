---
maestru: "0.4"
type: work-item
id: vp-16-android-auto
title: Suportar Android Auto
created: 2026-10-02
owner: developer
priority: high
status: done
completed: 2026-10-02
track: vcc-parking-post-mvp
specs:
  - vp-16-android-auto-spec
---

# vp-16-android-auto: Suportar Android Auto

## Resolução

A app passou a expor uma segunda face — a interface projetada no ecrã do carro —
sem duplicar domínio: `ParkingRepository`, `ParkedCarRepository` e o
`LocationProvider` são os mesmos do telemóvel.

**Declaração.** `androidx.car.app:app` e `app-projected` (1.7.0) no catálogo de
versões, `VccParkingCarAppService` declarado no manifest com a ação
`androidx.car.app.CarAppService` e a categoria `androidx.car.app.category.POI`,
`minCarApiLevel` 1 e `automotive_app_desc.xml` a declarar a app como de
templates. O serviço é exportado por exigência do host; a proteção é o
`HostValidator`, permissivo apenas em builds `debuggable` para o Desktop Head Unit.

**Estado.** `CarParkingLoader` e `CarParkingUiState` são JVM puros, sem
`CarContext` — o mesmo motivo que levou o `ReminderPlan` a sair do Android em
`vp-11-reminders`. O raio de pesquisa no carro é de 2 km, maior que no telemóvel,
porque quem conduz aceita desviar-se mais. A permissão de localização nunca é
pedida no carro: sem ela o estado é `PermissionMissing` e o ecrã remete para o
telemóvel, em vez de abrir um diálogo com o veículo em andamento.

**Ecrãs.** `PlaceListMapTemplate` na lista (mapa e parques no mesmo ecrã),
`PaneTemplate` no detalhe e no carro estacionado. O número de linhas vem sempre
do `ConstraintManager` do host, nunca de uma constante: varia com o veículo. A
navegação é delegada com `startCarApp` e `ACTION_NAVIGATE`, que no carro abre a
app de navegação ativa; um `Intent` normal abriria um seletor que o carro não
apresenta. Sem app de navegação, degrada com `CarToast`.

**Desvios.** Os estados de erro usam `ItemList.setNoItemsMessage` dentro do
`PlaceListMapTemplate` em vez de `MessageTemplate`, para o condutor não perder o
mapa nem a barra de ações. Na 1.7.0 o `setTitle`/`setHeaderAction` de
`PaneTemplate` está descontinuado, pelo que o detalhe e o carro estacionado usam
já `setHeader` com `Header`.

**Validação.** 15 testes JVM novos (`CarParkingLoaderTest`, `CarPlaceLabelTest`),
`:app:testDebugUnitTest` e `:app:assembleDebug` verdes e manifest fundido
verificado. Não foi validado em veículo nem no Desktop Head Unit.
