# 23 — Performance e JVM

Entender a JVM por baixo do código: heap, garbage collector, JIT e como medir
de verdade antes de otimizar. A maioria dos "gargalos" que a gente imagina não
existe; medir com JMH muda o jogo.

## O que roda por baixo

O `javac` compila pra bytecode (`.class`), que a **JVM** executa. Dois
componentes importam pro desempenho:

- **JIT (Just-In-Time)**: a JVM compila os métodos mais executados pra código
  de máquina na hora, por isso código Java "esquenta" — a primeira execução é
  mais lenta que a décima.
- **GC (Garbage Collector)**: gerencia memória automaticamente. Os objetos que
  você não usa mais são coletados. Entender GC é entender onde a memória vai.

## A memória: heap e stack

- **Heap**: onde os objetos vivem. É o que o GC administra.
- **Stack**: onde variáveis locais e referências ficam, por thread. Cada thread
  tem a sua, pequena e rápida.

Flags comuns:

```bash
java -Xmx2g -Xms512m App       # heap máx 2GB, inicial 512MB
java -Xss1m App                # stack por thread
java -XX:+UseG1GC App          # GC G1 (padrão)
```

- `-Xmx` (máximo) e `-Xms` (inicial): o heap cresce de `-Xms` até `-Xmx`
  conforme precisa. Muita gente põe os dois iguais pra evitar oscilação.
- Não deixe a JVM sem `-Xmx` num serviço: o default depende da máquina e pode
  estourar em produção.

## Garbage collectors

O padrão hoje é o **G1** (desde o JDK 9). Existem outros:

| GC | Uso | Quando |
| -- | --- | ------ |
| **G1** | Padrão desde JDK 9 | Geral: bom balanço throughput × pause |
| **ZGC** | Baixa latência | Pauses de sub-milissegundo, heaps grandes |
| **Shenandoah** | Baixa latência | Parecido com ZGC, model diferente |
| **Serial** | Single-thread | Máquina pequena, processos simples |
| **Parallel** | Throughput | Batch onde pause não importa |

Você não troca de GC no código; troca na flag de execução:

```bash
java -XX:+UseZGC -Xmx4g App
```

Quando se preocupar com GC: quando a app tem pauses perceptíveis (latência
alta, timeouts) ou o heap fica perto do `-Xmx` o tempo todo. Um `jstat` ou o
`JFR` mostram quanto tempo a app passa em GC.

## O que realmente custa

Prioridades do custo real em Java:

1. **I/O** (rede, disco, banco) — ordens de grandeza mais caro que qualquer
   coisa em memória.
2. **Alocação de objeto** — barata, mas GC é o resultado disso.
3. **Lock contention** — threads brigando por um lock (módulo 16).
4. **Código mal otimizado** — streams mal usados, `String` concatenada em loop
   (módulo 07).

## Medir com JMH

**JMH** (Java Microbenchmark Harness) é a ferramenta oficial do OpenJDK pra
micro-benchmark. `System.nanoTime()` numa main não presta: o JIT otimiza o que
você mede e o resultado mente. JMH aquece, roda várias iterações e separa o
custo real.

Dependências:

```kotlin
dependencies {
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}
```

Com o plugin `me.champeau.jmh` (0.7.2), o projeto ganha uma source set
`jmh` e a task `jmh`:

```kotlin
plugins {
    id("me.champeau.jmh") version "0.7.2"
}

jmh {
    jmhVersion = "1.37"
    warmupIterations = 5
    iterations = 10
    fork = 2
}
```

Um benchmark:

```java
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.Throughput)
@State(Scope.Thread)
public class StringBenchmark {

    @Benchmark
    public String concatenate() {
        String result = "";
        for (int i = 0; i < 100; i++) {
            result = result + "x";
        }
        return result;
    }

    @Benchmark
    public String builder() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("x");
        }
        return sb.toString();
    }
}
```

```bash
./gradlew jmh
```

Regras do JMH:

- Retorne o resultado ou engula num `Blackhole`: se o JIT perceber que o
  resultado não é usado, elimina a computação inteira (dead code
  elimination).
- `@Fork` e `@Warmup` obrigatórios: o JIT precisa esquentar antes de medir.
- **Nunca** compare "String vs StringBuilder" sem JMH. O resultado contra o
  senso comum: `+` em loop pequeno é barato por causa do javac/`StringBuilder`
  automático, e em loop grande o custo real aparece.
- Benchmark não substitui teste de integração: medir um método isolado não diz
  o throughput da sua app inteira.

## Profiling: onde o tempo vai de verdade

O JMH mede um método isolado. Pra saber onde o tempo da **app inteira** vai,
você profileia. Duas ferramentas:

**JFR (JDK Flight Recorder)** vem no JDK, liga sem recompilar:

```bash
java -XX:StartFlightRecording=filename=app.jfr,duration=60s App
```

O JFR grava eventos (CPU, GC, locks, alocação, I/O) num arquivo que o JDK
Mission Control (JMC) abre. É a primeira ferramenta: custo baixo e não
precisa instalar nada.

**async-profiler** é o profiler de produção do ecossistema Java
(`-prof async` também pluga no JMH):

```bash
./profiler.sh -e cpu -d 30 -f flame.svg <pid>
```

Gera flame graph (stack de chamadas com o tempo gasto em cada método). A
leitura: o topo do flame é o custo; a base é quem chamou. Se um método de
I/O domina o flame, otimizar o `for` ao lado não muda nada.

Regra de leitura:

- **CPU**: onde o processador está ocupado (cálculo, parsing, serialização).
- **Alloc**: quanta alocação cada caminho gera (garbage pra frente).
- **Lock**: onde as threads esperam (contention, módulo 16).

Profiling responde a pergunta certa: *o que domina?* JMH responde: *quanto
custa esse trecho?* Use os dois: JMH pra micro, profiler pra macro.

## Tuning de GC: quando mexer (e quando não)

A maioria das apps nunca precisa de tuning de GC. Os defaults (G1) atendem.
Sinais de que você *precisa* olhar:

- **OutOfMemoryError** constante ou heap perto do `-Xmx` o tempo todo.
- Pauses longas de GC perceptíveis (latência alta, timeouts).
- O JFR/JSTAT mostram a app passando >10% do tempo em GC.

O que dá pra ajustar sem religião:

| Flag | Efeito | Uso |
| ---- | ------ | --- |
| `-Xms` = `-Xmx` | heap fixo, sem oscilar | remove crescimento/shrink |
| `-XX:MaxRAMPercentage=75` | heap proporcional à RAM do container | serviço em container, melhor que `-Xmx` fixo |
| `-XX:+UseZGC` | trocar pra baixa latência | pauses longas do G1 incomodam |
| `-XX:MaxGCPauseMillis=200` | alvo de pause do G1 | deixa o GC mais agressivo |

O passo certo de tuning:

1. Meça o status quo com JFR (`jstat -gcutil <pid>` a cada N segundos).
2. Mude UMA variável por vez e meça de novo.
3. O GC que você escolheu pra teste de throughput (Parallel) não é o mesmo
   pra app interativa (G1/ZGC). O alvo define o GC.

Exemplo real de decisão: a app responde em até 100ms e o G1 passa 300ms em
pause de vez em quando. Trocar pra ZGC (`-XX:+UseZGC`) pode custar um pouco
de throughput e mais RAM, mas a latência fica estável. Mediu e a piora não
compensa? Volta pro G1. Nenhuma flag é "melhor"; é trade-off medido.

O que quase nunca resolve: subir `-Xmx` pra esconder leak (a app continua
vazando, só estoura mais tarde). Leak se resolve achando onde o objeto fica
retido (profiler de heap), não dando mais memória.

## Quando otimizar

Ordem certa:

1. **Meça primeiro** (JFR, JMH, profiler). Sem dado, toda otimização é
   opinião.
2. **Ache o custo dominante**: quase sempre é I/O ou query lenta, não o `for`.
3. **Otimize o que tem custo**: reduza alocação em hot path, use coleção
   certa, paralelize I/O com virtual threads (módulo 16).
4. **Repita a medição**: otimizou e não melhorou? Reverta.

O Java moderno (JDK 25) continua melhorando a JVM por baixo: compact object
headers (JEP 519) reduzem o heap em ~10% em alguns workloads, e o JFR ganhou
profiling de CPU experimental (JEP 509). Você se beneficia só de atualizar o
JDK, sem mudar o código.

## Comparação com TypeScript

| Conceito | Java | TypeScript/Node |
| -------- | ---- | --------------- |
| Runtime | JVM (bytecode + JIT) | V8 (bytecode + JIT) |
| Memória | GC automático, flags de heap | GC automático (V8), flags no start |
| Build/otimização | `javac` + JIT contínuo | bundler (esbuild, SWC) + V8 JIT |
| Micro-benchmark | JMH | `benchmark.js` / `tinybench` |
| Profile | JFR, async-profiler | `node --prof`, Chrome DevTools |
| GC tuning | flags explícitas (G1, ZGC) | flags V8 (--max-old-space-size) |
| Flame graph | async-profiler | `0x` (Node flame graphs) |

Os dois são JIT-compiled e GC'd; o V8 otimiza código JS no runtime, a JVM
também. A diferença grande: o Java tem controle explícito de heap e de GC via
flags, e ferramentas de profiling maduras (JFR vem no JDK). O V8 decide tudo
por você, e o profiler equivalente (`0x`, `node --prof`) é menos integrado.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| G1 como GC padrão | JDK 9 | Padrão |
| ZGC | JDK 15 | Maduro |
| Shenandoah | JDK 15 | Maduro |
| Virtual threads | JDK 21 | Permanente |
| Compact object headers (JEP 519) | JDK 25 | Produto (desligado por padrão) |
| JFR CPU-time profiling (JEP 509) | JDK 25 | Experimental |

## Exercícios

1. Escreva um benchmark JMH que compara concatenação com `+` vs
   `StringBuilder` em loop de 1.000 iterações. Rode com `./gradlew jmh` e
   anote a diferença de throughput. Depois troque o tamanho do loop pra
   10.000 e veja como a relação muda.
2. Escreva um benchmark que compara `ArrayList` vs `LinkedList` em adição no
   fim (1M de elementos). O resultado do JMH costuma surpreender: explique
   por que o `LinkedList` não ganha.
3. Com JFR (`-XX:StartFlightRecording=filename=app.jfr,duration=60s`), rode
   uma app que faz I/O de arquivo e identifique no `jfr` onde o tempo foi.
   Escreva o comando e o que você procurou no relatório.
4. Rode a mesma app com `-Xmx256m` e depois `-Xmx2g` e compare o número de GC
   pauses com `jstat -gcutil`. Escreva o que mudou e por quê.
5. Escreva um benchmark que mede `Stream.filter().map().collect()` vs um loop
   `for` equivalente. Rode e compare. Quando o stream compensa? (resposta:
   legibilidade, não velocidade — o JMH mostra margens pequenas).
6. Profileie uma app que lê arquivo com `async-profiler` e aponte no flame
   graph onde o tempo foi (I/O de arquivo deve dominar). Depois troque pra
   cálculo puro e compare os dois flames.
7. Rode a mesma app com G1 (`-XX:+UseG1GC`) e ZGC (`-XX:+UseZGC`) com JFR
   ligado, e compare pause de GC no relatório. Anote o trade-off observado
   (latência vs throughput/memória).

## Referências

- [JMH (GitHub)](https://github.com/openjdk/jmh) — o harness oficial de micro-benchmark
- [JMH — Sizing Up (Aleksey Shipilëv)](https://shipilev.net/blog/2014/jmh-sampling/) — por que os números enganam sem JMH
- [Garbage Collection Tuning (Oracle)](https://docs.oracle.com/en/java/javase/25/gctuning/introduction-garbage-collection-tuning.html) — o manual de tuning de GC
- [async-profiler (GitHub)](https://github.com/async-profiler/async-profiler) — o profiler de produção com flame graphs
- [JEP 519 — Compact Object Headers](https://openjdk.org/jeps/519) — headers de objeto reduzidos (JDK 25)
- [JEP 509 — JFR CPU-Time Profiling (Experimental)](https://openjdk.org/jeps/509) — profiling de CPU no JFR (JDK 25)
- [Flight Recorder (Oracle)](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jfr.html) — o profiler embutido no JDK

## Próximo módulo

**Segurança** — criptografia, hash de senha, TLS e as práticas que separam
código seguro de código que vira CVE.

[→ 24 — Segurança](./24-seguranca.md)