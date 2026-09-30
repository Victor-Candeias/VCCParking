---
maestru: "0.4"
type: work-spec
id: vp-03-overpass-spec
title: "Especificação da integração Overpass"
template: implementation-plan-v1
work-item: vcc-parking-mvp-android/vp-03-overpass
owner: developer
created: 2026-09-30
---

# Especificação da integração Overpass

## Overview

Preenche o pacote `data/remote` deixado por `vp-01-foundation` com o acesso à Overpass
API: construção da query `amenity=parking`, desserialização da resposta e fallback entre
vários endpoints públicos. Cobre as secções 3 a 7, 14, 15 e 28 do documento do MVP e os
critérios «Pesquisa `amenity=parking`», «Interpreta `node`, `way` e `relation`»,
«Implementa timeout» e «Faz fallback entre endpoints».

Consome a latitude e a longitude entregues por `vp-02-location`. É pré-requisito de
`vp-04-domain`, que converte os elementos devolvidos no modelo `Parking`, filtra os
privados e calcula distâncias, e de `vp-05-cache`, que acrescenta o último degrau do
fallback. Motivado pelo work-item `vp-03-overpass`.

### Âmbito

| Dentro | Fora |
|---|---|
| Query, DTO, cliente HTTP e fallback entre endpoints | Modelo `Parking` e mapeamento (`vp-04-domain`) |
| Classificação de erros e timeouts | Filtro `access=private` e distância (`vp-04-domain`) |
| Registo de diagnóstico da chamada | `ParkingRepository` e fallback para cache (`vp-05-cache`) |

O `ParkingRepository` não é criado aqui porque depende do modelo de domínio e do último
degrau do fallback (cache), que pertencem a work-items seguintes. Este work-item entrega
a camada remota completa por detrás da interface `OverpassService`, que o repositório
passa a consumir sem alterações.

## Implementation

### Phase 1: Query e DTO

#### Step 1.1: Construção da query

A query segue a secção 15 do documento do MVP e usa `nwr` para abranger `node`, `way` e
`relation`, com `out center tags` para trazer o centroide dos `ways` e `relations` sem o
custo da geometria completa.

| Decisão | Valor | Motivo |
|---|---|---|
| Timeout do servidor | 15 s | Valor documentado na secção 3; limite do lado do Overpass |
| Raio por omissão | 1000 m | Secção 20 do documento do MVP |
| Raio aceite | 100 m a 50 000 m | Evita queries degeneradas e respostas gigantes |
| Formatação | `Locale.ROOT`, 6 casas decimais | A query é texto; uma vírgula decimal produziria `400 Bad Request` |

Coordenadas fora de `-90..90` / `-180..180`, `NaN` ou infinitas são rejeitadas com
`IllegalArgumentException`; o raio é limitado ao intervalo aceite em vez de rejeitado,
porque vem de uma escolha de UI e não de uma entrada livre.

#### Step 1.2: Desserialização

Para `nodes` as coordenadas vêm em `lat`/`lon`; para `ways` e `relations` vêm em
`center` (secção 4). O DTO expõe `latitude`/`longitude` que resolvem as duas formas, e
os elementos sem qualquer coordenada são descartados pelo cliente.

| Tipo | Responsabilidade |
|---|---|
| `OverpassResponse` | Envelope com a lista de elementos |
| `OverpassElement` | `type`, `id`, `lat`/`lon`, `center`, `tags` |
| `OverpassCenter` | Centroide de `way` e `relation` |

O `Json` é configurado com `ignoreUnknownKeys` para tolerar campos como `osm3s` e
`generator`, e com `explicitNulls = false`. Todos os campos opcionais têm valor por
omissão, para que uma resposta sem `tags` não rebente a desserialização.

| Action | File | Details |
|---|---|---|
| Create | `.../data/remote/OverpassQuery.kt` | Construção e validação da query |
| Create | `.../data/remote/OverpassDto.kt` | DTO da resposta e configuração do `Json` |
| Create | `.../data/remote/OverpassApi.kt` | Interface Retrofit com `@Url` e `@Field("data")` |
| Delete | `.../data/remote/.gitkeep` | Marcador substituído pelo código real |

Verificação da fase 1:

- [x] A query gerada é idêntica à da secção 3 do documento do MVP
- [x] A query usa ponto decimal mesmo com a locale do sistema a usar vírgula
- [x] `node`, `way` com `center` e `relation` com `center` produzem coordenadas

### Phase 2: Cliente e fallback

#### Step 2.1: Endpoints e timeouts

Os endpoints ficam numa lista única e substituível por construtor, conforme a secção 5,
porque as instâncias públicas mudam com frequência.

| Decisão | Valor | Motivo |
|---|---|---|
| Timeout de ligação | 5 s | Deteta depressa um endpoint em baixo |
| Timeout total por tentativa | 10 s | Limite superior da secção 6; 3 tentativas cabem em ~30 s |
| `User-Agent` | `VccParking/<versionName> (Android)` | As instâncias Overpass exigem identificação do cliente |
| Registo HTTP | Apenas em `debug` | Evita expor a query, que contém a posição do utilizador |

#### Step 2.2: Classificação de erros

A secção 16 pede que se distinga cancelamento, timeout, rede, HTTP recuperável, HTTP não
recuperável e parsing. A classificação decide se o fallback avança para o endpoint
seguinte.

| Erro | Recuperável | Motivo |
|---|---|---|
| `CancellationException` | — | Propagada sem ser classificada |
| Timeout | Sim | Caso central da secção 6 |
| Rede (`IOException`) | Sim | Pode ser específico do endpoint |
| HTTP 400 e 414 | Não | A query é a mesma em todos os endpoints; insistir não ajuda |
| Outros HTTP (429, 4xx, 5xx) | Sim | Inclui 429, 502, 503 e 504 da secção 6 |
| Parsing | Sim | Uma instância pode devolver HTML de erro com estado 200 |
| Inesperado | Não | Falha de programação; não se mascara com uma repetição |

#### Step 2.3: Percurso do fallback

`OverpassClient` percorre os endpoints por ordem e devolve à primeira resposta válida.
Cada tentativa é registada com endpoint, duração, estado HTTP e número de elementos,
conforme a secção 28, sem nunca registar as coordenadas.

| Resultado | Significado |
|---|---|
| `OverpassResult.Success` | Elementos com coordenadas e endpoint que respondeu |
| `OverpassResult.Failure` | Lista de tentativas com o erro de cada endpoint |

O cliente não lança exceções de rede para fora: devolve `Failure`, para que
`vp-05-cache` possa decidir entre mostrar cache ou erro sem inspecionar exceções.

| Action | File | Details |
|---|---|---|
| Create | `.../data/remote/OverpassEndpoints.kt` | Lista de endpoints públicos |
| Create | `.../data/remote/OverpassResult.kt` | Resultado, tentativas e classificação de erros |
| Create | `.../data/remote/OverpassService.kt` | Interface consumida pelo repositório |
| Create | `.../data/remote/OverpassClient.kt` | Fallback, timeouts, registo e fábrica Retrofit |
| Modify | `app/build.gradle.kts` | Ativa `buildConfig` para distinguir `debug` de `release` |

Verificação da fase 2:

- [x] Um 504 no primeiro endpoint passa ao segundo
- [x] Um 400 falha de imediato sem tentar os restantes
- [x] O cancelamento da corrotina propaga `CancellationException`

### Phase 3: Validação

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/java/pt/vcc/parking/data/remote/OverpassQueryTest.kt` | Formato, locale e validação de entradas |
| Create | `app/src/test/java/pt/vcc/parking/data/remote/OverpassDtoTest.kt` | `node`, `way`, `relation`, campos ausentes |
| Create | `app/src/test/java/pt/vcc/parking/data/remote/OverpassClientTest.kt` | Fallback, erros e cancelamento |
| Run | `gradlew.bat :app:testDebugUnitTest` | Executa os testes unitários |
| Run | `gradlew.bat :app:assembleDebug` | Confirma que o módulo compila |

Os testes usam uma implementação falsa de `OverpassApi` com `retrofit2.Response`, o que
cobre a classificação HTTP sem acrescentar dependências de servidor de teste.

Verificação da fase 3:

- [x] Os cenários «funcionamento normal», «primeiro endpoint falha» e «todos falham» da secção 31
- [x] Elementos sem coordenadas são descartados

## Riscos

| Risco | Detalhe | Mitigação |
|---|---|---|
| Endpoints públicos voláteis | A lista da secção 5 pode ficar desatualizada | Lista isolada em `OverpassEndpoints` e substituível por construtor |
| Rate limiting | Um 429 repetido esgota os três endpoints | `vp-05-cache` acrescenta a cache como último degrau; secção 19 limita a frequência |
| Respostas grandes | Um raio elevado devolve muitos elementos | Raio limitado a 50 km e `out center tags` em vez de geometria |
| Privacidade | A query contém a posição do utilizador | Registo HTTP apenas em `debug` e diagnóstico sem coordenadas |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/parking/data/remote/OverpassQuery.kt` | Create | Construção e validação da query Overpass |
| `app/src/main/java/pt/vcc/parking/data/remote/OverpassDto.kt` | Create | DTO da resposta e configuração do `Json` |
| `app/src/main/java/pt/vcc/parking/data/remote/OverpassApi.kt` | Create | Interface Retrofit da Overpass API |
| `app/src/main/java/pt/vcc/parking/data/remote/OverpassEndpoints.kt` | Create | Endpoints públicos por omissão |
| `app/src/main/java/pt/vcc/parking/data/remote/OverpassResult.kt` | Create | Resultado da pesquisa e classificação de erros |
| `app/src/main/java/pt/vcc/parking/data/remote/OverpassService.kt` | Create | Contrato consumido pelo repositório |
| `app/src/main/java/pt/vcc/parking/data/remote/OverpassClient.kt` | Create | Fallback entre endpoints, timeouts e registo |
| `app/src/main/java/pt/vcc/parking/data/remote/.gitkeep` | Delete | Marcador do pacote já preenchido |
| `app/build.gradle.kts` | Modify | Ativar `buildConfig` para o registo apenas em debug |
| `app/src/test/java/pt/vcc/parking/data/remote/OverpassQueryTest.kt` | Create | Testes da query |
| `app/src/test/java/pt/vcc/parking/data/remote/OverpassDtoTest.kt` | Create | Testes da desserialização |
| `app/src/test/java/pt/vcc/parking/data/remote/OverpassClientTest.kt` | Create | Testes do fallback e dos erros |
