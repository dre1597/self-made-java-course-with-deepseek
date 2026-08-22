# 16 — Concorrência

Threads, `ExecutorService`, virtual threads, `CompletableFuture` e os riscos
de estado compartilhado. O modelo mental de concorrência no Java mudou com o
Project Loom: a partir do JDK 21, thread é barata, e o jeito de escrever
paralelismo mudou junto.

## Os blocos de construção

### Thread clássica (ainda existe, mas raro)

```java
Thread thread = new Thread(() -> System.out.println("rodando"));
thread.start();
thread.join();        // espera terminar
```

### ExecutorService: o padrão pré-virtual-threads

```java
ExecutorService pool = Executors.newFixedThreadPool(4);

Future<String> future = pool.submit(() -> fetchData());
String result = future.get();        // bloqueia até o resultado

pool.shutdown();                     // não aceita mais tarefas
```

O `Future.get()` é síncrono e bloqueia. Se você precisa de várias chamadas em
paralelo, `invokeAll`:

```java
List<Callable<BigDecimal>> tasks = List.of(
        () -> totalFromDatabase(),
        () -> totalFromApi()
);
List<Future<BigDecimal>> futures = pool.invokeAll(tasks);
```

Problema do pool de threads: o tamanho é limitado e cada thread custa ~1MB de
stack. Se você cria 10.000 threads de verdade, o sistema sofre.

## Virtual threads (JDK 21, permanente)

Thread é cara quando é *platform thread*. A virtual thread é leve, custa
quase nada e você pode criar uma por tarefa:

```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    IntStream.range(0, 10_000).forEach(i -> executor.submit(() -> {
        // cada tarefa roda numa virtual thread própria
        fetchRemote(i);
    }));
} // close() espera todas terminarem
```

O que muda:

- `newVirtualThreadPerTaskExecutor()` cria uma virtual thread **por tarefa**,
  sem pool. O número é ilimitado.
- A virtual thread monta e desmonta de um *carrier thread* de verdade quando
  bloqueia em I/O. Enquanto a thread espera o banco responder, outra tarefa
  usa o carrier.
- O `ExecutorService.close()` (JDK 19) fecha e espera, e funciona com
  try-with-resources.

O ganho é pra **I/O concorrente**: milhares de requisições esperando banco,
API externa, fila. Código CPU-bound (processamento pesado) não ganha, porque
virtual thread não acelera cálculo.

```java
Thread.ofVirtual().name("request-").start(() -> handleRequest());
```

O `Thread.Builder` cria threads nomeadas. Pra programar virtual thread,
`Thread.sleep`, `synchronized`, `Future.get` e `join` funcionam normalmente.

### Carriers, montagem e pinning

A virtual thread roda montada numa *carrier thread* (uma platform thread do
pool do scheduler). Quando ela bloqueia em I/O, desmonta e o carrier recebe
outra virtual thread. Por isso o custo é baixo: a thread de verdade não fica
parada esperando.

O caso que atrapalha é o **pinning**: a virtual thread fica presa ao carrier
e não desmonta. Acontece quando o código roda dentro de um bloco
`synchronized` ou chama método nativo que bloqueia. Enquanto isso, o carrier
fica ocupado e outra virtual thread não entra. Com poucas virtual threads
pinned, o impacto some; com muitas, o ganho cai.

```java
// pinning: synchronized segura o carrier durante o bloqueio
synchronized (lock) {
    blockingIoCall();   // virtual thread não desmonta aqui
}
```

O `ReentrantLock` (abaixo) não causa pinning; ele desmonta a virtual thread
normalmente. É o motivo de bibliotecas como HikariCP terem trocado
`synchronized` por `ReentrantLock`.

### Comparação com Node/TS

| Conceito | Java | Node/TS |
| -------- | ---- | ------- |
| Concorrência | virtual threads (bloqueante barato) | event loop + async/await |
| Paralelismo real | platform threads / carriers | `worker_threads` |
| I/O concorrente | virtual thread por requisição | `Promise.all` |

O Node resolve I/O concorrente com `async/await` e event loop; o Java moderno
resolve com uma thread bloqueante por tarefa, mas virtual. O código fica
síncrono (sem `await`), e a thread "espera" de graça.

## `CompletableFuture`

Para composição assíncrona, o `CompletableFuture` continua sendo a ferramenta
de encadeamento:

```java
CompletableFuture.supplyAsync(this::fetchUser)          // executa em pool
        .thenApply(User::name)
        .thenApply(String::toUpperCase)
        .thenAccept(name -> log.info("user: {}", name))
        .exceptionally(err -> {
            log.error("falhou", err);
            return null;
        });
```

Combinando futuros:

```java
CompletableFuture<BigDecimal> total = fetchOrders()
        .thenCombine(fetchPayments(), (orders, payments) ->
                orders.stream().map(Order::total).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .add(payments.stream().map(Payment::value).reduce(BigDecimal.ZERO, BigDecimal::add)));
```

Quando usar: pipelines de eventos, processamento em background, composição
non-blocking. Quando o problema é *fan-out* (esperar várias tarefas e juntar o
resultado), `StructuredTaskScope` lê melhor (módulo 16 e preview).

## Estado compartilhado

### `synchronized`

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

O lock é implícito no monitor do objeto. Garante exclusão mútua, mas é
grosseiro: todo o método fica serializado.

### `ReentrantLock`

O `synchronized` é implícito no monitor do objeto. O `ReentrantLock` é um lock
explícito da `java.util.concurrent.locks`, com mais controle:

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

- `lock.lock()`/`unlock()` manual. O `unlock` no `finally` é obrigatório, senão
  o lock fica preso.
- `tryLock()` tenta adquirir sem bloquear pra sempre, com timeout:

```java
if (lock.tryLock(2, TimeUnit.SECONDS)) {
    try {
        // região crítica
    } finally {
        lock.unlock();
    }
} else {
    throw new BusyException("recurso ocupado");
}
```

- `lockInterruptibly()` permite interromper a espera.
- Não causa pinning em virtual threads (ao contrário do `synchronized`).

O `synchronized` continua a opção certa pra região crítica curta e simples.
O `ReentrantLock` entra quando você precisa de timeout, interrupção ou justiça
(fairness).

### `ReadWriteLock`

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

Vários leitores entram juntos; o escritor espera todos os leitores saírem.
Ganha quando leitura é muito mais frequente que escrita. Se a escrita é
frequente, o lock simples ou atômicos saem na frente.

### `Semaphore`

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

Útil pra limitar acesso a recurso (pool externo, conexão com API, IO de
disco) sem pagar o custo de thread. Lembra um pool, mas por "permissão",
não por thread fixa.

### `CountDownLatch` e `CyclicBarrier`

- `CountDownLatch`: espera N eventos acontecerem uma vez. Uso clássico:
  esperar N threads terminarem antes de seguir.

```java
CountDownLatch ready = new CountDownLatch(3);

// cada worker chama ready.countDown() ao terminar
ready.await(10, TimeUnit.SECONDS);   // segue quando chegar a 0
```

- `CyclicBarrier`: espera N threads se encontrarem num ponto e segue todas
  juntas. Reutilizável (daí o "cyclic"). Uso: fases de processamento em
  paralelo.

```java
CyclicBarrier barrier = new CyclicBarrier(3);
// cada thread faz uma fase e chama barrier.await()
// todas se liberam juntas quando as 3 chegam
```

Regra de bolso: latch conta **para sempre** (fire-and-forget), barrier conta
**por rodada** (todas sincronizam e continuam).

### `BlockingQueue`

Fila thread-safe que bloqueia no `put` (cheia) e no `take` (vazia):

```java
BlockingQueue<Task> queue = new ArrayBlockingQueue<>(100);

// produtor
queue.put(task);        // bloqueia se a fila estiver cheia

// consumidor
Task task = queue.take();   // bloqueia se a fila estiver vazia
```

Padrão produtor-consumidor pronto, sem `wait`/`notify` manual. Com virtual
threads, um thread por consumidor funciona bem. Implementações:
`ArrayBlockingQueue` (tamanho fixo), `LinkedBlockingQueue`,
`PriorityBlockingQueue` (com prioridade).

### `Atomic*`

Para contadores e flags, os atômicos são melhores que `synchronized`:

```java
AtomicInteger counter = new AtomicInteger();
counter.incrementAndGet();
counter.addAndGet(5);
```

`AtomicInteger`, `AtomicLong`, `AtomicBoolean`, `AtomicReference` usam
CAS (compare-and-swap) sem lock de monitor.

### `ConcurrentHashMap`

O `HashMap` não é thread-safe. O `ConcurrentHashMap` é:

```java
ConcurrentMap<String, Integer> stock = new ConcurrentHashMap<>();
stock.computeIfAbsent("sku", this::loadStock);
```

Nunca use `HashMap`/`ArrayList` compartilhado entre threads sem sincronização
externa. Não existe "HashMap thread-safe de graça".

## Corridas e a armadilha do `for` paralelo

O erro clássico: mutar estado de fora do loop.

```java
// RUIM: contador compartilhado com += em paralelo
var total = new long[1];
IntStream.range(0, 1000).parallel().forEach(i -> total[0] += i);
```

`parallelStream()` (módulo 13) também está aqui: dividir trabalho entre
threads só é seguro quando cada elemento é independente e você reduz de forma
associativa, não muta variável externa.

## Deadlock

Deadlock é o estado em que duas threads se esperam pra sempre: cada uma
segura um lock e espera o que a outra segura.

```java
// thread A: segura lockA, espera lockB
// thread B: segura lockB, espera lockA
```

Prevenção:

- **Ordem fixa de locks**: todas as threads adquirem os locks na mesma ordem
  (`lockA` antes de `lockB`). Inverteu a ordem, o deadlock aparece.
- **`tryLock` com timeout**: em vez de bloquear pra sempre, tenta com prazo e
  desiste (módulo 17 tem um caso real com transações).
- **Menos locks**: cada lock a mais é um ponto de deadlock a mais.
  `ReentrantLock` e locks mais granulares aumentam a superfície, não
  diminuem.

## `volatile`

```java
public class Flag {
    private volatile boolean running = true;

    public void stop() {
        running = false;
    }
}
```

`volatile` garante que a escrita é visível pra outras threads (não fica em
cache local). Só resolve visibilidade, não atomicidade. `count++` com
`volatile` continua com corrida. Use `volatile` pra flag e estado simples;
`Atomic*` pra contador.

## `CompletableFuture.allOf` / `anyOf`

Combinar futuros:

```java
CompletableFuture<List<Order>> ordersFuture = fetchOrders();
CompletableFuture<List<Payment>> paymentsFuture = fetchPayments();

CompletableFuture<Void> both = CompletableFuture.allOf(ordersFuture, paymentsFuture);

both.join();   // espera os dois

var orders = ordersFuture.join();
var payments = paymentsFuture.join();
```

- `allOf` espera todos. `join()` (não `get()`) não lança checked exception.
- `anyOf` completa quando o primeiro dos futuros termina. Útil pra timeout
  distribuído (hedging): dispara 2 fontes, usa quem responder primeiro.
- Quando os futuros têm tipos diferentes, `allOf` devolve `Void` e você lê os
  resultados de cada um depois do `join`.

## Scoped Values (JDK 25, permanente)

Substitui o `ThreadLocal` no mundo de virtual threads:

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

`ScopedValue` define um valor imutável por escopo de execução, incluindo
threads filhas. Em virtual threads, `ThreadLocal` ainda funciona, mas
`ScopedValue` é o caminho recomendado pra contexto de requisição.

## Structured Concurrency (preview no Java 25)

O `StructuredTaskScope` trata um grupo de tarefas como uma unidade: todas
nascem, todas terminam, ou todas são canceladas. Preview (JEP 505), então
requer `--enable-preview` e pode mudar. Conhecer o conceito vale:

```java
try (var scope = StructuredTaskScope.open()) {
    Supplier<String> user = scope.fork(this::fetchUser);
    Supplier<String> order = scope.fork(this::fetchOrder);

    scope.join();        // espera as duas, ou cancela tudo se uma falhar
    String result = user.get() + " / " + order.get();
}
```

O ponto forte: se `fetchUser` falhar, o `join()` cancela `fetchOrder` junto.
Sem thread vazando, sem espera órfã.

## Comparação com TypeScript

| Conceito | Java | Node/TS |
| -------- | ---- | ------- |
| Concorrência | virtual threads | event loop / async |
| Tarefa assíncrona | `CompletableFuture` | `Promise` |
| Composição | `thenCombine`, `thenCompose` | `Promise.all`, chaining |
| Estado compartilhado | `Atomic*`, `ConcurrentHashMap` | single-thread (sem corrida de dados) |
| Cancellation | `Future.cancel` | `AbortController` |

O Node tem a vantagem de um só thread pra código JS: não existe corrida de
dados no seu código (o runtime cuida). O Java te dá paralelismo real de
verdade, e cobra por isso: você é responsável pelo estado compartilhado.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| `CompletableFuture` | JDK 8 | Permanente |
| Virtual threads | JDK 21 | Permanente |
| `ExecutorService.close()` | JDK 19 | Permanente |
| `Future.resultNow()` | JDK 19 | Permanente |
| Scoped Values | JDK 25 | Permanente |
| Structured Concurrency | JDK 21+ | Preview (JEP 505, 5ª no 25) |

## Exercícios

1. Escreva um programa que baixa 100 URLs usando `newVirtualThreadPerTaskExecutor`
   e mede o tempo. Depois troque por `newFixedThreadPool(4)` e compare. Qual
   é mais rápido e por quê?
2. Escreva um `Counter` seguro com `AtomicInteger` e outro com `synchronized`.
   Teste com 10.000 incrementos de 10 threads e verifique que o resultado é
   sempre 100.000 (use `CountDownLatch` pra sincronizar o fim).
3. Use `CompletableFuture` pra buscar dois dados em paralelo e combinar com
   `thenCombine`. Teste o caminho de exceção: o que `exceptionally` faz? E se
   você não tratar?
4. Escreva um método que usa `StructuredTaskScope` (com `--enable-preview`) pra
   fazer fan-out de duas chamadas e juntar o resultado. Faça uma delas falhar e
   observe o que acontece com a outra.

## Referências

- [JEP 444 — Virtual Threads](https://openjdk.org/jeps/444) — o JEP que tornou virtual threads uma feature permanente (JDK 21)
- [Virtual Threads (Java Core docs)](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html) — criação, scheduling, pinning e debugging de virtual threads
- [Virtual Threads (Dev.java)](https://dev.java/learn/new-features/virtual-threads/) — guia prático com `Thread.ofVirtual` e `newVirtualThreadPerTaskExecutor`
- [JEP 505 — Structured Concurrency (Fifth Preview)](https://openjdk.org/jeps/505) — o `StructuredTaskScope` e o padrão de fan-out (preview)
- [JEP 506 — Scoped Values](https://openjdk.org/jeps/506) — o substituto do `ThreadLocal` (JDK 25)

## Próximo módulo

**JDBC e Banco de Dados** — conexão, `PreparedStatement`, transações e o
padrão de acesso a dados com JDBC puro.

[→ 17 — JDBC e Banco de Dados](./17-jdbc.md)