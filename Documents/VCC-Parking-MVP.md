# VCC Parking --- MVP Android

## 1. Objetivo

O **VCC Parking** é uma aplicação Android para localizar parques de
estacionamento próximos do utilizador, usando dados do **OpenStreetMap
(OSM)** através da **Overpass API**.

O objetivo do MVP é validar rapidamente a utilidade da aplicação sem
depender, numa primeira fase, de um backend próprio.

### Funcionalidades do MVP

-   Obter a localização atual do utilizador.
-   Procurar parques num raio configurável.
-   Mostrar os parques num mapa e numa lista.
-   Calcular e mostrar a distância.
-   Mostrar informação OSM disponível: nome, tipo, capacidade, operador,
    acesso, pago/grátis, horários, lugares PMR, zona, cor da zona,
    telefone e website.
-   Abrir navegação até ao parque.
-   Excluir parques explicitamente privados.
-   Ter fallback entre vários servidores Overpass.
-   Manter cache local para continuar a apresentar resultados quando o
    Overpass estiver indisponível.

## 2. Fora do MVP

Para manter a primeira versão simples, ficam para fases posteriores:

-   disponibilidade de lugares em tempo real;
-   reservas e pagamentos;
-   integração com parquímetros;
-   histórico e alertas de estacionamento;
-   preços calculados pela aplicação;
-   autenticação;
-   backend próprio;
-   crowdsourcing;
-   Android Auto.

## 3. Fonte de dados

O **OpenStreetMap** contém informação geográfica colaborativa sobre
estacionamento. A pesquisa será feita através da **Overpass API**.

Elemento principal:

``` text
amenity=parking
```

Como um parque pode ser um `node`, `way` ou `relation`, a query utiliza
`nwr`.

### Query base

``` overpass
[out:json][timeout:15];
nwr["amenity"="parking"](around:1000,38.7253,-9.1500);
out center tags;
```

-   `1000`: raio em metros.
-   `38.7253`: latitude.
-   `-9.1500`: longitude.

A app substitui as coordenadas pela localização atual.

## 4. Porque usar `out center tags`

Para o MVP interessam principalmente identificador, coordenadas e tags.
Pedir a geometria completa pode aumentar muito a resposta.

Para `ways` e `relations`, as coordenadas podem surgir em:

``` json
"center": {
  "lat": 38.123,
  "lon": -9.123
}
```

Para `nodes`, surgem diretamente como `lat` e `lon`. O parser deve
suportar ambos.

## 5. Instabilidade do Overpass

As instâncias públicas do Overpass não devem ser tratadas como serviços
com SLA. Podem ocorrer timeout, HTTP 429, 500, 502, 503 e 504.

Por isso, o MVP não deve depender de apenas um endpoint.

``` kotlin
val overpassEndpoints = listOf(
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
)
```

Os endpoints públicos podem mudar ou ficar indisponíveis, pelo que devem
ficar numa configuração fácil de alterar.

## 6. Estratégia de fallback

``` text
Pesquisar estacionamento
        |
        v
Endpoint A
        |
     sucesso? ---- sim ----> processar resposta
        |
       não
        v
Endpoint B
        |
     sucesso? ---- sim ----> processar resposta
        |
       não
        v
Endpoint C
        |
     sucesso? ---- sim ----> processar resposta
        |
       não
        v
Cache local
        |
     existe?
      /   \
    sim   não
     |     |
 mostrar  erro amigável
 cache
```

Usar timeout de aproximadamente **5--10 segundos por tentativa**. O
fallback deve ocorrer pelo menos para timeout, 429, 502, 503 e 504,
podendo abranger outros erros 5xx.

## 7. Teste manual

``` bat
curl --max-time 20 -X POST "https://maps.mail.ru/osm/tools/overpass/api/interpreter" --data-urlencode "data=[out:json][timeout:15];nwr[amenity=parking](around:1000,38.7253,-9.1500);out center tags;"
```

## 8. Modelo interno

Não espalhar tags OSM diretamente pela UI. Criar um modelo interno:

``` kotlin
data class Parking(
    val osmId: Long,
    val osmType: String,
    val latitude: Double,
    val longitude: Double,
    val name: String?,
    val parkingType: String?,
    val capacity: Int?,
    val operator: String?,
    val access: String?,
    val fee: String?,
    val openingHours: String?,
    val disabledCapacity: Int?,
    val zone: String?,
    val zoneColour: String?,
    val phone: String?,
    val website: String?,
    val distanceMeters: Double? = null
)
```

## 9. Tags OSM úteis

  Tag OSM                         Utilização
  ------------------------------- --------------------------
  `name`                          Nome
  `parking`                       Tipo de estacionamento
  `capacity`                      Capacidade
  `operator`                      Operador
  `access`                        Tipo de acesso
  `fee`                           Indicação de pago/grátis
  `opening_hours`                 Horário
  `capacity:disabled`             Lugares PMR
  `zone`                          Zona
  `zone:colour`                   Cor da zona
  `phone` / `contact:phone`       Telefone
  `website` / `contact:website`   Website
  `covered`                       Coberto
  `surface`                       Piso
  `supervised`                    Vigilância
  `lit`                           Iluminação

Nem todos os parques têm todas as tags. Uma tag ausente significa
**informação não disponível**, não `false`.

## 10. Estacionamentos privados

No MVP, excluir parques explicitamente privados:

``` kotlin
parking.access != "private"
```

Pode ser feito no cliente para manter acesso à resposta original. Outros
valores de `access` não devem ser automaticamente interpretados como
públicos sem analisar o respetivo significado.

## 11. Arquitetura

``` text
UI
 |
ViewModel
 |
ParkingRepository
 |               \
 |                \
OverpassService   ParkingCache
 |
Overpass API
```

Estrutura sugerida:

``` text
pt.vcc.parking
├── data
│   ├── local
│   │   ├── ParkingDao.kt
│   │   ├── ParkingEntity.kt
│   │   └── ParkingDatabase.kt
│   ├── remote
│   │   ├── OverpassApi.kt
│   │   ├── OverpassDto.kt
│   │   └── OverpassClient.kt
│   └── repository
│       └── ParkingRepository.kt
├── domain
│   └── model
│       └── Parking.kt
├── location
│   └── LocationProvider.kt
├── ui
│   ├── map
│   ├── list
│   └── details
└── MainActivity.kt
```

## 12. Stack

``` text
Kotlin
Jetpack Compose
ViewModel
Coroutines
Retrofit + OkHttp
kotlinx.serialization ou Moshi
Room
Google Fused Location Provider
Mapa compatível com a solução escolhida
```

## 13. Permissões Android

``` xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.INTERNET" />
```

A localização deve ser pedida em runtime. Se for recusada, a app pode
permitir escolher manualmente uma zona ou explicar que a localização é
necessária para procurar parques próximos.

## 14. Retrofit

``` kotlin
interface OverpassApi {
    @FormUrlEncoded
    @POST
    suspend fun searchParking(
        @Url endpoint: String,
        @Field("data") query: String
    ): OverpassResponse
}
```

Também é possível criar um cliente Retrofit por endpoint.

## 15. Construção da query

``` kotlin
fun buildParkingQuery(
    latitude: Double,
    longitude: Double,
    radiusMeters: Int
): String {
    return "[out:json][timeout:15];" +
        "nwr[\"amenity\"=\"parking\"]" +
        "(around:$radiusMeters,$latitude,$longitude);" +
        "out center tags;"
}
```

Latitude, longitude e raio devem ser valores numéricos validados.

## 16. Repository com fallback

``` kotlin
class ParkingRepository(
    private val api: OverpassApi,
    private val cache: ParkingCache
) {
    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
    )

    suspend fun getNearbyParking(
        latitude: Double,
        longitude: Double,
        radius: Int
    ): List<Parking> {
        val query = buildParkingQuery(latitude, longitude, radius)

        for (endpoint in endpoints) {
            try {
                val response = api.searchParking(endpoint, query)
                val parking = response.toDomain()
                    .filter { it.access != "private" }

                cache.save(parking)
                return parking
            } catch (ex: Exception) {
                // Registar e tentar o endpoint seguinte.
            }
        }

        return cache.getNearby(latitude, longitude, radius)
    }
}
```

Na versão final, distinguir cancelamento de coroutine, timeout, erro de
rede, HTTP recuperável, HTTP não recuperável e parsing.
`CancellationException` deve ser propagada.

## 17. Cache com Room

Tabela possível:

``` text
parking
-----------------------------
osmId
osmType
latitude
longitude
name
parkingType
capacity
operator
access
fee
openingHours
disabledCapacity
zone
zoneColour
phone
website
updatedAt
```

Chave recomendada:

``` text
osmType + osmId
```

Assim evitam-se colisões entre IDs de tipos OSM diferentes.

## 18. Política de cache

``` text
0–15 minutos:
    cache recente

>15 minutos:
    tentar atualizar via Overpass

Overpass indisponível:
    usar cache existente e indicar
    que os dados podem estar desatualizados
```

Os dados OSM não representam ocupação em tempo real, portanto não é
necessário atualizar constantemente.

## 19. Evitar consultas excessivas

Não consultar Overpass em cada pequeno movimento do mapa.

Para o MVP, usar preferencialmente:

**Pesquisar nesta área**

Alternativamente, aplicar debounce de 500--1000 ms e só pesquisar após
deslocação significativa.

## 20. Raio

Opções iniciais:

``` text
500 m
1 km
2 km
5 km
```

Default sugerido: **1 km**.

## 21. Distância

``` kotlin
val result = FloatArray(1)

Location.distanceBetween(
    userLat,
    userLon,
    parkingLat,
    parkingLon,
    result
)

val distanceMeters = result[0]
```

Ordenação:

``` kotlin
parking.sortedBy { it.distanceMeters }
```

## 22. Ecrã principal

``` text
┌─────────────────────────────────┐
│ VCC Parking                     │
├─────────────────────────────────┤
│ [ Procurar nesta área ]         │
│                                 │
│             MAPA                │
│                                 │
│       P      P          P       │
│             ●                   │
│                                 │
├─────────────────────────────────┤
│ 12 parques encontrados          │
│ [500m] [1km] [2km] [5km]       │
└─────────────────────────────────┘
```

`●` = utilizador. `P` = parque.

## 23. Lista

``` text
Parque Saldanha Residence
350 m
Pago · Coberto
120 lugares

Parque ...
620 m
Informação de preço não disponível
```

Ordenação inicial: distância crescente.

## 24. Detalhe

Mostrar apenas informação disponível:

``` text
Nome
Distância
Capacidade
Tipo
Operador
Acesso
Pago/Grátis
Horário
Lugares PMR
Zona
Telefone
Website

[ Navegar ]
```

Nunca inventar dados ausentes no OSM.

## 25. Navegação externa

``` kotlin
val uri = Uri.parse("geo:$lat,$lon?q=$lat,$lon")
val intent = Intent(Intent.ACTION_VIEW, uri)
context.startActivity(intent)
```

Não é necessário implementar navegação GPS dentro da app no MVP.

## 26. Estados da UI

``` kotlin
sealed interface ParkingUiState {
    data object Loading : ParkingUiState

    data class Success(
        val parking: List<Parking>,
        val fromCache: Boolean
    ) : ParkingUiState

    data class Error(
        val message: String
    ) : ParkingUiState
}
```

A UI deve conseguir indicar quando está a mostrar cache.

## 27. Mensagens de erro

Evitar:

``` text
HTTP 504 Gateway Timeout
```

Preferir:

``` text
Não foi possível atualizar os parques neste momento.
```

Com cache:

``` text
Não foi possível atualizar os dados.
A mostrar a última informação disponível.
```

Adicionar **Tentar novamente**.

## 28. Logging

Durante o desenvolvimento registar:

-   endpoint usado;
-   duração da chamada;
-   HTTP status;
-   quantidade de elementos;
-   quantidade após filtros;
-   fallback;
-   utilização de cache;
-   erros de parsing.

Evitar persistir desnecessariamente a localização precisa do utilizador
nos logs.

## 29. Fluxo completo

``` text
Abrir aplicação
      |
      v
Pedir localização
      |
      v
Obter coordenadas
      |
      v
Consultar cache
      |
      v
Pesquisar Overpass
      |
      +--> A --falha--> B --falha--> C
                              |
                    +---------+---------+
                    |                   |
                  sucesso             falha
                    |                   |
                    v                   v
                 converter          usar cache
                    |
                    v
             filtrar privados
                    |
                    v
             calcular distância
                    |
                    v
            ordenar por distância
                    |
                    v
               guardar cache
                    |
                    v
              mapa + lista
```

## 30. Critérios de conclusão

-   [ ] Obtém localização.
-   [ ] Pesquisa `amenity=parking`.
-   [ ] Interpreta `node`, `way` e `relation`.
-   [ ] Mostra parques no mapa.
-   [ ] Mostra lista.
-   [ ] Calcula distância.
-   [ ] Ordena por distância.
-   [ ] Mostra detalhes disponíveis.
-   [ ] Exclui `access=private`.
-   [ ] Abre navegação externa.
-   [ ] Implementa timeout.
-   [ ] Faz fallback entre endpoints.
-   [ ] Guarda resultados em Room.
-   [ ] Usa cache se os servidores falharem.
-   [ ] Não inventa informação OSM inexistente.
-   [ ] Tem loading, sucesso, cache e erro.
-   [ ] Foi testada com rede normal, lenta e sem Internet.

## 31. Testes essenciais

### Funcionamento normal

Endpoint A responde → mostrar resultados → guardar cache.

### Primeiro endpoint falha

A falha → B responde → mostrar resultados.

### Todos falham

A, B e C falham → mostrar cache.

### Primeira utilização sem Internet

Sem cache → mostrar mensagem de erro.

### Parque privado

`access=private` → não mostrar.

### Dados incompletos

Sem `name`, `capacity` ou `fee` → a aplicação continua funcional.

### Way/relation

Sem `lat/lon` direto e com `center` → usar `center.lat` e `center.lon`.

## 32. Evolução para produção

``` text
VCC Parking Android
        |
        v
VCC Parking API
        |
   +----+----------------+
   |                     |
   v                     v
Cache/BD              OSM/Overpass
   |
   +--> futuras fontes
```

Um backend próprio permitirá controlar cache, reduzir chamadas públicas,
normalizar dados, combinar fontes, aplicar limites, monitorizar falhas e
trocar fornecedores sem atualizar a app.

## 33. Evoluções futuras

-   disponibilidade em tempo real;
-   integração com operadores;
-   preços e zonas;
-   parquímetros;
-   favoritos;
-   histórico;
-   guardar localização do carro;
-   alertas;
-   estacionamento gratuito próximo;
-   filtros avançados;
-   pesquisa por destino;
-   Android Auto;
-   backend próprio;
-   notificações;
-   crowdsourcing.

## 34. Configuração recomendada para a primeira versão

``` text
Aplicação:       Android / Kotlin
UI:              Jetpack Compose
Localização:     Fused Location Provider
Dados:           OpenStreetMap
Consulta:        Overpass API
Rede:            Retrofit + OkHttp
Persistência:    Room
Overpass:        3 endpoints + fallback
Timeout:         5–10 s por endpoint
Raio inicial:    1 km
Atualização:     "Pesquisar nesta área"
Filtro:          excluir access=private
Ordenação:       distância
Navegação:       Intent para aplicação externa
```

Esta arquitetura mantém o MVP pequeno e já trata o principal problema
observado nos testes: **uma instância pública do Overpass pode responder
numa tentativa e falhar na seguinte**.
