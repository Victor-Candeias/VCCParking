---
maestru: "0.4"
type: work-track
id: vcc-parking-post-mvp
title: VCC Parking — Pós-MVP
created: 2026-10-01
description: "Evoluir o VCC Parking para além da pesquisa de parques: guardar onde o utilizador estacionou, regresso ao carro, histórico, lembretes, informação mais rica dos parques, dados em tempo real e avaliação de fontes de dados alternativas."
owner: developer
status: draft
---

# vcc-parking-post-mvp: VCC Parking — Pós-MVP

Evolução do produto depois de `vcc-parking-mvp-android`. O MVP responde a «onde posso
estacionar»; este track acrescenta a resposta a «onde deixei o carro» e enriquece a
informação apresentada. Pressupõe o MVP validado em `vp-07-testing`.

## Summary

<!-- maestru:work-items-list -->
| ID | Title | Status | Created | Owner | Priority | Completed | Template | Blocked By | Spec |
|---|---|---|---|---|---|---|---|---|---|
| vp-08-park-save | Guardar a posição onde o utilizador estacionou | done | 2026-10-01 | developer | high | 2026-10-01 |  |  | [vp-08-park-save](../specs/vcc-parking-post-mvp/vp-08-park-save-spec.md) |
| vp-09-return-route | Guiar o utilizador de volta ao carro | done | 2026-10-01 | developer | high | 2026-10-01 |  | vp-08-park-save | [vp-09-return-route](../specs/vcc-parking-post-mvp/vp-09-return-route-spec.md) |
| vp-10-history | Histórico de estacionamentos | done | 2026-10-01 | developer | medium | 2026-10-01 |  | vp-08-park-save | [vp-10-history](../specs/vcc-parking-post-mvp/vp-10-history-spec.md) |
| vp-11-reminders | Lembretes e notificações de estacionamento | done | 2026-10-01 | developer | medium | 2026-10-01 |  | vp-08-park-save | [vp-11-reminders](../specs/vcc-parking-post-mvp/vp-11-reminders-spec.md) |
| vp-12-rich-details | Enriquecer a informação apresentada dos parques | done | 2026-10-01 | developer | medium | 2026-10-01 |  |  | [vp-12-rich-details](../specs/vcc-parking-post-mvp/vp-12-rich-details-spec.md) |
| vp-13-data-sources | Avaliar fontes de dados alternativas à Overpass | backlog | 2026-10-01 | developer | medium |  |  |  | [vp-13-data-sources](../specs/vcc-parking-post-mvp/vp-13-data-sources-spec.md) |
| vp-14-realtime | Integrar dados de lotação e preços em tempo real | backlog | 2026-10-01 | developer | low |  |  | vp-13-data-sources | [vp-14-realtime](../specs/vcc-parking-post-mvp/vp-14-realtime-spec.md) |
| vp-16-android-auto | Suportar Android Auto | done | 2026-10-02 | developer | high | 2026-10-02 |  |  | [vp-16-android-auto](../specs/vcc-parking-post-mvp/vp-16-android-auto-spec.md) |
| vp-17-auto-list-limit | Corrigir Android Auto sem resposta por lista de parques demasiado grande | done | 2026-10-09 | developer | critical | 2026-10-09 |  |  |  |
<!-- /maestru:work-items-list -->
