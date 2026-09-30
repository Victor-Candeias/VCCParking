---
maestru: "0.4"
type: work-spec
id: vp-02-location-spec
title: "Especificação de localização e permissões"
template: implementation-plan-v1
work-item: vcc-parking-mvp-android/vp-02-location
owner: developer
created: 2026-09-30
---

# Especificação de localização e permissões

## Overview

Preenche o pacote `location` deixado por `vp-01-foundation` com o acesso à posição do
utilizador através do Fused Location Provider, o pedido de permissões em runtime e os
estados de UI associados. Entrega a primeira etapa do fluxo da secção 29 do documento
do MVP (`Abrir aplicação → Pedir localização → Obter coordenadas`) e o critério de
conclusão «Obtém localização».

É pré-requisito de `vp-03-overpass`, que precisa de latitude e longitude para construir
a consulta, e de `vp-04-domain`, que calcula distâncias a partir da posição do
utilizador. Motivado pelo work-item `vp-02-location`.

## Implementation

### Phase 1: Camada de localização

#### Step 1.1: Modelo e resultado

A camada não expõe `android.location.Location` para fora do pacote, para que o domínio
e os testes fiquem independentes do SDK.

| Tipo | Responsabilidade |
|---|---|
| `UserLocation` | Latitude, longitude, precisão e instante da leitura |
| `LocationResult` | Resultado selado: `Available`, `PermissionMissing`, `LocationDisabled`, `Unavailable` |
| `LocationProvider` | Interface `suspend fun currentLocation(): LocationResult` |
| `FusedLocationProvider` | Implementação sobre `FusedLocationProviderClient` |

#### Step 1.2: Obtenção da posição

| Decisão | Valor | Motivo |
|---|---|---|
| Prioridade | `PRIORITY_BALANCED_POWER_ACCURACY` | Precisão suficiente para um raio de centenas de metros |
| Granularidade | `GRANULARITY_PERMISSION_LEVEL` | Respeita a escolha entre localização aproximada e precisa |
| Idade máxima | 60 s | Aceita uma leitura recente sem ativar o GPS |
| Duração máxima | 20 s | Dá margem a um primeiro fix de GPS sem esperas indefinidas na UI |
| Fallback | `lastLocation` | Usado quando a leitura atual devolve `null` |

A `Task` do Play Services é convertida em `suspend` com `suspendCancellableCoroutine`,
cancelando o `CancellationTokenSource` quando a corrotina é cancelada. Não se acrescenta
`kotlinx-coroutines-play-services` só para isto.

Antes de contactar o Play Services o provider verifica, por esta ordem, se existe
permissão concedida e se a localização do sistema está ligada
(`LocationManagerCompat.isLocationEnabled`), devolvendo o estado correspondente em vez de
uma exceção.

#### Step 1.3: Permissões

`ACCESS_FINE_LOCATION` e `ACCESS_COARSE_LOCATION` já estão declaradas no manifesto pelo
`vp-01-foundation`; este work-item acrescenta o pedido em runtime. Basta uma das duas
estar concedida para a app funcionar, conforme a secção 13 do documento do MVP.

| Action | File | Details |
|---|---|---|
| Create | `.../location/UserLocation.kt` | Modelo de posição independente do SDK |
| Create | `.../location/LocationProvider.kt` | Interface, resultado selado e implementação Fused |
| Create | `.../location/LocationPermissions.kt` | Permissões pedidas e verificação de concessão |
| Delete | `.../location/.gitkeep` | Marcador substituído pelo código real |

Verificação da fase 1:

- [x] O provider devolve `PermissionMissing` sem permissão e `LocationDisabled` com a localização desligada
- [x] O cancelamento da corrotina cancela o pedido ao Play Services

### Phase 2: Estado e UI

#### Step 2.1: ViewModel

`LocationViewModel` expõe um `StateFlow<LocationUiState>` e recebe o `LocationProvider`
por construtor, para poder ser testado com uma implementação falsa. A instância de
produção é criada por uma `Factory` que usa o `Application` das `CreationExtras`.

| Estado | Significado | Ação oferecida |
|---|---|---|
| `Idle` | Antes da primeira tentativa | — |
| `Loading` | Leitura em curso | — |
| `Available` | Coordenadas obtidas | Atualizar |
| `PermissionRequired` | Falta permissão e ainda é possível pedir | Permitir |
| `PermissionDenied` | Recusada de forma permanente | Abrir definições da app |
| `LocationDisabled` | Localização do sistema desligada | Abrir definições de localização |
| `Unavailable` | Sem leitura possível | Tentar novamente |

Pedidos concorrentes são ignorados enquanto o estado for `Loading`.

#### Step 2.2: Ecrã

O `MainActivity` substitui o `StartScreen` da fundação por um ecrã de localização que
mostra o estado atual e a ação correspondente. A distinção entre recusa temporária e
permanente usa `shouldShowRequestPermissionRationale` após o resultado do pedido, que é
o comportamento padrão do Android.

As mensagens seguem a secção 27 do documento do MVP: texto orientado ao utilizador, sem
códigos técnicos, e sempre com uma ação de recuperação. Conforme a secção 28, o registo
de diagnóstico indica apenas o tipo de resultado e nunca as coordenadas.

Este ecrã é provisório: `vp-06-ui` substitui-o pelo mapa e pela lista, reutilizando o
mesmo `LocationViewModel`.

| Action | File | Details |
|---|---|---|
| Create | `.../location/LocationViewModel.kt` | Estados, redução de resultados e factory |
| Modify | `.../MainActivity.kt` | Ecrã de localização, pedido de permissão e ações |
| Modify | `res/values/strings.xml` | Textos dos estados e das ações |
| Modify | `gradle/libs.versions.toml` | Biblioteca `lifecycle-runtime-compose` |
| Modify | `app/build.gradle.kts` | Dependência `lifecycle-runtime-compose` |

A dependência nova é necessária para `collectAsStateWithLifecycle`, que suspende a
recolha do estado quando o ecrã não está visível. Usa a versão do Lifecycle já fixada no
catálogo.

Verificação da fase 2:

- [x] Primeira abertura pede permissão
- [x] Permissão concedida mostra coordenadas e precisão
- [x] Recusa mostra explicação e ação de recuperação

### Phase 3: Validação

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/java/pt/vcc/parking/location/LocationViewModelTest.kt` | Testes do ViewModel com provider falso |
| Modify | `app/build.gradle.kts` | `unitTests.isReturnDefaultValues` para o `android.util.Log` do ViewModel |
| Run | `gradlew.bat :app:testDebugUnitTest` | Executa os testes unitários |
| Run | `gradlew.bat :app:assembleDebug` | Confirma que o módulo compila |

Verificação da fase 3:

- [x] Cada `LocationResult` produz o `LocationUiState` esperado
- [x] Um segundo pedido durante `Loading` não duplica a chamada ao provider

## Riscos

| Risco | Detalhe | Mitigação |
|---|---|---|
| Sem Google Play Services | O Fused Location Provider não existe em dispositivos sem GMS | O provider trata a falha como `Unavailable` em vez de rebentar |
| Heurística de recusa permanente | `shouldShowRequestPermissionRationale` também devolve `false` antes do primeiro pedido | O estado só é avaliado depois do resultado do pedido |
| Localização aproximada | O utilizador pode conceder apenas `ACCESS_COARSE_LOCATION` | A precisão é mostrada na UI e `vp-03-overpass` deve tolerar raios maiores |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/parking/location/UserLocation.kt` | Create | Modelo de posição do utilizador |
| `app/src/main/java/pt/vcc/parking/location/LocationProvider.kt` | Create | Acesso ao Fused Location Provider |
| `app/src/main/java/pt/vcc/parking/location/LocationPermissions.kt` | Create | Permissões de localização e verificação |
| `app/src/main/java/pt/vcc/parking/location/LocationViewModel.kt` | Create | Estado de localização para a UI |
| `app/src/main/java/pt/vcc/parking/location/.gitkeep` | Delete | Marcador do pacote já preenchido |
| `app/src/main/java/pt/vcc/parking/MainActivity.kt` | Modify | Ecrã de localização e pedido de permissão |
| `app/src/main/res/values/strings.xml` | Modify | Textos dos estados de localização |
| `gradle/libs.versions.toml` | Modify | Declarar `lifecycle-runtime-compose` |
| `app/build.gradle.kts` | Modify | Usar `lifecycle-runtime-compose` e devolver valores por omissão nos testes |
| `app/src/test/java/pt/vcc/parking/location/LocationViewModelTest.kt` | Create | Testes do ViewModel |
