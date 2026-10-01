---
maestru: "0.4"
type: work-item
id: vp-11-reminders
title: Lembretes e notificações de estacionamento
created: 2026-10-01
owner: developer
priority: medium
status: done
completed: 2026-10-01
track: vcc-parking-post-mvp
blocked-by:
  - vp-08-park-save
specs:
  - vp-11-reminders-spec
---

# vp-11-reminders: Lembretes e notificações de estacionamento

## Resolução

Implementado com `AlarmManager` e dois canais de notificação distintos.

**Dados.** Tabela `parking_reminder` (base de dados na versão 3, `MIGRATION_2_3`)
com o prazo, a antecedência do aviso, a periodicidade opcional e o estado
`dismissed`. Fica separada de `parked_car` porque o lembrete é opcional e não
deve obrigar o registo do estacionamento a carregar colunas vazias.

**Agendamento.** `ReminderPlan` decide que alarmes existem e a que horas, sem
tocar no Android — é o que torna as regras testáveis em JVM.
`AlarmReminderScheduler` entrega-os, com `setExactAndAllowWhileIdle` quando há
permissão e `setAndAllowWhileIdle` quando não há. O `BootCompletedReceiver`
reagenda depois de um reinício e o `ReminderCoordinator` mantém alarmes e
notificação persistente alinhados com a base de dados mesmo com a app fechada.

**Notificações.** Canal `parking_active` (baixa prioridade, estacionamento a
decorrer, com cronómetro) e `parking_deadline` (alta prioridade, fim do prazo).
São dois para quem achar o lembrete periódico incómodo o poder desligar sem
perder o aviso que justifica a funcionalidade. As ações — voltar ao carro, já
saí e mais 30 min — funcionam a partir da barra, sem abrir a app.

**UI.** O cartão do carro mostra o tempo restante e dá acesso ao `ReminderSheet`,
onde se escolhe o prazo, a antecedência e a periodicidade. A permissão de
notificações é pedida aqui e não no arranque: neste ponto o utilizador acabou de
pedir para ser avisado.

**Desvios.** `AlarmManager` em vez de `WorkManager` para a degradação e para o
lembrete periódico, e escolha do prazo pelo cartão em vez de no momento de
estacionar. Ambos documentados na secção «Desvios da implementação» da spec.

**Validação.** 22 testes JVM novos (`ReminderSchedulerTest`, `ReminderActionTest`),
`ParkingDatabaseMigrationTest` estendido à migração 2→3 e
`ReminderNotificationTest` para os canais. `:app:compileDebugKotlin`,
`:app:testDebugUnitTest` e `:app:compileDebugAndroidTestKotlin` verdes.
