# 22 — Build e Empacotamento (Gradle)

Gradle é o build tool padrão do ecossistema Java moderno (Kotlin DSL). Compilar,
testar, empacotar e publicar sem abrir um terminal à mão. O Maven ainda existe
e muita empresa usa; no fim tem um passe pra reconhecer a estrutura dele.

## O wrapper

Todo projeto Java com Gradle versiona o **wrapper**: um script (`gradlew`) que
baixa a versão exata do Gradle e roda com ela. É assim que todo mundo na equipe
usa a mesma versão, sem instalar nada.

```bash
gradle wrapper --gradle-version 9.1.0
./gradlew build
```

- `gradlew` no Linux/Mac, `gradlew.bat` no Windows.
- O Gradle 9.1.0 em diante roda com Java 25. O daemon do Gradle precisa de
  Java 17+ desde o Gradle 9.0.

## O `build.gradle.kts`

```kotlin
plugins {
    java
    application
}

group = "br.com.exemplo"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("tools.jackson.core:jackson-databind:3.2.0")
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "br.com.exemplo.Main"
}

tasks.test {
    useJUnitPlatform()
}
```

Pedacinhos:

- `plugins { java; application }`: o plugin `java` adiciona compilar/testar/empacotar;
  o `application` adiciona `run` e gera scripts de inicialização.
- `repositories`: de onde baixar dependências. `mavenCentral()` é o padrão.
- `dependencies`: `implementation` são de runtime; `testImplementation` são só
  de teste. O BOM do JUnit (`platform(...)`) resolve as versões dos artefatos
  JUnit pra você.
- `java.toolchain`: o Gradle compila e testa com o JDK 25, mesmo que o daemon
  rode em outro. Se não tiver o JDK instalado, o plugin Foojay baixa
  automaticamente.
- `application.mainClass`: qual classe roda no `run` e vira o ponto de entrada
  do pacote.

## Ciclo de vida das tasks

```bash
./gradlew compileJava      # compila src/main
./gradlew test             # roda os testes
./gradlew build            # compile + test + jar
./gradlew clean            # apaga build/
./gradlew run              # roda a aplicação (plugin application)
```

`build` gera o `build/libs/` com o `.jar` (sem dependências). O `test` gera
relatório em `build/reports/tests/test/index.html`.

## Configurações de dependência: qual usar

Cada palavra no `dependencies` controla pra quem a dependência vaza. Isso
importa em projeto que vira biblioteca ou tem mais de um módulo:

| Config | Visível em | Uso |
| ------ | ---------- | --- |
| `implementation` | só no meu módulo em runtime | dependência interna; não vaza pro consumidor |
| `api` | consumidores do meu módulo | exposta no tipo público (ex.: retorno de método) |
| `compileOnly` | só pra compilar | anotação/API que some em runtime (Lombok, `jakarta.*`) |
| `runtimeOnly` | só em runtime | driver/implementação que não uso no código (H2, PostgreSQL) |
| `testImplementation` | só nos testes | JUnit, Mockito |
| `testRuntimeOnly` | só nos testes em runtime | engine do JUnit |

```kotlin
dependencies {
    // exposta no tipo público: quem usa o meu módulo precisa dela no classpath
    api("tools.jackson.core:jackson-databind:3.2.0")

    // interna: ninguém vê, e eu posso trocar sem quebrar o consumidor
    implementation("com.zaxxer:HikariCP:7.1.0")

    // só pra compilar (API de anotação), não vai pro runtime
    compileOnly("org.projectlombok:lombok:1.18.38")

    // driver: o código não referencia a classe, o JDBC carrega via SPI
    runtimeOnly("com.h2database:h2:2.3.232")

    testImplementation(platform("org.junit:junit-bom:5.14.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

A regra de bolso: `implementation` é o padrão; `api` só quando o tipo aparece
na assinatura pública (retorno, parâmetro). Se você põe tudo como `api`, perde
o isolamento (qualquer dependência vaza). Se põe tudo como `implementation`,
um módulo que devolve `Product` no método público quebra quem consome.

## Version catalog: o padrão pra versões

Em vez de versão espalhada nas strings, o Gradle tem o **version catalog**
(`gradle/libs.versions.toml`), que centraliza versões e grupos. É o padrão
de mercado hoje:

`gradle/libs.versions.toml`:

```toml
[versions]
jackson = "3.2.0"
junit = "5.14.3"

[libraries]
jackson-databind = { module = "tools.jackson.core:jackson-databind", version.ref = "jackson" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junit" }

[bundles]
testing = ["junit-jupiter"]
```

`build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.jackson.databind)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.bundles.testing)
}
```

Um lugar só de versões, autocompletável no IDE, e o `libs.` dá acesso
tipado. Atualizar uma lib vira mudar uma linha no TOML, não uma busca no
projeto.

## Multi-module: separando o projeto em módulos

Projeto grande vira vários módulos Gradle: cada um com `src/`, `build.gradle`
e dependência declarada. Estrutura típica:

```
app/
    settings.gradle.kts          # inclui os módulos
    build.gradle.kts             # raiz: só plugins comuns
    core/                        # domínio: records, interfaces, regra
        src/main/java/...
        build.gradle.kts
    infrastructure/              # JDBC, HTTP, adaptadores
        src/main/java/...
        build.gradle.kts
    application/                 # main, composite root
        src/main/java/...
        build.gradle.kts
```

`settings.gradle.kts`:

```kotlin
rootProject.name = "app"

include("core", "infrastructure", "application")
```

`application/build.gradle.kts`:

```kotlin
plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":infrastructure"))
}
```

`infrastructure/build.gradle.kts`:

```kotlin
plugins {
    java
}

dependencies {
    api(project(":core"))          // o tipo Product vaza na assinatura
    implementation("tools.jackson.core:jackson-databind:3.2.0")
}
```

Os módulos respeitam as configurações do início: `core` expõe `Product` via
`api` pro `infrastructure` usar no retorno; `infrastructure` não vaza o
Jackson pro `application`. Trocar a implementação de banco vira trocar o
módulo, sem tocar no domínio. É o hexágono do módulo 21 em estrutura de build.

## Empacotamento: fat jar

O `jar` do Gradle é enxuto: só as suas classes, sem as dependências. Pra
distribuir um executável que roda com `java -jar`, você precisa de um **fat
jar** (todas as libs dentro). O plugin padrão pra isso é o **Shadow**:

```kotlin
plugins {
    id("com.gradleup.shadow") version "9.6.1"
}
```

```bash
./gradlew shadowJar
java -jar build/libs/app-all.jar
```

O Shadow gera `app-all.jar` com tudo embutido e o `Main-Class` do manifest
configurado. É o jeito comum de distribuir CLI e servidor simples (como o mini
projeto 2). O equivalente no Maven é o `maven-shade-plugin`.

## O Maven, de relance

O Maven é XML (`pom.xml`), declarativo, com ciclo de vida fixo. Se um dia você
cai numa empresa que usa Maven, o mapa mental:

```xml
<project>
    <groupId>br.com.exemplo</groupId>
    <artifactId>app</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.11.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

```bash
mvn compile     # compila
mvn test        # testa
mvn package     # gera o jar
```

Diferenças na prática:

- Gradle usa build script (Kotlin/Groovy), Maven usa XML.
- Gradle tem tasks flexíveis e incrementais; Maven tem ciclo de vida fixo e
  padronizado (`compile` → `test` → `package`).
- Gradle é mais rápido e o padrão de mercado hoje; Maven domina em projeto
  legado. Os dois resolvem o mesmo problema: dependências + build.

## Propriedades de runtime

Configuração de execução não vai no build, vai na linha de comando (pra você
poder variar por ambiente):

```bash
java -Xmx2g -jar app-all.jar --config=prod.properties
./gradlew run --args="--config=prod.properties"
```

## Comparação com TypeScript

| Conceito | Java | TypeScript/Node |
| -------- | ---- | --------------- |
| Build tool | Gradle | npm / pnpm / yarn |
| Dependências | `repositories` + `dependencies` | `package.json` + registry |
| Wrapper | `gradlew` | não tem (npm é global ou via nvm) |
| Testar | `./gradlew test` | `npm test` |
| Empacotar | `shadowJar` / fat jar | `npm run build` / bundler |
| Runtime | JVM (JDK) | Node runtime |
| Gerenciador de versão do runtime | asdf / sdkman | nvm |
| Versões centralizadas | version catalog (`libs.versions.toml`) | `package.json` |
| Escopo de dependência | `api`/`implementation`/`compileOnly` | `dependencies` vs `devDependencies` |
| Multi-módulo | `include(...)` + `project(":x")` | monorepo com workspaces |

O modelo é o mesmo: declarar dependências, rodar scripts, gerar artefato. A
diferença: no Node o runtime é um processo; no Java o runtime é a JVM, e o
build tool também roda nela (daemon). O escopo `devDependencies` do npm é o
primo do `testImplementation`; o workspaces do npm é o primo do multi-module.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| Gradle com Java 25 | Gradle 9.1.0 | Atual |
| Gradle mínimo Java 17 | Gradle 9.0 | Atual |
| Shadow plugin | 9.6.x | Atual |

## Exercícios

1. Crie um projeto Gradle mínimo com plugin `application`, uma classe `Main`
   que imprime algo, e rode `./gradlew run`. Depois rode `./gradlew build` e
   confira que o jar foi gerado em `build/libs/`.
2. Adicione o Jackson 3 (`tools.jackson.core:jackson-databind`) como
   dependência `implementation`, serialize um record com `JsonMapper` num
   método `main`, e rode com `./gradlew run`.
3. Configure o Shadow plugin, gere o fat jar e rode com `java -jar`. Confirme
   que o fat jar roda sem o Gradle por perto.
4. Adicione um teste JUnit 5 no `src/test` e rode `./gradlew test`. Quebre o
   teste de propósito e confira que o build falha e o relatório aparece em
   `build/reports/`.
5. (Maven) Escreva o `pom.xml` equivalente ao projeto do exercício 1 e rode
   `mvn compile`. Compare a estrutura do `target/` com a do `build/` do
   Gradle.
6. Mova as dependências do projeto do exercício 1 pro version catalog
   (`libs.versions.toml`) e troque as strings por `libs.`. Rode `build` e
   confira que resolve igual.
7. Crie um projeto com 3 módulos (`core`, `infrastructure`, `application`):
   um record `Product` no `core`, um repositório JDBC no `infrastructure`
   (que usa `Product`), e o `main` no `application`. Rode `build` na raiz e
   confira a ordem de compilação.
8. Num módulo que expõe `Product` no retorno de um método público, teste a
   diferença: declare a dependência de `core` como `implementation` e rode o
   build (quem consome quebra em runtime); depois troque pra `api` e confira
   que passa.

## Referências

- [Gradle User Manual](https://docs.gradle.org/current/userguide/userguide.html) — a referência completa, do wrapper às toolchains
- [The Application Plugin](https://docs.gradle.org/current/userguide/application_plugin.html) — `run`, scripts de inicialização e distribuição
- [Gradle Toolchains for JVM Projects](https://docs.gradle.org/current/userguide/toolchains.html) — como o Gradle usa o JDK 25 sem depender do daemon
- [Gradle Compatibility Matrix](https://docs.gradle.org/current/userguide/compatibility.html) — qual versão do Gradle roda com qual Java
- [Shadow Plugin](https://gradleup.com/shadow/getting-started/) — o fat jar padrão do Gradle
- [Gradle 9.1.0 Release Notes](https://docs.gradle.org/9.1.0/release-notes.html) — suporte a Java 25

## Próximo módulo

**Performance e JVM** — a JVM por baixo do seu código: heap, GC, JIT e como
medir antes de otimizar.

[→ 23 — Performance e JVM](./23-performance.md)