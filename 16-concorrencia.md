# 16 — Concorrência

Threads, `ExecutorService`, virtual threads, `CompletableFuture` e os riscos de estado compartilhado. A partir do JDK
21, thread é barata (Project Loom) e o jeito de escrever concorrência mudou.

## O modelo mental

**Concorrência** é ter várias tarefas em andamento ao mesmo tempo, com o CPU intercalando entre elas. **Paralelismo** é
um caso especial: várias tarefas rodando literalmente ao mesmo tempo, em cores diferentes. Você quase sempre quer
concorrência (milhares de requisições avançando de uma vez), não necessariamente paralelismo.

Java executa tarefas concorrentes com **threads**: uma linha de execução com a própria pilha de chamadas. Um programa
com uma thread processa em sequência. Com várias, as tarefas se intercalam, e em máquina multi-core rodam de verdade em
paralelo.

O motivo prático de querer threads é o **I/O**: esperar banco, chamar API, ler arquivo. Enquanto uma tarefa espera a
resposta, o CPU fica ocioso, e outra tarefa poderia usar esse tempo. Código que passa a maior parte do tempo esperando
I/O é **I/O-bound**; código que passa o tempo calculando é **CPU-bound**. Threads aceleram o primeiro, não o segundo.

O problema que atravessa a aula inteira é o **estado compartilhado**. Quando duas threads leem e escrevem a mesma
variável sem controle, o resultado é imprevisível (corrida de dados), e nem sempre uma thread vê a escrita da outra
(problema de visibilidade). Metade das ferramentas desta aula resolve esses dois problemas; a outra metade organiza a
comunicação entre threads.

Mapa da aula: primeiro o jeito antigo de rodar tarefas (threads e pool), depois as virtual threads, o jeito atual,
depois as ferramentas de estado compartilhado do mais simples ao mais específico, e por fim a composição assíncrona com
`CompletableFuture` e as duas novidades do Java 25.

## O jeito antigo: threads e pools

### Thread clássica

```java
Thread thread = new Thread(() -> System.out.println("rodando"));
thread.

start();
thread.

join();        // espera terminar
```

Criar uma thread por tarefa funcionava em exemplo pequeno e quebrava em escala: cada platform thread custa ~1MB de
pilha, e 10.000 delas derrubam a máquina.

### ExecutorService: o pool

O pool reusa um número fixo de threads. As tarefas entram numa fila e as threads do pool vão executando:

```java
ExecutorService pool = Executors.newFixedThreadPool(4);

Future<String> future = pool.submit(() -> fetchData());
String result = future.get();        // bloqueia até o resultado

pool.

shutdown();                     // não aceita mais tarefas
```

`Future.get()` é síncrono e bloqueia a thread que chama. Pra lançar várias tarefas e esperar todas:

```java
List<Callable<BigDecimal>> tasks = List.of(
    () -> totalFromDatabase(),
    () -> totalFromApi()
);
List<Future<BigDecimal>> futures = pool.invokeAll(tasks);
```

O problema do pool: o tamanho é limitado, então você dimensiona a mão. Poucas threads e o I/O fica parado na fila;
muitas e o custo de thread volta. Era o dilema que o Project Loom resolveu.

## Virtual threads (JDK 21, permanente)

A platform thread é cara. A virtual thread é leve o bastante pra criar uma por tarefa, mesmo que sejam 100.000. O
segredo: quando uma virtual thread bloqueia em I/O, o JVM a desmonta de uma platform thread (o *carrier*) e entrega o
carrier pra outra virtual thread. O código continua síncrono e bloqueante, mas
"esperar" não custa mais: enquanto uma tarefa aguarda o banco, outras usam o mesmo carrier.

```java
try(var executor = Executors.newVirtualThreadPerTaskExecutor()){
    IntStream.

range(0,10_000).

forEach(i ->executor.

submit(() ->{

fetchRemote(i);
    }));
        } // close() espera todas terminarem
```

Diferenças pro pool:

- `newVirtualThreadPerTaskExecutor()` cria uma virtual thread **por tarefa**, sem pool e sem limite prático.
- O `close()` do `ExecutorService` (JDK 19) espera as tarefas terminarem e funciona com try-with-resources.

Criação direta, com nome pra facilitar debug:

```java
Thread.ofVirtual().

name("request-").

start(() ->

handleRequest());
```

O ganho é pra **I/O-bound**: requisições esperando banco, API externa, fila. Código **CPU-bound** não ganha nada, porque
virtual thread não acelera cálculo — o carrier é a mesma thread de verdade.

Pra programar virtual thread, o resto funciona igual: `Thread.sleep`,
`synchronized`, `Future.get` e `join` seguem valendo.

### Carriers e pinning

Uma virtual thread roda montada num **carrier**, uma platform thread do scheduler. Quando ela bloqueia em I/O, desmonta
e o carrier atende outra virtual thread. Por isso o custo é baixo: a thread de verdade não fica parada esperando.

O caso que quebra o esquema é o **pinning**: a virtual thread fica presa ao carrier e não desmonta. Acontece dentro de
bloco `synchronized` e em chamada a método nativo que bloqueia. O carrier fica ocupado e não atende ninguém. Com poucas
virtual threads pinned, o impacto é pequeno; com muitas, o ganho vai embora.

```java
// pinning: synchronized segura o carrier durante o bloqueio
synchronized (lock){

blockingIoCall();   // virtual thread não desmonta aqui
}
```

O `ReentrantLock` não causa pinning: desmonta a virtual thread normalmente. Por isso bibliotecas como HikariCP trocaram
`synchronized` por
`ReentrantLock`.

### Comparação com Node/TS

| Conceito         | Java                                | Node/TS                  |
|------------------|-------------------------------------|--------------------------|
| Concorrência     | virtual threads (bloqueante barato) | event loop + async/await |
| Paralelismo real | platform threads / carriers         | `worker_threads`         |
| I/O concorrente  | virtual thread por requisição       | `Promise.all`            |

O Node resolve I/O concorrente com `async/await` e event loop. O Java moderno resolve com uma thread bloqueante por
tarefa, mas virtual. O código fica síncrono, sem `await`, e a espera é de graça.

## Estado compartilhado

Quando threads compartilham estado, três problemas aparecem:

1. **Atomicidade**: `count++` são três passos (ler, somar, escrever). Duas threads podem intercalar e perder um
   incremento.
2. **Visibilidade**: sem sincronização, uma thread pode não ver a escrita de outra, porque o valor fica em cache local.
3. **Ordem**: o compilador e o CPU podem reordenar instruções se ninguém sincroniza.

As ferramentas abaixo resolvem combinações desses problemas. A ordem segue do controle mais simples pro mais específico.

### `volatile` — só visibilidade

```java
public class Flag {
  private volatile boolean running = true;

  public void stop() {
    running = false;
  }
}
```

`volatile` garante que a escrita fica visível pra outras threads, sem cache local. Resolve visibilidade, não
atomicidade: `count++` com `volatile`
continua com corrida. Use pra flag e estado simples; pra contador, `Atomic*`.

### `synchronized` — exclusão mútua bruta

```java
public class Counter {
  private int count;

  public synchronized void increment() {
    count++;
  }

  public synchronized int value() {
    return count;
  }
}
```

O lock é implícito no monitor do objeto. Resolve atomicidade e visibilidade de uma vez, mas é grosseiro: o método
inteiro fica serializado. Custa pinning em virtual thread se o método bloqueia em I/O.

### `ReentrantLock` — o lock explícito

O `ReentrantLock` é um lock da `java.util.concurrent.locks` com controle manual:

```java
private final ReentrantLock lock = new ReentrantLock();

public void transfer(Account from, Account to, BigDecimal amount) {
  lock.lock();
  try {
    from.debit(amount);
    to.credit(amount);
  } finally {
    lock.unlock();
  }
}
```

Diferenças do `synchronized`:

- `lock()` e `unlock()` manuais. O `unlock` no `finally` é obrigatório, senão o lock fica preso.
- `tryLock()` tenta adquirir com timeout em vez de bloquear pra sempre:

```java
if(lock.tryLock(2,TimeUnit.SECONDS)){
    try{
    // região crítica
    }finally{
    lock.

unlock();
    }
        }else{
        throw new

BusyException("recurso ocupado");
}
```

- `lockInterruptibly()` deixa interromper a espera.
- Não causa pinning em virtual threads.

`tryLock` com timeout é também a ferramenta básica contra deadlock (seção abaixo). O `synchronized` continua a escolha
certa pra região crítica curta e simples; o `ReentrantLock` entra quando você precisa de timeout, interrupção ou justiça
(fairness).

### `ReadWriteLock` — leitores juntos, escritor sozinho

Separa leitores (concorrentes entre si) de escritor (exclusivo):

```java
private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

public BigDecimal balance() {
  rwLock.readLock().lock();
  try {
    return balance;
  } finally {
    rwLock.readLock().unlock();
  }
}

public void deposit(BigDecimal amount) {
  rwLock.writeLock().lock();
  try {
    balance = balance.add(amount);
  } finally {
    rwLock.writeLock().unlock();
  }
}
```

Vários leitores entram juntos; o escritor espera todos os leitores saírem. Ganha quando a leitura é muito mais frequente
que a escrita. Se a escrita é frequente, o lock simples ou atômicos saem na frente.

### `Semaphore` — limite de N de cada vez

Controla quantas threads entram numa região:

```java
private final Semaphore permits = new Semaphore(3);   // até 3 de cada vez

public void process() throws InterruptedException {
  permits.acquire();
  try {
    // no máximo 3 processos concorrentes
  } finally {
    permits.release();
  }
}
```

Limita acesso a recurso (pool externo, conexão com API, I/O de disco) sem custo de thread fixa. Lembra um pool, mas por
permissão, não por thread.

### `CountDownLatch` e `CyclicBarrier` — sincronizar início e fim

- `CountDownLatch`: espera N eventos acontecerem uma vez. Uso clássico:
  esperar N threads terminarem antes de seguir.

```java
CountDownLatch ready = new CountDownLatch(3);

// cada worker chama ready.countDown() ao terminar
ready.

await(10,TimeUnit.SECONDS);   // segue quando chegar a 0
```

- `CyclicBarrier`: espera N threads se encontrarem num ponto e segue todas juntas. Reutilizável, por isso "cyclic". Uso:
  fases de processamento em paralelo.

```java
CyclicBarrier barrier = new CyclicBarrier(3);
// cada thread faz uma fase e chama barrier.await()
// todas se liberam juntas quando as 3 chegam
```

Regra de bolso: latch conta **uma vez** (fire-and-forget), barrier conta **por rodada** (todas sincronizam e continuam).

### `BlockingQueue` — produtor-consumidor pronto

Fila thread-safe que bloqueia no `put` (cheia) e no `take` (vazia):

```java
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(100);

// produtor
queue.

put(task);        // bloqueia se a fila estiver cheia

// consumidor
Task task = queue.take();   // bloqueia se a fila estiver vazia
```

Padrão produtor-consumidor pronto, sem `wait`/`notify` manual. Com virtual threads, uma thread por consumidor funciona
bem. Implementações:
`ArrayBlockingQueue` (tamanho fixo), `LinkedBlockingQueue`,
`PriorityBlockingQueue` (com prioridade).

### `Atomic*` — contadores sem lock

Pra contadores e flags, os atômicos resolvem atomicidade com CAS (compare-and-swap), sem monitor:

```java
AtomicInteger counter = new AtomicInteger();
counter.

incrementAndGet();
counter.

addAndGet(5);
```

`AtomicInteger`, `AtomicLong`, `AtomicBoolean`, `AtomicReference`. Use pra contador e flag simples; pra estrutura maior,
o lock ou a estrutura thread-safe.

### `ConcurrentHashMap` — o map thread-safe

O `HashMap` não é thread-safe. O `ConcurrentHashMap` é:

```java
ConcurrentMap<String, Integer> stock = new ConcurrentHashMap<>();
stock.

computeIfAbsent("sku",this::loadStock);
```

Nunca compartilhe `HashMap`/`ArrayList` entre threads sem sincronização externa. Não existe "HashMap thread-safe de
graça".

## A armadilha do `for` paralelo

O erro clássico: mutar estado de fora do loop.

```java
// RUIM: contador compartilhado com += em paralelo
var total = new long[1];
IntStream.

range(0,1000).

parallel().

forEach(i ->total[0]+=i);
```

`parallelStream()` (módulo 13) entra na mesma regra: dividir trabalho entre threads só é seguro quando cada elemento é
independente e a redução é associativa. Mutar variável externa dentro do `parallel()` é corrida de dados.

## Deadlock

Deadlock é o estado em que duas threads se esperam pra sempre: cada uma segura um lock e espera o lock que a outra
segura.

```java
// thread A: segura lockA, espera lockB
// thread B: segura lockB, espera lockA
```

Prevenção:

- **Ordem fixa de locks**: todas as threads adquirem os locks na mesma ordem (`lockA` antes de `lockB`). Inverteu a
  ordem, o deadlock aparece.
- **`tryLock` com timeout**: em vez de bloquear pra sempre, tenta com prazo e desiste (o módulo 17 tem um caso real com
  transações).
- **Menos locks**: cada lock a mais é um ponto de deadlock a mais. Lock granular demais aumenta a superfície, não
  diminui.

## Composição assíncrona com `CompletableFuture`

`CompletableFuture` é um valor que vai estar disponível no futuro, e que você encadeia sem bloquear thread:

```java
CompletableFuture.supplyAsync(this::fetchUser)          // executa em pool
        .

thenApply(User::name)
        .

thenApply(String::toUpperCase)
        .

thenAccept(name ->log.

info("user: {}",name))
    .

exceptionally(err ->{
    log.

error("falhou",err);
            return null;
                });
```

- `supplyAsync` roda a tarefa num pool comum.
- `thenApply` transforma o resultado quando ele chega.
- `thenAccept` consome o resultado, sem devolver nada.
- `exceptionally` define o que fazer se o pipeline falhar.

A diferença pros `Future` do `ExecutorService`: você não bloqueia esperando; registra a continuação. Útil pra pipeline
de eventos e processamento em background.

### Combinar futuros

Pra juntar o resultado de dois futuros independentes, `thenCombine`:

```java
CompletableFuture<BigDecimal> total = fetchOrders()
    .thenCombine(fetchPayments(), (orders, payments) ->
        orders.stream().map(Order::total).reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(payments.stream().map(Payment::value).reduce(BigDecimal.ZERO, BigDecimal::add)));
```

Pra esperar N futuros de uma vez, `allOf`:

```java
CompletableFuture<List<Order>> ordersFuture = fetchOrders();
CompletableFuture<List<Payment>> paymentsFuture = fetchPayments();

CompletableFuture<Void> both = CompletableFuture.allOf(ordersFuture, paymentsFuture);

both.

join();   // espera os dois

var orders = ordersFuture.join();
var payments = paymentsFuture.join();
```

- `allOf` espera todos. Como os futuros podem ter tipos diferentes, devolve
  `Void`, e você lê o resultado de cada um depois do `join`. O `join()` (em vez de `get()`) não lança checked exception.
- `anyOf` completa quando o primeiro dos futuros termina. Útil pra hedging:
  dispara duas fontes e usa quem responder primeiro.

Se o problema é esperar um grupo de tarefas com o mesmo ciclo de vida (todas nascem, todas terminam), o
`StructuredTaskScope` lê melhor, seção lá embaixo.

## Scoped Values (JDK 25, permanente)

Pra passar contexto por uma cadeia de chamadas (id de requisição, usuário logado), o jeito antigo era `ThreadLocal`. Com
virtual threads, `ScopedValue`
é o caminho recomendado: um valor imutável por escopo de execução, que propaga pra threads filhas.

```java
static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

void handleRequest(String requestId) {
  ScopedValue.where(REQUEST_ID, requestId)
      .run(() -> process());
}

void process() {
  // dentro da run, REQUEST_ID.get() devolve o requestId
  String id = REQUEST_ID.get();
}
```

O valor vale dentro do bloco `run` e some quando o bloco termina. `ThreadLocal`
ainda funciona, mas o `ScopedValue` é mais barato e mais fácil de raciocinar:
não vaza valor entre requisições.

## Structured Concurrency (preview no Java 25)

O `StructuredTaskScope` trata um grupo de tarefas como uma unidade: todas nascem, todas terminam, ou todas são
canceladas. É preview (JEP 505), então requer `--enable-preview` e pode mudar.

```java
try(var scope = StructuredTaskScope.open()){
Supplier<String> user = scope.fork(this::fetchUser);
Supplier<String> order = scope.fork(this::fetchOrder);

    scope.

join();        // espera as duas, ou cancela tudo se uma falhar

String result = user.get() + " / " + order.get();
}
```

O ponto forte: se `fetchUser` falhar, o `join()` cancela `fetchOrder` junto. Sem thread vazando, sem espera órfã.

## Comparação com TypeScript

| Conceito             | Java                           | Node/TS                              |
|----------------------|--------------------------------|--------------------------------------|
| Concorrência         | virtual threads                | event loop / async                   |
| Tarefa assíncrona    | `CompletableFuture`            | `Promise`                            |
| Composição           | `thenCombine`, `thenCompose`   | `Promise.all`, chaining              |
| Estado compartilhado | `Atomic*`, `ConcurrentHashMap` | single-thread (sem corrida de dados) |
| Cancellation         | `Future.cancel`                | `AbortController`                    |

O Node tem um só thread pra código JS, então não existe corrida de dados no seu código: o runtime cuida. O Java te dá
paralelismo real e cobra por isso:
você é responsável pelo estado compartilhado.

## O que mudou entre versões

| Feature                   | Versão  | Situação                    |
|---------------------------|---------|-----------------------------|
| `CompletableFuture`       | JDK 8   | Permanente                  |
| `ExecutorService.close()` | JDK 19  | Permanente                  |
| `Future.resultNow()`      | JDK 19  | Permanente                  |
| Virtual threads           | JDK 21  | Permanente                  |
| Scoped Values             | JDK 25  | Permanente                  |
| Structured Concurrency    | JDK 21+ | Preview (JEP 505, 5ª no 25) |

## Exercícios

1. Escreva um programa que dispara 100 tarefas de I/O simulado (ex.: cada uma faz `Thread.sleep(50)` pra representar
   espera de rede) usando
   `newVirtualThreadPerTaskExecutor` e mede o tempo total com
   `System.nanoTime`. Repita com `newFixedThreadPool(4)`. Esperado: com virtual threads o total fica perto de ~50ms (uma
   leva só); com 4 threads, perto de 50ms × 25 = 1.250ms. Explique por quê.
2. Escreva um `Counter` seguro com `AtomicInteger` e outro com `synchronized`. Teste com 10.000 incrementos de 10
   threads e verifique que o resultado é sempre 100.000 (use `CountDownLatch` pra sincronizar o fim das threads).
3. Use `CompletableFuture` pra buscar dois dados em paralelo e combinar com
   `thenCombine`. Faça uma das fontes lançar exceção e compare dois cenários:
   com `exceptionally` (que devolve um fallback, ex.: `BigDecimal.ZERO`) e sem ele. Esperado: sem `exceptionally`, o
   `join()` lança `CompletionException`
   embrulhando a causa real.

## Referências

- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444) — o JEP que tornou virtual threads uma feature permanente
  (JDK 21)
- [Virtual Threads (Java Core docs)](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html) — criação,
  scheduling, pinning e debugging de virtual threads
- [Virtual Threads (Dev.java)](https://dev.java/learn/new-features/virtual-threads/) — guia prático com
  `Thread.ofVirtual` e `newVirtualThreadPerTaskExecutor`
- [JEP 505 — Structured Concurrency (Fifth Preview)](https://openjdk.org/jeps/505) — o `StructuredTaskScope` e o padrão
  de fan-out (preview)
- [JEP 506 — Scoped Values](https://openjdk.org/jeps/506) — o substituto do `ThreadLocal` (JDK 25)

## Próximo módulo

**JDBC e Banco de Dados** — conexão, `PreparedStatement`, transações e o padrão de acesso a dados com JDBC puro.

[→ 17 — JDBC e Banco de Dados](./17-jdbc.md)