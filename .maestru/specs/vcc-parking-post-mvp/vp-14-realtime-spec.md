---
maestru: "0.4"
type: work-spec
id: vp-14-realtime-spec
title: "Avaliação de dados de lotação e preços em tempo real"
template: research-v1
work-item: vcc-parking-post-mvp/vp-14-realtime
owner: developer
created: 2026-10-01
---

# Avaliação de dados de lotação e preços em tempo real

## Question

O OSM descreve parques, não o seu estado. A app sabe que um parque existe, mas não sabe
se tem lugares livres — que é frequentemente a única coisa que o utilizador quer saber
quando já está a circular à procura de lugar.

**Pergunta:** é viável mostrar lotação e tarifas atualizadas no VCC Parking, e com que
fontes, cobertura e custo?

### Critérios de uma resposta suficiente

| Critério | O que tem de ficar decidido |
|---|---|
| Disponibilidade | Que operadores e municípios publicam estado em tempo real nas zonas-alvo |
| Formato | Se há normas reutilizáveis ou se cada fonte exige um adaptador próprio |
| Frequência | Com que periodicidade os dados são atualizados e qual a latência real |
| Fiabilidade | Qual o erro típico entre o publicado e o observado no local |
| Custo e licença | Condições de acesso, limites e permissão de exibição |
| Arquitetura | Como integrar sem quebrar o modo offline de `vp-05-cache` |

### Fora de âmbito

- Escolha da fonte base de parques — é o objeto de `vp-13-data-sources`, que bloqueia
  este trabalho
- Reserva e pagamento de lugar
- Previsão de disponibilidade por modelos preditivos
- Contagem própria por sensores ou crowdsourcing

### Time-box

3 dias.

## Research

### Opções a avaliar

| Abordagem | Prós | Contras | Esforço | Risco |
|---|---|---|---|---|
| **Open data municipal** | Gratuito, oficial, por vezes com lotação ao minuto | Só algumas cidades; um formato por cidade | Alto por cidade | Baixo nos dados |
| **Norma DATEX II** | Norma europeia para dados de tráfego e estacionamento | Complexa e com perfis diferentes por país | Alto | Médio |
| **GBFS / APDS** | Normas abertas de mobilidade e estacionamento | Adoção ainda limitada para parques | Médio | Médio |
| **APIs de operadores** | Dados diretos da fonte, incluindo tarifas | Um contrato e um adaptador por operador | Alto | Alto: dependência comercial |
| **Agregadores comerciais** | Uma única integração para muitos operadores | Custo por pedido, cobertura variável | Médio | Alto: custo e dependência |
| **Não fazer nada** | Sem custo, sem risco, sem promessas por cumprir | A app não responde à pergunta mais frequente | Nulo | Baixo |

«Não fazer nada» está na tabela deliberadamente: é um resultado aceitável se a cobertura
medida for baixa. Mostrar lotação em tempo real em 3 % dos parques pode ser pior do que
não a mostrar, porque cria uma expectativa que a app não cumpre na maioria dos casos.

### Evidência a recolher

1. **Inventário de fontes.** Levantar portais de open data e operadores nas zonas-alvo,
   registando formato, frequência e licença.
2. **Cobertura efetiva.** Calcular a percentagem de parques da app que teria estado em
   tempo real, por zona. É este número que decide se a funcionalidade vale a pena.
3. **Fiabilidade.** Comparar, numa amostra, o valor publicado com a observação no local,
   para medir o erro real e não o prometido.
4. **Desduplicação.** Avaliar a dificuldade de ligar um parque de uma fonte oficial ao
   correspondente nó OSM: nome e coordenadas raramente coincidem exatamente.
5. **Impacto arquitetural.** Definir como o estado em tempo real coexiste com a cache de
   `vp-05-cache`, que assume dados estáveis durante 15 minutos.

### Restrições conhecidas à partida

- A política de frescura de 15 minutos de `vp-05-cache` é incompatível com lotação em
  tempo real. O estado teria de viver fora dessa cache, com uma janela própria da ordem
  dos minutos, e ser claramente datado na UI.
- A secção 9 do documento do MVP — ausência de dados nunca é apresentada como negativa —
  aplica-se aqui com mais força: um parque sem dados de lotação não pode parecer cheio
  nem vazio.
- A desduplicação entre fontes oficiais e OSM é provavelmente o maior custo escondido
  desta funcionalidade, e não a integração das APIs em si.

## Recommendation

<!-- A preencher no fim da investigação. -->

A recomendação deve indicar explicitamente:

- Se a funcionalidade avança, avança apenas em cidades-piloto, ou não avança
- A cobertura mínima que justifica avançar, definida **antes** de ver os resultados
- Como o estado em tempo real é datado e distinguido dos dados estáticos na UI
- Os work-items de implementação a criar, se aplicável

### Hipótese de trabalho

A hipótese inicial é que a cobertura será suficiente apenas em algumas cidades com open
data maduro, o que aponta para uma abordagem por cidade-piloto, com um adaptador por
fonte atrás de uma interface comum, e um indicador de lotação que só aparece quando há
dados recentes.

Como limiar prévio, propõe-se que a funcionalidade só avance se pelo menos 30 % dos
parques de uma cidade-alvo tiverem estado em tempo real fiável. Abaixo disso, a opção
recomendada é não avançar e reavaliar quando houver mais fontes disponíveis.
