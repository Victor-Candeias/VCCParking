---
maestru: "0.4"
type: work-spec
id: vp-05-cache-spec
title: "Especificação do cache local com Room"
template: implementation-plan-v1
work-item: vcc-parking-mvp-android/vp-05-cache
owner: developer
created: 2026-09-30
---

# Especificação do cache local com Room

## Overview

Preenche os pacotes `data/local` e `data/repository` deixados por `vp-01-foundation`:
persiste os parques convertidos por `vp-04-domain` em Room e cria o `ParkingRepository`
que junta a camada remota de `vp-03-overpass` à cache. Cobre as secções 16, 17, 18 e 19
do documento do MVP e os critérios «Guarda resultados em Room» e «Usa cache se os
servidores falharem» da secção 30.

Fecha o fallback iniciado em `vp-03-overpass`: o `OverpassClient` devolve
`OverpassResult.Failure` em vez de lançar exceções precisamente para que a decisão entre
cache e erro seja tomada aqui. É pré-requisito de `vp-06-ui`, que precisa de saber se
está a mostrar cache (`fromCache` da secção 26) e de apresentar as mensagens da
secção 27.

### Âmbito

| Dentro | Fora |
|---|---|
| Entidade, DAO e base de dados Room (secção 17) | Modelo `Parking` e distância (`vp-04-domain`) |
| Política de frescura de 15 minutos (secção 18) | Botão «Pesquisar nesta área» e debounce (`vp-06-ui`) |
| `ParkingRepository` com fallback remoto e cache (secção 16) | Textos de erro e indicação visual de cache (`vp-06-ui`) |
| Resultado que distingue dados remotos de cache | `ViewModel` e `ParkingUiState` (`vp-06-ui`) |

A secção 19 pede que não se consulte o Overpass a cada movimento do mapa. O repositório
entrega a parte que lhe cabe — a janela de frescura e um `forceRefresh` explícito — e
`vp-06-ui` liga-a ao botão «Pesquisar nesta área».

## Implementation

### Phase 1: Persistência Room

#### Step 1.1: Entidade e chave

A tabela segue a secção 17. A chave é composta por `osmType` e `osmId`, como a secção
recomenda, porque o id OSM só é único dentro do tipo — é a mesma identidade que
`Parking.id` já expõe.

| Decisão | Valor | Motivo |
|---|---|---|
| Chave primária | `(osmType, osmId)` | Evita colisões entre `node`, `way` e `relation` |
| Campos de tags | Todos opcionais | Secção 9: tag ausente é informação indisponível |
| `updatedAt` | Epoch millis da gravação | Base da política de frescura da secção 18 |
| `distanceMeters` | Não persistido | Depende da posição do utilizador, que muda a cada pesquisa |

#### Step 1.2: DAO

O SQLite não calcula distâncias geográficas. A leitura usa uma caixa envolvente em
`latitude`/`longitude` e a ordenação por distância fica a cargo de `nearestFrom`
(`vp-04-domain`), que já é o passo seguinte do fluxo da secção 29.

| Operação | Comportamento |
|---|---|
| `upsertAll` | Substitui os registos existentes com o novo `updatedAt` |
| `nearby` | Devolve os registos dentro da caixa envolvente do raio pedido |
| `latestUpdateAt` | `updatedAt` mais recente dentro da caixa; alimenta a política de frescura |
| `deleteOlderThan` | Retenção; corre após cada gravação |

A caixa envolvente é calculada no Kotlin a partir do raio: a margem de latitude é
`raio / 111 320 m` e a de longitude é essa margem dividida por `cos(latitude)`. A caixa é
maior do que o círculo, pelo que pode devolver alguns parques fora do raio; a diferença é
irrelevante porque a lista é ordenada por distância antes de ser mostrada.

#### Step 1.3: Base de dados

| Decisão | Valor | Motivo |
|---|---|---|
| Nome | `vcc-parking.db` | — |
| Versão | 1 | Primeira versão do esquema |
| Migrações | Destrutivas | A tabela é cache descartável; recriar é mais barato que migrar |
| Instância | Singleton em `VccParkingApplication` | A classe foi criada em `vp-01-foundation` para esta inicialização |

| Action | File | Details |
|---|---|---|
| Create | `.../data/local/ParkingEntity.kt` | Tabela da secção 17 com chave composta |
| Create | `.../data/local/ParkingDao.kt` | Gravação, leitura por caixa envolvente e retenção |
| Create | `.../data/local/ParkingDatabase.kt` | Base de dados Room e singleton |
| Create | `.../data/local/ParkingEntityMapper.kt` | Conversão entre `ParkingEntity` e `Parking` |
| Delete | `.../data/local/.gitkeep` | Marcador substituído pelo código real |

Verificação da fase 1:

- [x] Gravar o mesmo `osmType`/`osmId` duas vezes mantém um único registo
- [x] A conversão para `Parking` e de volta preserva todos os campos
- [x] A caixa envolvente inclui um parque no limite do raio

### Phase 2: Repositório com fallback

#### Step 2.1: Interface `ParkingCache`

O repositório depende da interface `ParkingCache` e não do DAO, como na secção 16. O
motivo é prático: o Room exige contexto Android e não funciona nos testes unitários JVM,
pelo que os testes do repositório usam uma implementação falsa — o mesmo padrão com que
`vp-03-overpass` isolou o `OverpassService`.

| Tipo | Responsabilidade |
|---|---|
| `ParkingCache` | Contrato de leitura, gravação e idade dos dados |
| `RoomParkingCache` | Implementação sobre `ParkingDao`, com a caixa envolvente |

#### Step 2.2: Política de frescura

Segue a secção 18. Os dados OSM não representam ocupação em tempo real, por isso uma
cache recente evita uma chamada de rede inteira.

| Idade da cache | Comportamento |
|---|---|
| 0 a 15 minutos | Devolve a cache sem contactar o Overpass |
| Mais de 15 minutos | Tenta o Overpass; em caso de sucesso grava e devolve |
| Overpass indisponível com cache | Devolve a cache marcada como possivelmente desatualizada |
| Overpass indisponível sem cache | Devolve falha com as tentativas, para a mensagem da secção 27 |

`forceRefresh` ignora a janela de 15 minutos e serve o botão «Pesquisar nesta área» da
secção 19. O relógio entra por construtor (`now: () -> Long`) para que a janela seja
testável sem esperar.

#### Step 2.3: Resultado

A secção 26 exige que a UI saiba quando está a mostrar cache, e a secção 27 exige uma
mensagem diferente consoante exista ou não cache. O repositório devolve um resultado que
responde às duas perguntas, em vez de uma lista simples.

| Tipo | Conteúdo |
|---|---|
| `ParkingResult.Success` | Lista ordenada, `fromCache` e `updatedAtMillis` |
| `ParkingResult.Failure` | Tentativas Overpass; não havia cache utilizável |

`CancellationException` continua a propagar: o `OverpassClient` já a reemite e o
repositório não a captura.

#### Step 2.4: Ordem do fluxo

Segue a secção 29: converter, filtrar privados, calcular distância, ordenar, guardar
cache. A conversão e os três passos seguintes já existem em `vp-04-domain`
(`toParking` e `nearestFrom`), pelo que o repositório apenas os encadeia.

| Decisão | Valor | Motivo |
|---|---|---|
| O que se grava | Todos os parques convertidos, incluindo privados | Secção 10: o filtro é do cliente; mudar a política não deve obrigar a limpar a cache |
| Quando se filtra | Na leitura, tanto da rede como da cache | Garante o mesmo resultado nos dois caminhos |
| Retenção | 7 dias | Impede o crescimento indefinido sem apagar cache útil offline |
| Registo | Endpoint, origem dos dados e contagem antes e depois do filtro | Secção 28, sem registar coordenadas |

| Action | File | Details |
|---|---|---|
| Create | `.../data/local/ParkingCache.kt` | Contrato da cache |
| Create | `.../data/local/RoomParkingCache.kt` | Implementação Room do contrato |
| Create | `.../data/repository/ParkingResult.kt` | Resultado com origem e idade dos dados |
| Create | `.../data/repository/ParkingRepository.kt` | Fallback remoto, cache e política de frescura |
| Delete | `.../data/repository/.gitkeep` | Marcador substituído pelo código real |
| Modify | `.../VccParkingApplication.kt` | Expõe a base de dados e o repositório à UI |

Verificação da fase 2:

- [x] Cache com 5 minutos evita a chamada Overpass; com `forceRefresh` não evita
- [x] Overpass com sucesso grava a cache e devolve `fromCache = false`
- [x] Todos os endpoints a falhar com cache devolve `fromCache = true`
- [x] Todos os endpoints a falhar sem cache devolve `Failure`
- [x] Parques `access=private` não aparecem em nenhum dos caminhos

### Phase 3: Validação

Não são precisas dependências novas: o Room e o `kotlinx-coroutines-test` já foram
declarados em `vp-01-foundation`.

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/java/pt/vcc/parking/data/local/ParkingEntityMapperTest.kt` | Conversão nos dois sentidos |
| Create | `app/src/test/java/pt/vcc/parking/data/repository/ParkingRepositoryTest.kt` | Frescura, fallback, filtro e cancelamento |
| Create | `app/src/androidTest/java/pt/vcc/parking/data/local/ParkingDaoTest.kt` | DAO em base de dados em memória |
| Run | `gradlew.bat :app:testDebugUnitTest` | Executa os testes unitários |
| Run | `gradlew.bat :app:assembleDebug` | Confirma a geração de código do Room pelo KSP |

O teste do DAO é instrumentado porque o Room precisa de um contexto Android; os testes do
repositório ficam na JVM graças à interface `ParkingCache`.

Verificação da fase 3:

- [x] Os cenários «Funcionamento normal», «Todos falham» e «Primeira utilização sem Internet» da secção 31

## Riscos

| Risco | Detalhe | Mitigação |
|---|---|---|
| Room indisponível em testes JVM | O repositório ficaria sem testes unitários | Interface `ParkingCache` com implementação falsa nos testes |
| Caixa envolvente perto dos polos ou do antimeridiano | `cos(latitude)` tende para zero e a longitude dá a volta | Margem de longitude limitada e caixa tratada como aproximação, com a ordenação por distância a corrigir |
| Cache a crescer sem limite | Pesquisas em muitos locais acumulam registos | Retenção de 7 dias aplicada após cada gravação |
| Cache antiga apresentada como atual | O utilizador pode decidir com dados velhos | `fromCache` e `updatedAtMillis` no resultado, para a mensagem da secção 27 |
| Esquema a mudar em work-items seguintes | Uma migração mal feita apaga dados | Migrações destrutivas assumidas: a tabela é cache, não fonte de verdade |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/parking/data/local/ParkingEntity.kt` | Create | Tabela da secção 17 com chave composta |
| `app/src/main/java/pt/vcc/parking/data/local/ParkingDao.kt` | Create | Gravação, leitura por caixa envolvente e retenção |
| `app/src/main/java/pt/vcc/parking/data/local/ParkingDatabase.kt` | Create | Base de dados Room e singleton |
| `app/src/main/java/pt/vcc/parking/data/local/ParkingEntityMapper.kt` | Create | Conversão entre entidade e modelo de domínio |
| `app/src/main/java/pt/vcc/parking/data/local/ParkingCache.kt` | Create | Contrato de cache usado pelo repositório |
| `app/src/main/java/pt/vcc/parking/data/local/RoomParkingCache.kt` | Create | Implementação Room do contrato |
| `app/src/main/java/pt/vcc/parking/data/local/.gitkeep` | Delete | Marcador do pacote já preenchido |
| `app/src/main/java/pt/vcc/parking/data/repository/ParkingResult.kt` | Create | Resultado com origem e idade dos dados |
| `app/src/main/java/pt/vcc/parking/data/repository/ParkingRepository.kt` | Create | Fallback remoto, cache e política de frescura |
| `app/src/main/java/pt/vcc/parking/data/repository/.gitkeep` | Delete | Marcador do pacote já preenchido |
| `app/src/main/java/pt/vcc/parking/VccParkingApplication.kt` | Modify | Expõe base de dados e repositório |
| `app/src/test/java/pt/vcc/parking/data/local/ParkingEntityMapperTest.kt` | Create | Testes da conversão |
| `app/src/test/java/pt/vcc/parking/data/repository/ParkingRepositoryTest.kt` | Create | Testes de frescura, fallback e filtro |
| `app/src/androidTest/java/pt/vcc/parking/data/local/ParkingDaoTest.kt` | Create | Testes do DAO em memória |
