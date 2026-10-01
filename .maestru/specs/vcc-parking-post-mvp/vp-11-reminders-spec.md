---
maestru: "0.4"
type: work-spec
id: vp-11-reminders-spec
title: "Especificação dos lembretes de estacionamento"
template: implementation-plan-v1
work-item: vcc-parking-post-mvp/vp-11-reminders
owner: developer
created: 2026-10-01
---

# Especificação dos lembretes de estacionamento

## Overview

Avisa o utilizador antes de o tempo de estacionamento terminar e lembra-o de que o carro
continua estacionado. Resolve o custo real de esquecer um parquímetro, que é uma multa,
e não apenas uma inconveniência.

Depende de `vp-08-park-save`, que fornece `parkedAtMillis` e o estacionamento ativo.
Motivado pelo work-item `vp-11-reminders`.

### Âmbito

| Dentro | Fora |
|---|---|
| Prazo definido pelo utilizador ao estacionar | Registo do estacionamento (`vp-08-park-save`) |
| Aviso antecipado configurável | Pagamento de parquímetro dentro da app |
| Notificação persistente do estacionamento ativo | Preço real por zona (`vp-12-rich-details`) |
| Permissão de notificações e alarmes exatos | Deteção automática de saída |
| Atalhos de notificação: «Voltar ao carro», «Já saí» | — |

Pagar o estacionamento fica de fora: exige integração com operadores, contas e meios de
pagamento, que é um produto diferente do que a app é hoje.

## Implementation

### Phase 1: Agendamento

#### Step 1.1: Escolha do mecanismo

| Mecanismo | Precisão | Uso |
|---|---|---|
| `AlarmManager.setExactAndAllowWhileIdle` | Ao minuto | Fim do prazo e aviso antecipado |
| `WorkManager` | Minutos a horas | Lembrete «ainda estacionado» e limpeza |

Um aviso de parquímetro atrasado 15 minutos é inútil, por isso o prazo usa alarme exato.
Desde o Android 12 isso exige `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`; se o utilizador
recusar, a app degrada para `WorkManager` e avisa que o lembrete pode atrasar, em vez de
falhar em silêncio.

#### Step 1.2: Dados do lembrete

| Campo | Tipo | Motivo |
|---|---|---|
| `parkedCarId` | `Long` | Liga ao registo de `vp-08-park-save` |
| `expiresAtMillis` | `Long?` | Fim do prazo pago |
| `warnBeforeMillis` | `Long` | Antecedência do aviso; por omissão 15 min |
| `recurringEveryMillis` | `Long?` | Lembrete periódico «ainda estacionado» |
| `dismissed` | `Boolean` | Evita reavisar depois de o utilizador dispensar |

Terminar o estacionamento cancela todos os alarmes associados. Guardar um novo
estacionamento cancela os do anterior — a regra de estacionamento ativo único de
`vp-08-park-save` estende-se aos lembretes.

| Action | File | Details |
|---|---|---|
| Create | `.../data/local/ParkingReminderEntity.kt` | Tabela `parking_reminder` |
| Create | `.../data/local/ParkingReminderDao.kt` | Leitura e escrita dos lembretes |
| Modify | `.../data/local/ParkingDatabase.kt` | Versão 3 com migração explícita |
| Create | `.../reminder/ReminderScheduler.kt` | Agendar, cancelar e reagendar |
| Create | `.../reminder/ReminderReceiver.kt` | `BroadcastReceiver` do alarme |
| Create | `.../reminder/BootCompletedReceiver.kt` | Reagendar após reinício do dispositivo |

Os alarmes não sobrevivem a um reinício; sem o `BootCompletedReceiver` o lembrete
desaparece silenciosamente, que é o pior resultado possível para esta funcionalidade.

Verificação da fase 1:

- [ ] Terminar o estacionamento cancela os alarmes
- [ ] Os alarmes são reagendados após reinício
- [ ] Sem permissão de alarme exato, o fluxo degrada e avisa

### Phase 2: Notificações

| Notificação | Canal | Comportamento |
|---|---|---|
| Estacionamento ativo | Baixa prioridade, persistente | Mostra tempo decorrido e atalhos |
| Aviso antecipado | Alta prioridade | «Faltam 15 minutos» |
| Prazo terminado | Alta prioridade | «O tempo terminou» |

Ações na notificação: «Voltar ao carro» (abre o ecrã de `vp-09-return-route`),
«Já saí» (termina o estacionamento) e «Mais 30 min» (prolonga o prazo).

| Action | File | Details |
|---|---|---|
| Create | `.../reminder/ReminderNotifications.kt` | Canais, conteúdo e ações |
| Create | `.../reminder/ReminderActionReceiver.kt` | Trata as ações da notificação |
| Modify | `app/src/main/AndroidManifest.xml` | Recetores, `POST_NOTIFICATIONS` e alarmes exatos |
| Modify | `.../VccParkingApplication.kt` | Criação dos canais de notificação |

Verificação da fase 2:

- [ ] «Já saí» termina o estacionamento sem abrir a app
- [ ] A notificação persistente desaparece ao terminar
- [ ] Sem `POST_NOTIFICATIONS` a app continua utilizável

### Phase 3: UI

| Action | File | Details |
|---|---|---|
| Create | `.../ui/reminder/ReminderSheet.kt` | Escolha do prazo ao estacionar |
| Create | `.../ui/reminder/ReminderSettings.kt` | Antecedência e lembrete periódico |
| Modify | `.../ui/parked/ParkedCarCard.kt` | Tempo restante e ação de prolongar |
| Modify | `app/src/main/res/values/strings.xml` | Textos dos lembretes |

Prazos sugeridos: 30 min, 1 h, 2 h, 4 h e personalizado. A permissão de notificações é
pedida quando o utilizador define o primeiro lembrete, e não no arranque, porque nesse
momento o pedido tem contexto.

Verificação da fase 3:

- [ ] O prazo é opcional; guardar sem prazo continua a funcionar
- [ ] O tempo restante atualiza sem recomposição manual
- [ ] Prolongar reagenda os dois alarmes

### Phase 4: Validação

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/java/pt/vcc/parking/reminder/ReminderSchedulerTest.kt` | Agendar, cancelar e degradação |
| Create | `app/src/test/java/pt/vcc/parking/reminder/ReminderActionTest.kt` | Ações da notificação |
| Create | `app/src/androidTest/java/pt/vcc/parking/reminder/ReminderNotificationTest.kt` | Canais e conteúdo |
| Run | `gradlew.bat :app:testDebugUnitTest` | Testes unitários |
| Run | `gradlew.bat :app:assembleDebug` | Compilação |

## Riscos

| Risco | Detalhe | Mitigação |
|---|---|---|
| Restrições de fabricante | Alguns OEM matam alarmes em segundo plano | Avisar e documentar a exclusão de otimização de bateria |
| Permissão de alarme exato negada | Lembrete pode atrasar | Degradação para `WorkManager` com aviso explícito |
| Reinício do dispositivo | Alarmes perdidos | `BootCompletedReceiver` |
| Excesso de notificações | Utilizador desativa o canal | Lembrete periódico desligado por omissão |
| Expectativa errada | O utilizador confia no aviso e leva multa | Texto claro de que o aviso é indicativo |

## Desvios da implementação

Decisões tomadas durante a implementação que divergem do que está planeado acima.

| Planeado | Implementado | Porquê |
|---|---|---|
| Degradação para `WorkManager` quando o alarme exato é negado | `AlarmManager.setAndAllowWhileIdle` (inexato) | O fallback inexato dá a mesma garantia prática de entrega sem acrescentar uma dependência nova ao projeto |
| Lembrete «ainda estacionado» pelo `WorkManager` | Alarme reagendado um de cada vez pelo `ReminderReceiver` | Um periódico que sobrevivesse ao fim do estacionamento era o risco maior; reagendar a cada disparo garante que o ciclo morre com o registo |
| Escolha do prazo no momento de estacionar | Ação «Definir lembrete» no cartão do carro | Um popup a seguir a guardar o carro atrasa a ação principal; quem não quer prazo não paga nada por isso |
| `ReminderCoordinator.kt` não previsto | Criado | A notificação persistente e os alarmes têm de seguir o estado da base de dados a partir de fora da UI, que pode nem estar aberta |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/parking/data/local/ParkingReminderEntity.kt` | Create | Tabela dos lembretes |
| `app/src/main/java/pt/vcc/parking/data/local/ParkingReminderDao.kt` | Create | Acesso aos lembretes |
| `app/src/main/java/pt/vcc/parking/data/local/ParkingDatabase.kt` | Modify | Versão 3 e migração |
| `app/src/main/java/pt/vcc/parking/reminder/ReminderScheduler.kt` | Create | Agendamento dos alarmes |
| `app/src/main/java/pt/vcc/parking/reminder/ReminderReceiver.kt` | Create | Receção do alarme |
| `app/src/main/java/pt/vcc/parking/reminder/ReminderActionReceiver.kt` | Create | Ações da notificação |
| `app/src/main/java/pt/vcc/parking/reminder/BootCompletedReceiver.kt` | Create | Reagendar após reinício |
| `app/src/main/java/pt/vcc/parking/reminder/ReminderNotifications.kt` | Create | Canais e notificações |
| `app/src/main/java/pt/vcc/parking/ui/reminder/ReminderSheet.kt` | Create | Escolha do prazo |
| `app/src/main/java/pt/vcc/parking/ui/reminder/ReminderSettings.kt` | Create | Definições dos lembretes |
| `app/src/main/java/pt/vcc/parking/ui/parked/ParkedCarCard.kt` | Modify | Tempo restante e prolongar |
| `app/src/main/java/pt/vcc/parking/VccParkingApplication.kt` | Modify | Criação dos canais |
| `app/src/main/AndroidManifest.xml` | Modify | Permissões e recetores |
| `app/src/main/res/values/strings.xml` | Modify | Textos dos lembretes |
| `app/src/test/java/pt/vcc/parking/reminder/ReminderSchedulerTest.kt` | Create | Testes do agendamento |
| `app/src/test/java/pt/vcc/parking/reminder/ReminderActionTest.kt` | Create | Testes das ações |
| `app/src/androidTest/java/pt/vcc/parking/reminder/ReminderNotificationTest.kt` | Create | Testes das notificações |
