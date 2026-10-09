---
maestru: "0.4"
type: work-spec
id: vp-16-android-auto-spec
title: "Especificação do suporte Android Auto"
template: implementation-plan-v1
work-item: vcc-parking-post-mvp/vp-16-android-auto
owner: developer
created: 2026-10-02
---

# Especificação do suporte Android Auto

## Overview

A app é hoje exclusivamente de telemóvel: o `AndroidManifest.xml` declara apenas a
`MainActivity` e não existe `CarAppService`, pelo que o Android Auto não a reconhece.
Este work-item acrescenta uma segunda face à mesma aplicação — a interface projetada
no ecrã do carro — reutilizando sem alterações o `ParkingRepository` de `vp-03-overpass`,
o `ParkedCarRepository` de `vp-08-park-save` e o `FusedLocationProvider` de `vp-02-location`.

A biblioteca AndroidX Car App não desenha views: a app descreve *templates* e o host
decide como os apresenta. Isto é intencional e é o que garante que a interface respeita
as regras de distração do condutor em qualquer veículo.

### Âmbito

| Dentro | Fora |
|---|---|
| `CarAppService` e `Session` na categoria POI | Android Automotive OS (sistema embebido) |
| Lista e mapa dos parques próximos | Jetpack Compose no carro (não é suportado) |
| Detalhe do parque com ação de navegar | Navegação turn-by-turn própria |
| Guardar e voltar ao carro a partir do carro | Filtros avançados de `vp-12-rich-details` |
| Limites de conteúdo e validação de host | Fotografia e nota do estacionamento |

O Android Automotive OS fica de fora: exige um APK distinto, com `minSdk` e distribuição
próprios. O `CarAppService` escrito aqui é a base comum, mas essa decisão é um work-item
separado.

Os filtros ficam de fora por regra de distração: cada interruptor é um toque com o carro
em andamento. A lista projetada mostra os parques mais próximos, já sem os privados, que
é a resposta que o condutor precisa.

## Implementation

### Phase 1: Dependências e declaração

Sem o serviço declarado no manifest o Android Auto nem sequer lista a aplicação, por
isso esta fase é pré-requisito de tudo o resto.

| Decisão | Valor | Motivo |
|---|---|---|
| Categoria | `androidx.car.app.category.POI` | A app encontra pontos de interesse; não é uma app de navegação |
| `minCarApiLevel` | 1 | Nenhum template usado exige nível superior; abrange todos os hosts |
| Validação de host | `HostValidator` restrito em release | `ALLOW_ALL_HOSTS_VALIDATOR` deixaria qualquer app ligar-se ao serviço |
| `app-projected` | Incluído | É o host de projeção do Android Auto no telemóvel |

| Action | File | Details |
|---|---|---|
| Modify | `gradle/libs.versions.toml` | `androidx.car.app:app`, `app-projected` e `app-testing` |
| Modify | `app/build.gradle.kts` | Dependências da biblioteca Car App |
| Modify | `app/src/main/AndroidManifest.xml` | Serviço, categoria POI e `meta-data` |
| Create | `app/src/main/res/xml/automotive_app_desc.xml` | `<uses name="template"/>` |

Verificação da fase 1:

- [x] `androidx.car.app:app:1.7.0` resolve no `debugCompileClasspath`
- [x] O serviço é exportado e não está protegido por permissão
- [x] O `HostValidator` só é permissivo em builds `debuggable`

### Phase 2: Serviço, sessão e estado

#### Step 2.1: Ponto de entrada

O `CarAppService` é o equivalente à `MainActivity`, mas sem UI própria. A `Session`
cria o ecrã raiz; o `Screen` devolve um `Template` sempre que o host o pede.

#### Step 2.2: Estado testável

A lógica de carregamento não pode ficar dentro do `Screen`: `Screen` depende do
`CarContext`, que não existe em JVM. Pelo mesmo motivo que `vp-11-reminders` extraiu o
`ReminderPlan`, o estado fica num `CarParkingLoader` puro, que recebe o
`LocationProvider` e o `ParkingRepository` por construtor.

| Estado | Quando | O que o ecrã mostra |
|---|---|---|
| `Loading` | Durante a procura | Template em carregamento |
| `Ready` | Parques encontrados | Mapa com âncora e lista |
| `Empty` | Pesquisa sem resultados | Mensagem e ação de repetir |
| `PermissionMissing` | Sem permissão de localização | Remete para o telemóvel |
| `LocationUnavailable` | GPS desligado ou sem leitura | Mensagem e ação de repetir |
| `Failure` | Overpass indisponível e sem cache | Mensagem e ação de repetir |

A permissão de localização não é pedida no carro: um diálogo de runtime com o veículo
em andamento é exatamente o que as regras de distração proíbem. O ecrã explica e pede
que seja concedida no telemóvel.

| Action | File | Details |
|---|---|---|
| Create | `.../car/VccParkingCarAppService.kt` | Serviço e validação de host |
| Create | `.../car/VccParkingSession.kt` | Sessão e ecrã raiz |
| Create | `.../car/CarParkingUiState.kt` | Estados da interface projetada |
| Create | `.../car/CarParkingLoader.kt` | Localização e pesquisa, sem `CarContext` |
| Create | `.../car/CarDependencies.kt` | Acesso aos repositórios da `Application` |

Verificação da fase 2:

- [x] O `CarParkingLoader` compila e é testável sem o SDK do carro
- [x] Sem permissão, o estado é `PermissionMissing` e não uma falha genérica
- [x] A pesquisa reutiliza a cache de `vp-05-cache` quando o Overpass falha

### Phase 3: Ecrãs projetados

#### Step 3.1: Lista com mapa

`PlaceListMapTemplate` é o template da categoria POI: dá mapa e lista no mesmo ecrã
sem a app desenhar nada. O `ConstraintManager` do host decide quantas linhas cabem —
o número não é fixo e varia com o veículo, por isso é pedido e não assumido.

| Decisão | Valor | Motivo |
|---|---|---|
| Nº de parques | Mínimo entre `ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST` e 20 | É o host que conhece o ecrã, mas pode anunciar 1000 e o template tem de caber no Binder (`vp-17-auto-list-limit`) |
| Etiqueta do marcador | Índice, no máximo 3 caracteres | Limite de `PlaceMarker` |
| Ordem | Distância crescente | Já garantida por `nearestFrom` |

#### Step 3.2: Detalhe e navegação

`PaneTemplate` com os dados que decidem a paragem: distância, capacidade, preço e
horário. A navegação não é implementada — é delegada no host com
`CarContext.startCarApp` e `ACTION_NAVIGATE`, que no carro abre a app de navegação
ativa em vez de um seletor.

#### Step 3.3: O carro estacionado

O ciclo de `vp-08-park-save` e `vp-09-return-route` faz ainda mais sentido projetado:
o condutor acaba de estacionar e tem o ecrã à frente. Guardar a posição é um toque;
voltar ao carro delega outra vez no host.

| Action | File | Details |
|---|---|---|
| Create | `.../car/ParkingListCarScreen.kt` | `PlaceListMapTemplate` com mapa e lista |
| Create | `.../car/ParkingDetailsCarScreen.kt` | `PaneTemplate` e ação de navegar |
| Create | `.../car/ParkedCarCarScreen.kt` | Guardar, voltar e terminar |
| Create | `.../car/CarParkingText.kt` | Texto sem Compose para os templates |
| Create | `.../car/CarPlaceLabel.kt` | Etiquetas dos marcadores e limites |
| Modify | `app/src/main/res/values/strings.xml` | Textos da interface projetada |

Na 1.7.0 o `setTitle` e o `setHeaderAction` de `PaneTemplate` e `MessageTemplate` estão
descontinuados a favor de um `Header` próprio. Os ecrãs de detalhe e do carro estacionado
usam já `setHeader`; o `PlaceListMapTemplate` mantém os métodos antigos, que aí continuam
a ser a API corrente.

Verificação da fase 3:

- [x] A lista nunca excede o limite devolvido pelo `ConstraintManager`
- [x] Sem app de navegação, a ação degrada com `CarToast` em vez de rebentar
- [x] Nenhum ecrã pede texto livre ao condutor

### Phase 4: Validação

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/java/pt/vcc/parking/car/CarParkingLoaderTest.kt` | Estados e degradação |
| Create | `app/src/test/java/pt/vcc/parking/car/CarPlaceLabelTest.kt` | Etiquetas e limite de conteúdo |
| Run | `gradlew.bat :app:testDebugUnitTest` | Testes unitários |
| Run | `gradlew.bat :app:assembleDebug` | Compilação e fusão do manifest |

## Riscos

| Risco | Detalhe | Mitigação |
|---|---|---|
| Revisão da Play Store | Apps de carro têm revisão própria | Categoria POI correta e sem texto livre |
| Permissão no telemóvel | O condutor pode nunca a ter concedido | Estado dedicado que explica, em vez de falhar |
| Limites de conteúdo | Variam por veículo | `ConstraintManager` em vez de constantes |
| Templates descontinuados | `PlaceListMapTemplate` evolui | Toda a construção isolada em `ParkingListCarScreen` |
| Sem app de navegação | `ACTION_NAVIGATE` pode não ter destino | `CarToast` e o ecrã mantém-se utilizável |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `gradle/libs.versions.toml` | Modify | Versões da biblioteca Car App |
| `app/build.gradle.kts` | Modify | Dependências `app`, `app-projected` e `app-testing` |
| `app/src/main/AndroidManifest.xml` | Modify | `CarAppService`, categoria POI e `meta-data` |
| `app/src/main/res/xml/automotive_app_desc.xml` | Create | Declaração de app de templates |
| `app/src/main/java/pt/vcc/parking/car/VccParkingCarAppService.kt` | Create | Ponto de entrada do Android Auto |
| `app/src/main/java/pt/vcc/parking/car/VccParkingSession.kt` | Create | Sessão e ecrã raiz |
| `app/src/main/java/pt/vcc/parking/car/CarDependencies.kt` | Create | Repositórios a partir do `CarContext` |
| `app/src/main/java/pt/vcc/parking/car/CarParkingUiState.kt` | Create | Estados da interface projetada |
| `app/src/main/java/pt/vcc/parking/car/CarParkingLoader.kt` | Create | Localização e pesquisa testáveis |
| `app/src/main/java/pt/vcc/parking/car/CarParkingText.kt` | Create | Texto dos templates sem Compose |
| `app/src/main/java/pt/vcc/parking/car/CarPlaceLabel.kt` | Create | Etiquetas dos marcadores e limites de conteúdo |
| `app/src/main/java/pt/vcc/parking/car/ParkingListCarScreen.kt` | Create | Mapa e lista de parques |
| `app/src/main/java/pt/vcc/parking/car/ParkingDetailsCarScreen.kt` | Create | Detalhe e navegação |
| `app/src/main/java/pt/vcc/parking/car/ParkedCarCarScreen.kt` | Create | Guardar e voltar ao carro |
| `app/src/main/res/values/strings.xml` | Modify | Textos da interface projetada |
| `app/src/test/java/pt/vcc/parking/car/CarParkingLoaderTest.kt` | Create | Testes dos estados |
| `app/src/test/java/pt/vcc/parking/car/CarPlaceLabelTest.kt` | Create | Testes das etiquetas e limites |
