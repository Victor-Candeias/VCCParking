---
maestru: "0.4"
type: work-spec
id: vp-12-rich-details-spec
title: "Especificação da informação enriquecida dos parques"
template: implementation-plan-v1
work-item: vcc-parking-post-mvp/vp-12-rich-details
owner: developer
created: 2026-10-01
---

# Especificação da informação enriquecida dos parques

## Overview

O detalhe do MVP (`vp-06-ui`, secção 24) mostra os campos de `Parking` tal como vêm do
OSM. Esta funcionalidade transforma essa informação em respostas úteis: está aberto
agora, quanto custa, que restrições tem e que tipo de lugares existem — sem inventar
dados que o OSM não tem.

Assenta no modelo `Parking` de `vp-04-domain` e no `ParkingFormat` de `vp-06-ui`. Não
depende de nenhuma API nova: usa tags OSM que a query de `vp-03-overpass` já traz ou que
passa a trazer. Motivado pelo work-item `vp-12-rich-details`.

### Âmbito

| Dentro | Fora |
|---|---|
| Interpretação de `opening_hours` («aberto agora») | Lotação em tempo real (`vp-14-realtime`) |
| Interpretação de `fee`, `charge` e `fee:conditional` | Mudança de fonte de dados (`vp-13-data-sources`) |
| Restrições: altura, acesso, tipo de veículo | Reserva ou pagamento |
| Lugares especiais: mobilidade reduzida, elétricos | Previsão de disponibilidade |
| Filtros e ordenação na lista por estes atributos | — |
| Ligação para corrigir dados em falta no OSM | — |

## Implementation

### Phase 1: Novas tags

#### Step 1.1: Alargar a query e o modelo

A query `nwr` de `vp-03-overpass` já devolve todas as tags com `out center tags`; não é
preciso alterar a query. O que muda é o mapeamento em `ParkingMapper`.

| Tag OSM | Campo | Uso |
|---|---|---|
| `maxheight` | `maxHeightMeters` | Restrição crítica para carrinhas e tejadilhos |
| `charge` | `charge` | Valor cobrado, quando existe |
| `fee:conditional` | `feeConditional` | Gratuito fora de certos horários |
| `supervised` / `surveillance` | `supervised` | Segurança percebida |
| `covered` | `covered` | Coberto ou ao ar livre |
| `capacity:charging` | `chargingCapacity` | Lugares com carregamento elétrico |
| `capacity:parent` | `parentCapacity` | Lugares para famílias |
| `parking:condition` | `condition` | Disco, residentes, tempo máximo |
| `maxstay` | `maxStay` | Tempo máximo permitido |
| `payment:*` | `paymentMethods` | Meios de pagamento aceites |

A regra da secção 9 mantém-se e é central: um campo a `null` é informação indisponível e
nunca `false`. A UI nunca pode escrever «não tem» a partir da ausência de uma tag.

| Action | File | Details |
|---|---|---|
| Modify | `.../domain/model/Parking.kt` | Campos novos, todos opcionais |
| Modify | `.../data/remote/ParkingMapper.kt` | Mapeamento das tags novas |
| Modify | `.../data/local/ParkingEntity.kt` | Colunas novas |
| Modify | `.../data/local/ParkingEntityMapper.kt` | Conversão dos campos novos |
| Modify | `.../data/local/ParkingDatabase.kt` | Nova versão; a cache pode ser recriada |

Verificação da fase 1:

- [ ] Tags ausentes continuam a produzir `null` e nunca valores por omissão
- [ ] Valores inválidos (ex.: `maxheight=yes`) não rebentam o mapeamento
- [ ] A cache antiga é recriada sem erro

### Phase 2: Interpretação

#### Step 2.1: `opening_hours`

A sintaxe `opening_hours` do OSM é complexa (`Mo-Fr 08:00-20:00; Sa 09:00-13:00; PH off`).
Escrever um interpretador completo não é razoável; escrever um parcial que erre em
silêncio é pior.

| Caso | Comportamento |
|---|---|
| `24/7` | «Aberto 24 horas» |
| Intervalos simples por dia da semana | «Aberto agora · fecha às 20:00» |
| Sintaxe não suportada | Mostra o valor em bruto, sem afirmar aberto ou fechado |
| Tag ausente | Não mostra nada |

O princípio é explícito: perante dúvida, a app mostra o texto original em vez de
adivinhar. Dizer «aberto» a quem encontra o parque fechado é pior do que não dizer nada.

#### Step 2.2: Preço e restrições

| Entrada | Saída |
|---|---|
| `fee=no` | «Gratuito» |
| `fee=yes` + `charge=1.20 EUR/h` | «1,20 € por hora» |
| `fee=yes` sem `charge` | «Pago · valor não indicado» |
| `fee:conditional=no @ (20:00-08:00)` | «Gratuito entre as 20:00 e as 08:00» |
| `maxheight=2.1` | «Altura máxima 2,10 m» |

| Action | File | Details |
|---|---|---|
| Create | `.../domain/OpeningHours.kt` | Interpretador parcial e honesto |
| Create | `.../domain/ParkingCharge.kt` | Normalização de preço e condições |
| Create | `.../domain/ParkingRestrictions.kt` | Altura, tempo máximo e condições |
| Modify | `.../ui/ParkingFormat.kt` | Formatação dos campos novos |

Verificação da fase 2:

- [ ] `24/7` e intervalos simples são interpretados corretamente
- [ ] Sintaxe desconhecida nunca produz «aberto» ou «fechado»
- [ ] Os valores monetários respeitam a locale do dispositivo

### Phase 3: Filtros, ordenação e contribuição

| Filtro | Valores |
|---|---|
| Preço | Qualquer, gratuito |
| Estado | Qualquer, aberto agora |
| Cobertura | Qualquer, coberto |
| Altura | Sem limite conhecido, acima de um valor escolhido |
| Lugares | Mobilidade reduzida, carregamento elétrico |

Os filtros só podem excluir com base em informação explícita. Um parque sem `fee` não é
excluído pelo filtro «gratuito» — é mostrado numa secção «sem informação», porque excluir
dados em falta esconderia a maior parte dos resultados em muitas zonas.

| Action | File | Details |
|---|---|---|
| Modify | `.../domain/ParkingFilters.kt` | Filtros novos com tratamento de dados em falta |
| Create | `.../ui/filters/ParkingFilterSheet.kt` | Seleção de filtros |
| Create | `.../ui/filters/ParkingFilterChips.kt` | Filtros ativos no ecrã principal |
| Create | `.../ui/details/ContributeToOsm.kt` | Ligação para corrigir o parque no OSM |
| Modify | `.../ui/details/ParkingDetailsSheet.kt` | Secções novas do detalhe |
| Modify | `.../ui/list/ParkingList.kt` | Indicadores de preço e estado |
| Modify | `app/src/main/res/values/strings.xml` | Textos novos |

A ligação para o OSM fecha o ciclo: quando falta informação, o utilizador que está no
local é quem melhor a pode acrescentar.

Verificação da fase 3:

- [ ] Nenhum filtro exclui parques por falta de informação
- [ ] Os filtros ativos são visíveis e removíveis
- [ ] O detalhe não mostra secções vazias

### Phase 4: Validação

| Action | File | Details |
|---|---|---|
| Create | `app/src/test/java/pt/vcc/parking/domain/OpeningHoursTest.kt` | Casos suportados e não suportados |
| Create | `app/src/test/java/pt/vcc/parking/domain/ParkingChargeTest.kt` | Preço e condições |
| Create | `app/src/test/java/pt/vcc/parking/domain/ParkingFiltersTest.kt` | Dados em falta nunca excluídos |
| Create | `app/src/test/java/pt/vcc/parking/ui/ParkingFormatTest.kt` | Formatação e locale |
| Run | `gradlew.bat :app:testDebugUnitTest` | Testes unitários |
| Run | `gradlew.bat :app:assembleDebug` | Compilação |

## Riscos

| Risco | Detalhe | Mitigação |
|---|---|---|
| `opening_hours` complexo | Interpretação errada induz o utilizador em erro | Suporte parcial e valor em bruto na dúvida |
| Cobertura irregular do OSM | Muitos parques sem tags | Secção «sem informação» e ligação de contribuição |
| Filtros a esvaziar a lista | Excluir por falta de dados esconde resultados | Dados em falta nunca são excluídos |
| Preços desatualizados | O OSM não é fonte de tarifários | Mostrar a origem e a data da informação |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `app/src/main/java/pt/vcc/parking/domain/model/Parking.kt` | Modify | Campos novos opcionais |
| `app/src/main/java/pt/vcc/parking/data/remote/ParkingMapper.kt` | Modify | Mapeamento das tags novas |
| `app/src/main/java/pt/vcc/parking/data/local/ParkingEntity.kt` | Modify | Colunas novas |
| `app/src/main/java/pt/vcc/parking/data/local/ParkingEntityMapper.kt` | Modify | Conversão dos campos novos |
| `app/src/main/java/pt/vcc/parking/data/local/ParkingDatabase.kt` | Modify | Nova versão da cache |
| `app/src/main/java/pt/vcc/parking/domain/OpeningHours.kt` | Create | Interpretação de horários |
| `app/src/main/java/pt/vcc/parking/domain/ParkingCharge.kt` | Create | Preço e condições |
| `app/src/main/java/pt/vcc/parking/domain/ParkingRestrictions.kt` | Create | Altura e tempo máximo |
| `app/src/main/java/pt/vcc/parking/domain/ParkingFilters.kt` | Modify | Filtros novos |
| `app/src/main/java/pt/vcc/parking/ui/ParkingFormat.kt` | Modify | Formatação dos campos novos |
| `app/src/main/java/pt/vcc/parking/ui/filters/ParkingFilterSheet.kt` | Create | Seleção de filtros |
| `app/src/main/java/pt/vcc/parking/ui/filters/ParkingFilterChips.kt` | Create | Filtros ativos |
| `app/src/main/java/pt/vcc/parking/ui/details/ParkingDetailsSheet.kt` | Modify | Secções novas do detalhe |
| `app/src/main/java/pt/vcc/parking/ui/details/ContributeToOsm.kt` | Create | Correção de dados no OSM |
| `app/src/main/java/pt/vcc/parking/ui/list/ParkingList.kt` | Modify | Indicadores na lista |
| `app/src/main/res/values/strings.xml` | Modify | Textos novos |
| `app/src/test/java/pt/vcc/parking/domain/OpeningHoursTest.kt` | Create | Testes de horários |
| `app/src/test/java/pt/vcc/parking/domain/ParkingChargeTest.kt` | Create | Testes de preço |
| `app/src/test/java/pt/vcc/parking/domain/ParkingFiltersTest.kt` | Create | Testes dos filtros |
| `app/src/test/java/pt/vcc/parking/ui/ParkingFormatTest.kt` | Create | Testes de formatação |
