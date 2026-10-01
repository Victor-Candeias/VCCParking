---
maestru: "0.4"
type: work-spec
id: vp-13-data-sources-spec
title: "Avaliação de fontes de dados de estacionamento"
template: research-v1
work-item: vcc-parking-post-mvp/vp-13-data-sources
owner: developer
created: 2026-10-01
---

# Avaliação de fontes de dados de estacionamento

## Question

A Overpass API foi escolhida em `vp-03-overpass` sem comparação formal com alternativas:
é gratuita, aberta e não exige chave, o que a torna adequada a um MVP. A questão agora é
se continua a ser a escolha certa à medida que o produto cresce, ou se deve ser
complementada ou substituída.

**Pergunta:** qual deve ser a fonte (ou combinação de fontes) de dados de parques de
estacionamento do VCC Parking depois do MVP?

### Critérios de uma resposta suficiente

| Critério | O que tem de ficar decidido |
|---|---|
| Cobertura | Densidade e qualidade de parques nas zonas-alvo, medida e não assumida |
| Riqueza de dados | Preço, horários, altura, lotação — e qual delas cada fonte entrega |
| Custo | Modelo de preços e custo estimado por utilizador ativo |
| Licenciamento | Se os dados podem ser guardados em cache e exibidos como a app faz hoje |
| Esforço | Trabalho de integração e impacto em `OverpassService` e `ParkingRepository` |
| Risco | Dependência de fornecedor, limites de utilização e condições de rescisão |

### Fora de âmbito

- Dados de lotação em tempo real — é o objeto de `vp-14-realtime`, que depende desta
  decisão
- Serviços de routing — tratados em `vp-09-return-route`
- Implementação da fonte escolhida — produzirá um work-item próprio

### Time-box

3 dias, incluindo a medição de cobertura.

## Research

### Opções a avaliar

| Abordagem | Prós | Contras | Esforço | Risco |
|---|---|---|---|---|
| **Manter Overpass** | Sem custos, sem chave, dados abertos, já integrada | Cobertura irregular, instâncias públicas limitadas, sem lotação | Nulo | Médio: dependência de instâncias comunitárias |
| **Overpass + instância própria** | Controlo total, sem limites de terceiros | Custo de infraestrutura e manutenção de replicação OSM | Alto | Baixo no serviço, alto na operação |
| **Google Places** | Cobertura e qualidade elevadas, dados normalizados | Custo por pedido, restrições de cache e de exibição fora dos mapas Google | Médio | Alto: custo cresce com os utilizadores |
| **Mapbox Search** | Boa integração com mapas, base OSM enriquecida | Requer token, limites no plano gratuito | Médio | Médio |
| **HERE Places** | POIs de qualidade, dados de mobilidade | Requer conta e chave, modelo de preços menos transparente | Médio | Médio |
| **TomTom Search** | POIs e dados de estacionamento, integração com tráfego | Requer chave, custos acima do plano gratuito | Médio | Médio |
| **Open data municipal** | Dados oficiais: tarifas, horários e por vezes lotação | Cobertura só nas cidades que publicam, formatos heterogéneos | Alto por cidade | Baixo nos dados, alto na manutenção |
| **Híbrido: OSM + open data** | Cobertura ampla com precisão oficial onde existe | Fusão e desduplicação de registos é trabalho não trivial | Alto | Médio |

### Evidência a recolher

1. **Medição de cobertura.** Escolher 5 zonas de teste (centro urbano denso, periferia,
   cidade média, zona industrial, zona rural) e contar os parques devolvidos por cada
   fonte, comparando com uma verificação manual numa amostra.
2. **Riqueza de dados.** Para a mesma amostra, medir a percentagem de parques com
   preço, horários, capacidade e altura em cada fonte.
3. **Licenciamento.** Ler os termos de cada fornecedor com duas perguntas concretas: é
   permitido guardar em cache local como `vp-05-cache` faz, e é permitido mostrar os
   dados sobre tiles que não sejam do próprio fornecedor.
4. **Custo.** Estimar pedidos por utilizador ativo por mês a partir do comportamento
   real da app (pesquisa por ação explícita, janela de frescura de 15 minutos) e aplicar
   as tabelas de preços.
5. **Limites das instâncias públicas Overpass.** Medir a taxa de erro e a latência
   observadas ao longo de uma semana, para quantificar o risco atual em vez de o supor.

### Pontos de decisão conhecidos à partida

- A arquitetura já isola a fonte atrás de `OverpassService` e `ParkingRepository`
  (`vp-03-overpass`, `vp-05-cache`). Trocar ou acrescentar uma fonte não deve obrigar a
  tocar na UI — isso reduz muito o custo de mudar de ideias mais tarde.
- A restrição de cache é eliminatória: várias APIs comerciais proíbem armazenamento
  prolongado, o que entra em conflito direto com o funcionamento offline de
  `vp-05-cache`. Uma fonte que o proíba obriga a repensar o modo offline.
- Um modelo pago por pedido transfere o risco de custo para o crescimento da app, o que
  não é compatível com um produto sem receita.

## Recommendation

<!-- A preencher no fim da investigação. -->

A recomendação deve indicar explicitamente:

- A fonte primária e, se aplicável, a secundária, com a regra de precedência entre elas
- Se o modo offline de `vp-05-cache` se mantém inalterado
- O impacto em `vp-14-realtime`, que depende desta decisão
- Os work-items de implementação a criar

### Hipótese de trabalho

A hipótese inicial, a confirmar ou refutar com os dados recolhidos, é um modelo híbrido:
manter o OSM/Overpass como base de cobertura, porque é a única fonte que permite cache
offline sem restrições de licenciamento, e acrescentar open data municipal nas cidades
onde exista, para tarifas e horários fiáveis. As APIs comerciais ficariam como opção de
último recurso, apenas se a medição de cobertura mostrar lacunas que o OSM não resolve.

Esta hipótese é explicitamente provisória: existe para ser testada pela medição de
cobertura, não para a enviesar.
