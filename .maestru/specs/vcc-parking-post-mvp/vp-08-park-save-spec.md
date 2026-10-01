---
maestru: "0.4"
type: work-spec
id: vp-08-park-save-spec
title: "Especificação do registo do local de estacionamento"
template: implementation-plan-v1
work-item: vcc-parking-post-mvp/vp-08-park-save
owner: developer
created: 2026-10-01
---

# Especificação do registo do local de estacionamento

## Overview

O MVP responde a «onde posso estacionar»; esta funcionalidade responde a «onde deixei o
carro». Acrescenta um registo persistente da posição onde o utilizador estacionou, com
data/hora e anotações opcionais, reutilizando o `LocationProvider` de `vp-02-location` e
a base de dados Room criada em `vp-05-cache`.

É a base de `vp-09-return-route` (regresso ao carro), `vp-10-history` (histórico) e
`vp-11-reminders` (lembretes), que consomem o mesmo registo. Motivado pelo work-item
`vp-08-park-save`.

### Âmbito

| Dentro | Fora |
|---|---|
| Entidade, DAO e repositório do estacionamento ativo | Cálculo de rota a pé (`vp-09-return-route`) |
| Captura da posição com precisão e timestamp | Lista de registos anteriores (`vp-10-history`) |
| Nota de texto, piso/lugar e fotografia opcional | Notificações e alarmes (`vp-11-reminders`) |
| Associação opcional ao `Parking` escolhido na lista | Sincronização com a nuvem |
| Terminar o estacionamento («Já saí») | Deteção automática de estacionamento |

A deteção automática (Bluetooth do carro, Activity Recognition) fica de fora
deliberadamente: exige permissões adicionais e produz falsos positivos. O registo é
sempre uma ação explícita do utilizador nesta fase.

## Implementation

### Phase 1: Persistência do estacionamento

#### Step 1.1: Modelo de domínio

| Campo | Tipo | Motivo |
|---|---|---|
| `id` | `Long` autogerado | Vários registos ao longo do tempo; o histórico precisa de identidade própria |
| `latitude` / `longitude` | `Double` | Posição capturada no momento de guardar |
| `accuracyMeters` | `Float?` | A UI deve mostrar a incerteza em vez de fingir precisão |
| `parkedAtMillis` | `Long` | Base dos lembretes e da ordenação do histórico |
| `endedAtMillis` | `Long?` | `null` enquanto o estacionamento está ativo |
| `note` | `String?` | Texto livre do utilizador (ex.: «piso -2, lugar 134») |
| `photoUri` | `String?` | Fotografia do local, guardada no armazenamento da app |
| `osmType` / `osmId` | `String?` / `Long?` | Liga ao `Parking` quando o registo nasce do detalhe de um parque |

Só pode existir um estacionamento ativo (`endedAtMillis IS NULL`) de cada vez. Guardar um
novo estacionamento termina automaticamente o anterior, em vez de o apagar — o histórico
de `vp-10-history` depende disso.

#### Step 1.2: Room

A base de dados `vcc-parking.db` de `vp-05-cache` usa migrações destrutivas porque a
tabela de parques é cache descartável. Esta tabela **não** é descartável: um registo
perdido é informação do utilizador perdida. A migração passa a ser explícita.

| Action | File | Details |
|---|---|---|
| Create | `.../domain/model/ParkedCar.kt` | Modelo de domínio do estacionamento |
| Create | `.../data/local/ParkedCarEntity.kt` | Tabela `parked_car` |
| Create | `.../data/local/ParkedCarDao.kt` | Inserir, terminar, observar o ativo, listar |
| Create | `.../data/local/ParkedCarEntityMapper.kt` | Conversão entidade ↔ domínio |
| Modify | `.../data/local/ParkingDatabase.kt` | Versão 2, nova entidade e migração explícita |

Verificação da fase 1:

- [ ] Guardar um estacionamento com outro ativo termina o anterior
- [ ] Existe no máximo um registo com `endedAtMillis IS NULL`
- [ ] A migração 1 → 2 preserva os dados existentes
- [ ] A conversão entidade ↔ domínio preserva todos os campos

### Phase 2: Repositório e captura da posição

#### Step 2.1: `ParkedCarRepository`

| Operação | Comportamento |
|---|---|
| `observeActive()` | `Flow<ParkedCar?>` para a UI reagir sem polling |
| `park(position, note, photoUri, parking)` | Termina o ativo e cria o novo registo |
| `updateDetails(id, note, photoUri)` | Edição depois de guardar, sem repetir a captura |
| `endActive()` | Marca `endedAtMillis`; não apaga |

O relógio entra por construtor (`now: () -> Long`), como em `vp-05-cache`, para manter os
testes determinísticos.

#### Step 2.2: Captura da posição

Reutiliza o `LocationProvider` de `vp-02-location`. Guardar não pode depender de uma
posição perfeita: dentro de um parque subterrâneo o GPS é mau ou inexistente.

| Situação | Comportamento |
|---|---|
| Posição recente e precisa | Guarda imediatamente |
| Posição imprecisa | Guarda e avisa a precisão; o utilizador pode corrigir no mapa |
| Sem posição em 10 s | Oferece marcar o ponto manualmente no mapa |
| Sem permissão | Pede permissão; se recusada, só permite marcação manual |

A correção manual no mapa é essencial e não opcional: sem ela a funcionalidade falha
exatamente onde é mais necessária.

| Action | File | Details |
|---|---|---|
| Create | `.../data/repository/ParkedCarRepository.kt` | Regras do estacionamento ativo |
| Create | `.../parked/ParkedCarViewModel.kt` | Estado de guardar, editar e terminar |
| Create | `.../parked/ParkedCarUiState.kt` | Estados: sem registo, a capturar, ativo, erro |
| Modify | `.../VccParkingApplication.kt` | Expõe o novo repositório |

Verificação da fase 2:

- [ ] Sem posição disponível, o fluxo manual permite guardar
- [ ] `observeActive` emite após `park` e após `endActive`
- [ ] `CancellationException` propaga sem ser capturada

### Phase 3: UI

| Action | File | Details |
|---|---|---|
| Create | `.../ui/parked/ParkHereButton.kt` | Ação «Estacionei aqui» no ecrã principal |
| Create | `.../ui/parked/ParkedCarCard.kt` | Cartão com posição, hora, precisão e nota |
| Create | `.../ui/parked/ParkedCarEditSheet.kt` | Edição da nota, piso/lugar e fotografia |
| Create | `.../ui/parked/ParkedCarPinPicker.kt` | Marcação manual do ponto no mapa |
| Modify | `.../ui/ParkingScreen.kt` | Integra a ação e o cartão do estacionamento ativo |
| Modify | `.../ui/details/ParkingDetailsSheet.kt` | «Estacionei neste parque» a partir do detalhe |
| Modify | `.../ui/map/ParkingMap.kt` | Marcador distinto para o carro |
| Modify | `app/src/main/res/values/strings.xml` | Textos da funcionalidade |
| Create | `app/src/main/res/drawable/ic_map_car.xml` | Marcador do carro |

A fotografia usa o seletor de media do sistema e a captura por `Intent`, sem permissão de
armazenamento. O ficheiro é copiado para o armazenamento privado da app, porque um URI de
galeria pode deixar de ser acessível depois de o utilizador apagar a foto.

Verificação da fase 3:

- [ ] O marcador do carro distingue-se visualmente dos parques
- [ ] O cartão mostra «há X minutos» e atualiza sem recomposição manual
- [ ] Terminar o estacionamento pede confirmação

### Phase 4: Validação

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/java/pt/vcc/parking/data/local/ParkedCarEntityMapperTest.kt` | Conversão nos dois sentidos |
| Create | `app/src/test/java/pt/vcc/parking/data/repository/ParkedCarRepositoryTest.kt` | Estacionamento ativo único e término |
| Create | `app/src/test/java/pt/vcc/parking/parked/ParkedCarViewModelTest.kt` | Estados e fluxo sem posição |
| Create | `app/src/androidTest/java/pt/vcc/parking/data/local/ParkingDatabaseMigrationTest.kt` | Migração 1 → 2 |
| Run | `gradlew.bat :app:testDebugUnitTest` | Testes unitários |
| Run | `gradlew.bat :app:assembleDebug` | Compilação |

## Riscos

| Risco | Detalhe | Mitigação |
|---|---|---|
| GPS fraco em parques subterrâneos | A posição guardada pode ficar dezenas de metros ao lado | Mostrar precisão e permitir corrigir no mapa |
| Perda de dados do utilizador | Migração destrutiva herdada de `vp-05-cache` | Migração explícita e teste de migração |
| Fotografias a ocupar espaço | Registos antigos acumulam imagens | Retenção alinhada com `vp-10-history` |
| Privacidade | A posição do carro é dado sensível | Apenas local, sem rede e sem registo de coordenadas |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/parking/domain/model/ParkedCar.kt` | Create | Modelo de domínio |
| `app/src/main/java/pt/vcc/parking/data/local/ParkedCarEntity.kt` | Create | Tabela Room |
| `app/src/main/java/pt/vcc/parking/data/local/ParkedCarDao.kt` | Create | Acesso aos registos |
| `app/src/main/java/pt/vcc/parking/data/local/ParkedCarEntityMapper.kt` | Create | Conversão entidade ↔ domínio |
| `app/src/main/java/pt/vcc/parking/data/local/ParkingDatabase.kt` | Modify | Versão 2 e migração explícita |
| `app/src/main/java/pt/vcc/parking/data/repository/ParkedCarRepository.kt` | Create | Regras do estacionamento ativo |
| `app/src/main/java/pt/vcc/parking/parked/ParkedCarViewModel.kt` | Create | Estado da funcionalidade |
| `app/src/main/java/pt/vcc/parking/parked/ParkedCarUiState.kt` | Create | Estados da UI |
| `app/src/main/java/pt/vcc/parking/ui/parked/ParkHereButton.kt` | Create | Ação de guardar |
| `app/src/main/java/pt/vcc/parking/ui/parked/ParkedCarCard.kt` | Create | Cartão do estacionamento ativo |
| `app/src/main/java/pt/vcc/parking/ui/parked/ParkedCarEditSheet.kt` | Create | Edição de nota e fotografia |
| `app/src/main/java/pt/vcc/parking/ui/parked/ParkedCarPinPicker.kt` | Create | Marcação manual no mapa |
| `app/src/main/java/pt/vcc/parking/ui/ParkingScreen.kt` | Modify | Integração no ecrã principal |
| `app/src/main/java/pt/vcc/parking/ui/details/ParkingDetailsSheet.kt` | Modify | Guardar a partir do detalhe |
| `app/src/main/java/pt/vcc/parking/ui/map/ParkingMap.kt` | Modify | Marcador do carro |
| `app/src/main/java/pt/vcc/parking/VccParkingApplication.kt` | Modify | Expor o repositório |
| `app/src/main/res/values/strings.xml` | Modify | Textos da funcionalidade |
| `app/src/main/res/drawable/ic_map_car.xml` | Create | Ícone do marcador do carro |
| `app/src/test/java/pt/vcc/parking/data/local/ParkedCarEntityMapperTest.kt` | Create | Testes do mapeamento |
| `app/src/test/java/pt/vcc/parking/data/repository/ParkedCarRepositoryTest.kt` | Create | Testes do repositório |
| `app/src/test/java/pt/vcc/parking/parked/ParkedCarViewModelTest.kt` | Create | Testes do ViewModel |
| `app/src/androidTest/java/pt/vcc/parking/data/local/ParkingDatabaseMigrationTest.kt` | Create | Teste da migração 1 → 2 |
