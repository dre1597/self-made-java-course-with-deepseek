# 25 — Evolução do Java e Previews

Java mudou de ritmo. Desde o JDK 9 (2017) a linguagem lança a cada **6 meses**,
e as LTS (long-term support) a cada 2 anos. Isso mudou como a linguagem
evolui: features grandes amadurecem em **preview** antes de virar padrão, e o
que era "Java 8 pra sempre" virou "Java 25, 26, 27...".

## O ciclo de releases

| Tipo | Cadência | Exemplos | Pra que serve |
| ---- | -------- | -------- | ------------- |
| LTS | ~a cada 2 anos | 8, 11, 17, 21, 25 | Suporte longo, produção |
| Feature | a cada 6 meses | 22, 23, 24, 26, 27 | Entrega contínua, experimentação |

- O **25** (set/2025) é a LTS atual do curso.
- Feature releases entre LTS são seguros de usar em produção? A maioria das
  empresas segura nas LTS, mas o 26 e o 27 trazem features que você vai querer
  (HTTP/3, por exemplo, chegou no 26).
- A regra prática: LTS em produção, feature release em ferramenta e estudo.
  O bytecode é retrocompatível; seu código roda em versão nova sem recompilar.

## Como uma feature vira padrão

O caminho de uma feature grande no Java moderno:

1. **Rascunho/JEP draft** (OpenJDK discute).
2. **Preview** (flag `--enable-preview`): a feature existe mas pode mudar. Você
   testa, o time ajusta, o feedback decide.
3. **Permanente**: sem flag, comportamento final.

Preview é pra feedback, não pra produção. Sintoma: se você precisa de
`--enable-preview` pra compilar, a feature ainda pode mudar.

As previews que importam no Java 25/26:

| Feature | Preview desde | Status |
| ------- | ------------- | ------ |
| Primitive types in patterns (`case int`, `case double`) | 23 | 4º preview (26) |
| Structured concurrency | 21 | 6º preview (26) |
| Scoped values | 20 | Permanente (25) |
| Vector API | 16 | Incubator (11ª vez, 26) |
| Value classes (Valhalla) | — | Ainda em desenvolvimento |

## O que ficou permanente recente (pra ler atentamente)

- **JDK 25 (LTS)**: module import declarations (`import module java.base`),
  compact source files + instance `main`, flexible constructor bodies, API KDF
  (`javax.crypto.KDF`), scoped values, compact object headers (JEP 519),
  generational Shenandoah (JEP 521).
- **JDK 21 (LTS anterior)**: virtual threads, pattern matching em `switch`,
  record patterns, `StringTemplate` (preview).

O `import module` merece destaque porque muda como você escreve no dia a dia:

```java
// antes: 20+ imports manuais de java.util, java.io, java.nio...
import module java.base;

public class Main {
    public static void main(String[] args) {
        Path file = Path.of("dados.txt");
        List<String> lines = Files.readAllLines(file);
        lines.stream().filter(l -> !l.isBlank()).forEach(System.out::println);
    }
}
```

Uma linha importa tudo que o `java.base` exporta. Funciona em código comum
(não precisa de `module-info.java`). O trade-off: menos imports explícitos, e
se dois módulos exportam a mesma classe (ex.: `java.sql.Date`), você resolve
com import explícito.

## As previews em código real

Preview é difícil de avaliar lendo a JEP; em código faz sentido. As que
importam no Java 25/26, com o formato atual da API:

### Structured concurrency (JEP 505, preview)

Sub-tarefas vivem num escopo que as cancela juntas. Se uma falha, as outras
param; se o dono é interrompido, todas cancelam. Sem vazamento de thread.

```java
record UserDashboard(User user, List<Order> orders) {}

public UserDashboard loadDashboard(String userId) throws Exception {
    try (var scope = StructuredTaskScope.open()) {

        Subtask<User> user = scope.fork(() -> fetchUser(userId));
        Subtask<List<Order>> orders = scope.fork(() -> fetchOrders(userId));

        scope.join();   // espera as duas; lança se qualquer uma falhou

        return new UserDashboard(user.get(), orders.get());
    }
}
```

`open()` é a factory do 5º preview (antes era `new ShutdownOnFailure()`). O
`try-with-resources` garante: saiu do bloco, cancelou o que restou e esperou.
O `fork` roda cada sub-tarefa numa virtual thread.

### Scoped values (JEP 506, permanente no 25)

Compartilhar dado imutável entre a request e tudo que ela chama, sem passar
parâmetro por 10 métodos. É o substituto moderno do `ThreadLocal`, mais barato
e com garantia de não vazar pra thread errada.

```java
private static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

public void handle(HttpExchange exchange) {
    String requestId = UUID.randomUUID().toString();
    ScopedValue.where(REQUEST_ID, requestId, () -> process(exchange));
}

public void process(HttpExchange exchange) {
    log.info("request {} iniciada", REQUEST_ID.get());   // lê no método filho
}
```

O `where` cria o binding só dentro do bloco; o `get()` dentro do `process` lê
o valor do pai, inclusive de uma virtual thread filha. Fora do bloco, o valor
some. Sem variável global.

### Compact source files (JEP 512, permanente no 25)

Java como script: sem classe, sem `public static`, sem `import`. O `main` de
instância e o `import module java.base` implícito.

```java
// hello.java — rode com: java hello.java
void main() {
    System.out.println("Olá, Java!");
}
```

```java
void main() {
    Path file = Path.of("dados.txt");
    Files.readAllLines(file).forEach(System.out::println);
}
```

Sem `class`, sem `String[] args`, sem imports. Pra ferramenta de linha de
comando e experimento rápido. Quando o arquivo cresce, você migra pra classe
normal e os imports explícitos voltam a ser necessários.

### Onde elas estão no 26

- Structured concurrency: 6º preview (JEP 525), API estabilizando.
- Scoped values: já permanente no 25.
- Compact source files: já permanente no 25.
- Primitive types in patterns: 4º preview (JEP 530).

## O que vem por aí (o radar)

Do que está em desenvolvimento no OpenJDK, o que vai moldar o Java dos
próximos anos:

- **Project Valhalla — value classes**: objetos sem identidade, com o
  desempenho de primitivo. O "grande prêmio" da linguagem; pode virar preview
  em 2026/2027. É o que vai reduzir pressão de GC em hot paths.
- **Project Loom já entregou**: virtual threads (21) e structured concurrency
  (em preview, caminho pra permanente). Concorrência simples virou tema
  resolvido.
- **Project Panama entregou**: FFM API (JDK 22) pra chamar código nativo sem
  JNI. O "Java acessa C" ficou rápido e seguro.
- **Project Leyden**: ahead-of-time (AOT) e startup mais rápido. Já rendeu
  JEPs no 24/25/26 (AOT class loading, object caching).
- **Post-quantum crypto**: TLS com key exchange híbrido (JDK 27 em diante).

## Java vs. a cultura de outras linguagens

O que diferencia o Java hoje:

- **Estabilidade com inovação**: o `record`, o `sealed` e o pattern matching
  modernizaram a linguagem sem quebrar o que já existia. O Java que você
  escreve em 2026 não parece o Java de 2014, mas migrar é direto.
- **Sem framework na linguagem**: diferente de TS (que vem com o ecossistema
  Node em volta), Java separa linguagem, JDK e frameworks. Você já viu isso no
  curso: `HttpClient`, JDBC e Jackson são camadas separadas.
- **Backward compatibility é sagrada**: seu código de 2004 ainda compila. É
  uma força e uma âncora; o OpenJDK gerencia isso com previews e deprecation
  lenta.

## Onde ver o que vem

- [JEP Index](https://openjdk.org/jeps/) — cada proposta, com status e versão.
- [JDK release notes](https://www.oracle.com/java/technologies/javase/25all-relnotes.html) — o que mudou em cada release.
- [Inside Java](https://inside.java/) — artigos do time da Oracle sobre cada
  feature.
- `java --list-jeps` (JDK 25+) — os JEPs da sua instalação.

## Comparação com TypeScript

| Conceito | Java | TypeScript/Node |
| -------- | ---- | --------------- |
| Cadência | LTS a cada 2 anos + feature a cada 6 | LTS do Node ~anual, TS mensal |
| Preview | `--enable-preview`, amadurece por release | `beta`/`next` tags no npm |
| Retrocompat | Muito valorizada, deprecation lenta | TS quebra em major (4→5), Node também |
| Evolução | JEPs com status rastreável | Proposal stages do TC39 / versão |

O TS muda rápido e quebra mais; o Java muda em ciclos longos e preserva. As
duas evoluem com o mesmo modelo de "proposta → experimento → padrão".

## Encerrando o curso

Você percorreu a linguagem de ponta a ponta:

- **Base**: sintaxe, tipos, coleções, OOP, records/enums/sealed, generics,
  exceptions.
- **Idioma moderno**: pattern matching, lambdas e streams, o JDK 11+ de I/O e
  HTTP.
- **Concorrência**: virtual threads, `ExecutorService`, `CompletableFuture`.
- **Dados e infra**: JDBC, build com Gradle, testes com JUnit/Mockito.
- **Engenharia**: design patterns no Java moderno, arquitetura, performance e
  segurança.

O que falta são os **mini projetos** do `projetos/`: o Gerenciador de Tarefas
CLI e o Servidor HTTP puro com JDBC. Eles juntam os módulos em código de
verdade.

Se o Java 26 ou 27 lançar uma feature que te interessa, a forma de acompanhar
está na seção de JEPs. O curso te deu a base; o radar te mantém atualizado.

## Exercícios

1. Escreva um `hello.java` compact source file (sem classe, `main` de
   instância) que lê um arquivo e imprime as linhas. Rode com
   `java hello.java`. Depois migre pra classe normal e aponte o que mudou.
2. Use `ScopedValue` pra propagar um `requestId` de um método pai pra um
   método filho (sem parâmetro). Teste que o valor é visível no filho e que
   fora do bloco `where` o `get()` lança.
3. Escreva um `loadDashboard` com `StructuredTaskScope` que busca usuário e
   pedidos em paralelo (`--enable-preview`). Teste: as duas completam juntas,
   e se uma falha, a outra é cancelada (a soma dos resultados não completa).
4. Compare `ThreadLocal` vs `ScopedValue` num teste: prove que o `ThreadLocal`
   vaza valor pra thread reutilizada e o `ScopedValue` não (escopo fechado).

## Referências

- [JEP Index](https://openjdk.org/jeps/) — todas as propostas com status
- [JDK 25 Release Notes](https://www.oracle.com/java/technologies/javase/25all-relnotes.html) — o que mudou na LTS atual
- [JDK 26 Release Notes](https://www.oracle.com/java/technologies/javase/26all-relnotes.html) — o feature release seguinte
- [JEP 511 — Module Import Declarations](https://openjdk.org/jeps/511) — `import module java.base` (JDK 25)
- [JEP 512 — Compact Source Files and Instance Main Methods](https://openjdk.org/jeps/512) — scripts Java sem classe (JDK 25)
- [JEP 505 — Structured Concurrency (Preview)](https://openjdk.org/jeps/505) — `StructuredTaskScope` e o futuro da concorrência
- [JEP 506 — Scoped Values](https://openjdk.org/jeps/506) — `ScopedValue`, o substituto do `ThreadLocal` (JDK 25)
- [Structured Concurrency (Oracle docs)](https://docs.oracle.com/en/java/javase/25/core/structured-concurrency.html) — os joiners do 5º preview
- [Project Valhalla](https://openjdk.org/projects/valhalla/) — value classes e o futuro da performance
- [Project Leyden](https://openjdk.org/projects/leyden/) — startup e AOT
- [Inside Java](https://inside.java/) — acompanhamento das features pelo Nicolai Parlog