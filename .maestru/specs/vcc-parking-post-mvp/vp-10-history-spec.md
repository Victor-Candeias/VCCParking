---
maestru: "0.4"
type: work-spec
id: vp-10-history-spec
title: "Especificação do histórico de estacionamentos"
template: implementation-plan-v1
work-item: vcc-parking-post-mvp/vp-10-history
owner: developer
created: 2026-10-01
---

# Especificação do histórico de estacionamentos

## Overview

Expõe os registos terminados que `vp-08-park-save` já persiste mas que hoje ficam
invisíveis: lista, detalhe, pesquisa, apagar e exportar. Dá ao utilizador controlo sobre
os seus próprios dados de localização, o que é também um requisito de privacidade.

Depende de `vp-08-park-save`, que cria a tabela `parked_car` e nunca apaga registos ao
terminar um estacionamento. Motivado pelo work-item `vp-10-history`.

### Âmbito

| Dentro | Fora |
|---|---|
| Lista cronológica com paginação | Registo do estacionamento (`vp-08-park-save`) |
| Detalhe de um registo anterior no mapa | Lembretes (`vp-11-reminders`) |
| Apagar um registo e apagar tudo | Sincronização ou cópia de segurança na nuvem |
| Exportar para GeoJSON/CSV | Estatísticas avançadas e gráficos |
| Política de retenção configurável | — |

## Implementation

### Phase 1: Leitura e retenção

#### Step 1.1: Consulta paginada

O histórico cresce indefinidamente; carregar tudo em memória não é aceitável. A listagem
lê apenas um bloco de cada vez.

> Decisão de implementação: em vez do `PagingSource` do Room, a consulta recebe um
> `LIMIT` reativo (`observeHistory(limit)`) que o ViewModel aumenta em páginas de 30.
> Evita a dependência Paging 3 e é coerente com o resto do projeto, que também dispensa
> a biblioteca de navegação.

| Operação | Comportamento |
|---|---|
| `observeHistory(limit)` | Registos terminados, mais recentes primeiro |
| `observeCount()` | Total de registos, para saber se há mais páginas |
| `delete(id)` | Apaga o registo e devolve-o para permitir anular |
| `deleteAll()` | Apaga todos os registos terminados e respetivas fotografias |
| `applyRetention()` | Retenção automática |

Apagar um registo tem de apagar também o ficheiro da fotografia no armazenamento privado,
caso contrário a app acumula imagens órfãs. A fotografia só é removida quando a remoção
deixa de ser anulável (`discardPhoto`), para que «Anular» possa repor o registo completo.

#### Step 1.2: Retenção

| Opção | Valor | Motivo |
|---|---|---|
| Por omissão | 90 dias | Suficiente para rever hábitos sem guardar indefinidamente |
| Alternativas | 30 dias, 1 ano, sempre | O utilizador decide |
| Momento da limpeza | No arranque e após terminar um estacionamento | Evita trabalho periódico em segundo plano |

| Action | File | Details |
|---|---|---|
| Modify | `.../data/local/ParkedCarDao.kt` | Consulta paginada, apagar e retenção |
| Create | `.../data/repository/ParkingHistoryRepository.kt` | Histórico, retenção e ficheiros |
| Create | `.../data/local/HistorySettings.kt` | Preferência de retenção em `SharedPreferences` |
| Create | `.../data/local/ParkedPhotoStore.kt` | Remoção dos ficheiros de fotografia |

> Decisão de implementação: a preferência usa `SharedPreferences` exposto como `Flow`, em
> vez de DataStore, para não acrescentar uma dependência por causa de um único valor.

Verificação da fase 1:

- [x] Apagar um registo apaga a fotografia associada
- [x] O estacionamento ativo nunca aparece no histórico
- [x] A retenção não apaga o registo ativo

### Phase 2: UI do histórico

| Action | File | Details |
|---|---|---|
| Create | `.../history/HistoryViewModel.kt` | Paginação, filtros e ações |
| Create | `.../history/HistoryUiState.kt` | Estados: vazio, carregado, erro |
| Create | `.../ui/history/HistoryScreen.kt` | Lista agrupada por dia |
| Create | `.../ui/history/HistoryItemRow.kt` | Data, duração, nota e miniatura |
| Create | `.../ui/history/HistoryDetailSheet.kt` | Mapa, fotografia e ações |
| Modify | `.../ui/ParkingScreen.kt` | Entrada para o histórico |
| Modify | `.../ui/ParkingRoute.kt` | Destino do histórico |
| Modify | `.../VccParkingApplication.kt` | Criação do repositório do histórico |
| Modify | `app/src/main/res/values/strings.xml` | Textos do histórico |

A lista mostra a duração do estacionamento (`endedAtMillis - parkedAtMillis`), que é a
informação mais útil para quem revê os registos, e agrupa por dia para evitar uma lista
plana de centenas de linhas.

Verificação da fase 2:

- [x] A lista vazia explica como criar o primeiro registo
- [x] Apagar pede confirmação e permite anular com `Snackbar`
- [x] O detalhe reutiliza o mapa de `vp-06-ui` sem duplicar código

### Phase 3: Exportação e privacidade

| Formato | Uso |
|---|---|
| GeoJSON | Abrir noutras ferramentas de mapas |
| CSV | Folha de cálculo; útil para despesas |

A exportação usa o `Storage Access Framework`, sem permissão de armazenamento, e o
ficheiro é escrito no destino escolhido pelo utilizador.

| Action | File | Details |
|---|---|---|
| Create | `.../history/HistoryExporter.kt` | Serialização para GeoJSON e CSV |
| Create | `.../ui/history/HistoryPrivacySection.kt` | Retenção e «Apagar todo o histórico» |

Verificação da fase 3:

- [x] O GeoJSON exportado abre num visualizador comum
- [x] «Apagar todo o histórico» exige confirmação explícita
- [x] A exportação não inclui dados que o utilizador já apagou

### Phase 4: Validação

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/java/pt/vcc/parking/data/repository/ParkingHistoryRepositoryTest.kt` | Retenção e remoção de ficheiros |
| Create | `app/src/test/java/pt/vcc/parking/history/HistoryExporterTest.kt` | Formato GeoJSON e CSV |
| Create | `app/src/test/java/pt/vcc/parking/history/HistoryViewModelTest.kt` | Estados e ações |
| Modify | `app/src/test/java/pt/vcc/parking/data/local/FakeParkedCarDao.kt` | Fake partilhado do DAO |
| Run | `gradlew.bat :app:testDebugUnitTest` | Testes unitários |
| Run | `gradlew.bat :app:assembleDebug` | Compilação |

## Riscos

| Risco | Detalhe | Mitigação |
|---|---|---|
| Fotografias órfãs | Apagar o registo sem apagar o ficheiro | Remoção do ficheiro no mesmo repositório |
| Crescimento da base de dados | Histórico sem limite | Retenção por omissão de 90 dias |
| Privacidade | O histórico é um registo de deslocações | Apenas local, exportação explícita, apagar tudo disponível |
| Apagar por engano | Ação destrutiva | Confirmação e `Snackbar` para anular |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/parking/data/local/ParkedCarDao.kt` | Modify | Consultas do histórico e retenção |
| `app/src/main/java/pt/vcc/parking/data/local/HistorySettings.kt` | Create | Preferência de retenção |
| `app/src/main/java/pt/vcc/parking/data/local/ParkedPhotoStore.kt` | Create | Ficheiros das fotografias |
| `app/src/main/java/pt/vcc/parking/data/repository/ParkingHistoryRepository.kt` | Create | Histórico, retenção e ficheiros |
| `app/src/main/java/pt/vcc/parking/history/HistoryViewModel.kt` | Create | Estado do histórico |
| `app/src/main/java/pt/vcc/parking/history/HistoryUiState.kt` | Create | Estados da UI |
| `app/src/main/java/pt/vcc/parking/history/HistoryExporter.kt` | Create | Exportação GeoJSON e CSV |
| `app/src/main/java/pt/vcc/parking/ui/history/HistoryScreen.kt` | Create | Ecrã do histórico |
| `app/src/main/java/pt/vcc/parking/ui/history/HistoryItemRow.kt` | Create | Linha da lista |
| `app/src/main/java/pt/vcc/parking/ui/history/HistoryDetailSheet.kt` | Create | Detalhe de um registo |
| `app/src/main/java/pt/vcc/parking/ui/history/HistoryPrivacySection.kt` | Create | Retenção e remoção total |
| `app/src/main/java/pt/vcc/parking/ui/ParkingScreen.kt` | Modify | Entrada para o histórico |
| `app/src/main/java/pt/vcc/parking/ui/ParkingRoute.kt` | Modify | Destino do histórico |
| `app/src/main/java/pt/vcc/parking/VccParkingApplication.kt` | Modify | Criação do repositório |
| `app/src/main/res/values/strings.xml` | Modify | Textos do histórico |
| `app/src/test/java/pt/vcc/parking/data/local/FakeParkedCarDao.kt` | Modify | Fake partilhado do DAO |
| `app/src/test/java/pt/vcc/parking/data/repository/ParkingHistoryRepositoryTest.kt` | Create | Testes do repositório |
| `app/src/test/java/pt/vcc/parking/history/HistoryExporterTest.kt` | Create | Testes da exportação |
| `app/src/test/java/pt/vcc/parking/history/HistoryViewModelTest.kt` | Create | Testes do ViewModel |
