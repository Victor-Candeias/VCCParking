---
maestru: "0.4"
type: work-spec
id: vp-01-foundation-spec
title: "Especificação da arquitetura Android"
template: implementation-plan-v1
work-item: vcc-parking-mvp-android/vp-01-foundation
owner: developer
created: 2026-09-30
---

# Especificação da arquitetura Android

## Overview

Estabelece a fundação do projeto Android do VCC Parking: configuração Gradle, catálogo
de dependências, estrutura de pacotes da arquitetura `UI → ViewModel → Repository →
(OverpassService | ParkingCache)` e uma aplicação Compose mínima que compila e arranca.
Beneficia todos os work-items seguintes, que passam a ter módulo, dependências e pacotes
prontos. Motivado pelo work-item `vp-01-foundation`.

## Implementation

### Phase 1: Configuração Gradle

Toolchain escolhida a partir do que está instalado na máquina (Android SDK com
plataforma `android-37.0` e build-tools `36.0.0`, JDK 25 do Android Studio).

| Componente | Versão | Nota |
|---|---|---|
| Android Gradle Plugin | 9.4.1 | Suporta no máximo a API 37 |
| Gradle | 9.8.0 | Acima do mínimo 9.6.0 exigido pelo AGP 9.4 |
| Kotlin | 2.4.20 | Compilação gerida pelo Kotlin integrado do AGP 9 |
| KSP | 2.3.12 | Processador do Room |
| compileSdk / targetSdk | 37 | Android 17 |
| minSdk | 24 | Compose suporta 21; 24 evita limitações antigas |
| jvmTarget | 17 | Baseline documentado do AGP 9.4 |

#### Step 1.1: Diferenças do AGP 9 face ao AGP 8

O AGP 9 alterou o DSL. Estas diferenças condicionam todos os ficheiros de build:

| Área | AGP 8.x | AGP 9.4 |
|---|---|---|
| SDK | `compileSdk = 37` | `compileSdk { version = release(37) }` |
| Kotlin | aplicar `org.jetbrains.kotlin.android` | Kotlin integrado; não se aplica o plugin |
| JVM target | `android { kotlinOptions { } }` | `kotlin { compilerOptions { } }` |
| Compose | `composeOptions { kotlinCompilerExtensionVersion }` | plugin `org.jetbrains.kotlin.plugin.compose` |
| Anotações | kapt | KSP (kapt incompatível com Kotlin integrado) |

#### Step 1.2: Ficheiros de build

| Action | File | Details |
|---|---|---|
| Create | `settings.gradle.kts` | Repositórios `google()`, `mavenCentral()`, `gradlePluginPortal()`; inclui `:app` |
| Create | `build.gradle.kts` | Declara AGP, Compose, serialization e KSP com `apply false` |
| Create | `gradle/libs.versions.toml` | Catálogo único de versões e bibliotecas |
| Create | `gradle.properties` | `android.useAndroidX`, heap do daemon, build paralelo e cache |
| Create | `gradle/wrapper/gradle-wrapper.properties` | Fixa a distribuição Gradle 9.8.0 |
| Run | `gradle wrapper --gradle-version 9.8.0` | Gera `gradlew`, `gradlew.bat` e `gradle-wrapper.jar` |
| Modify | `.gitignore` | Entradas Android/Gradle e exceção para versionar o wrapper jar |

Verificação da fase 1:

- [x] `gradlew.bat` existe e executa
- [x] O catálogo de versões resolve sem conflitos

#### Step 1.3: Módulo da aplicação

| Action | File | Details |
|---|---|---|
| Create | `app/build.gradle.kts` | Namespace `pt.vcc.parking`, DSL em bloco do AGP 9, Compose ativo |
| Create | `app/proguard-rules.pro` | Regras para osmdroid e serializers do kotlinx |
| Create | `local.properties` | Caminho do SDK (não versionado) |

Dependências declaradas, agrupadas por responsabilidade:

| Área | Bibliotecas |
|---|---|
| UI | Compose BOM, Material 3, Activity Compose, Lifecycle ViewModel Compose |
| Assíncrono | kotlinx-coroutines-android |
| Rede | Retrofit 3, converter kotlinx-serialization, OkHttp, logging-interceptor |
| Serialização | kotlinx-serialization-json |
| Persistência | Room runtime/ktx + compiler via KSP |
| Localização | play-services-location |
| Mapa | osmdroid-android |
| Testes | JUnit 4, coroutines-test, AndroidX JUnit, Espresso, Compose UI test |

### Phase 2: Estrutura e aplicação mínima

#### Step 2.1: Estrutura de pacotes

Cada pacote fica criado com um marcador que identifica o work-item responsável por o
preencher, para que a arquitetura fique visível sem antecipar lógica de outras tarefas.

| Pacote | Responsabilidade | Preenchido por |
|---|---|---|
| `domain/model` | Modelo `Parking` | `vp-04-domain` |
| `data/remote` | Cliente Overpass, DTO, endpoints | `vp-03-overpass` |
| `data/local` | Room: entity, DAO, database | `vp-05-cache` |
| `data/repository` | `ParkingRepository` com fallback e cache | `vp-03-overpass`, `vp-05-cache` |
| `location` | Fused Location Provider | `vp-02-location` |
| `ui/map` | Ecrã de mapa | `vp-06-ui` |
| `ui/list` | Lista por distância | `vp-06-ui` |
| `ui/details` | Detalhe e navegação externa | `vp-06-ui` |

#### Step 2.2: Aplicação mínima

| Action | File | Details |
|---|---|---|
| Create | `app/src/main/AndroidManifest.xml` | Permissões de Internet, estado de rede e localização; atividade de arranque |
| Create | `.../VccParkingApplication.kt` | Classe de aplicação para inicialização futura |
| Create | `.../MainActivity.kt` | Activity Compose com ecrã inicial |
| Create | `.../ui/theme/Theme.kt`, `Color.kt` | Tema Material 3 claro e escuro |
| Create | `res/values/strings.xml` | Nome da app e atribuição OpenStreetMap |
| Create | `res/values/themes.xml` | Tema base sem action bar |
| Create | `res/drawable/`, `res/mipmap-anydpi*/` | Ícone vetorial e ícone adaptativo |

Verificação da fase 2:

- [x] `:app:assembleDebug` conclui com sucesso
- [x] `:app:assembleRelease` conclui com sucesso, incluindo `lintVitalRelease`
- [x] O APK declara `pt.vcc.parking`, minSdk 24, targetSdk 37 e atividade de arranque
- [x] A app instala e abre num emulador

## Riscos

| Risco | Detalhe | Mitigação |
|---|---|---|
| osmdroid arquivado | O projeto osmdroid foi arquivado em novembro de 2024 e a última versão é a 6.1.20 | Reavaliar antes de `vp-06-ui`; alternativa é MapLibre |
| Combinação AGP/Kotlin | Kotlin 2.4.20 documenta compatibilidade até AGP 9.3.1 | Validado por build; fixar versões se surgirem falhas |
| Política de tiles OSM | O osmdroid exige `userAgentValue` próprio e atribuição visível | Configurar em `vp-06-ui` |

## Impacted Files

| File | Action | Purpose |
|------|--------|---------|
| `settings.gradle.kts` | Create | Repositórios e módulos do projeto |
| `build.gradle.kts` | Create | Declaração dos plugins na raiz |
| `gradle/libs.versions.toml` | Create | Catálogo de versões e bibliotecas |
| `gradle.properties` | Create | Propriedades do build |
| `gradle/wrapper/gradle-wrapper.properties` | Create | Distribuição Gradle fixada |
| `gradlew` | Create | Script do wrapper para Unix |
| `gradlew.bat` | Create | Script do wrapper para Windows |
| `gradle/wrapper/gradle-wrapper.jar` | Create | Binário do wrapper |
| `.gitignore` | Modify | Ignorar artefactos Android e versionar o wrapper jar |
| `app/build.gradle.kts` | Create | Configuração do módulo da aplicação |
| `app/proguard-rules.pro` | Create | Regras de ofuscação |
| `app/src/main/AndroidManifest.xml` | Create | Permissões e atividade de arranque |
| `app/src/main/java/pt/vcc/parking/VccParkingApplication.kt` | Create | Classe de aplicação |
| `app/src/main/java/pt/vcc/parking/MainActivity.kt` | Create | Activity Compose inicial |
| `app/src/main/java/pt/vcc/parking/ui/theme/Theme.kt` | Create | Tema Material 3 |
| `app/src/main/java/pt/vcc/parking/ui/theme/Color.kt` | Create | Paleta do tema |
| `app/src/main/res/values/strings.xml` | Create | Textos da aplicação |
| `app/src/main/res/values/themes.xml` | Create | Tema base |
| `app/src/main/res/drawable/ic_launcher_background.xml` | Create | Fundo do ícone |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Create | Símbolo do ícone |
| `app/src/main/res/mipmap-anydpi/ic_launcher.xml` | Create | Ícone para API 24-25 |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Create | Ícone adaptativo |
