# 13 — Lambdas e Streams

Interfaces funcionais, lambdas, method references e o pipeline de streams.
Este é o módulo que mais muda o jeito de escrever Java: de loop imperativo pra
declaração do que você quer, não de como.

## Interfaces funcionais

Uma interface funcional tem **um único método abstrato**. O `@FunctionalInterface`
avisa o compilador e quem lê:

```java
@FunctionalInterface
public interface DiscountCalculator {
    BigDecimal calculate(BigDecimal total);
}
```

As interfaces funcionais que você usa todo dia vêm da JDK, em
`java.util.function`:

| Interface | Assinatura | Uso |
| --------- | ---------- | --- |
| `Function<T, R>` | `R apply(T)` | transformar T em R |
| `BiFunction<T, U, R>` | `R apply(T, U)` | transformar dois valores |
| `Consumer<T>` | `void accept(T)` | efeito colateral, não retorna |
| `Supplier<T>` | `T get()` | produzir valor |
| `Predicate<T>` | `boolean test(T)` | teste verdadeiro/falso |
| `UnaryOperator<T>` | `T apply(T)` | T em T |
| `BinaryOperator<T>` | `T apply(T, T)` | dois T em T |

Compare com TS: `Function<T, R>` é o `(t: T) => R`; `Predicate<T>` é o
`(t: T) => boolean`; `Supplier<T>` é `() => T`. A diferença: no Java os tipos
de função têm nome e vêm prontos da JDK, no TS você escreve o tipo na mão.

## Lambdas

```java
DiscountCalculator flat = total -> total.subtract(BigDecimal.TEN);
DiscountCalculator percentage = total -> total.multiply(BigDecimal.valueOf(0.90));
```

Formas:

```java
// sem parâmetro
Supplier<String> now = () -> LocalDateTime.now().toString();

// um parâmetro, inferido
Function<String, Integer> length = s -> s.length();

// parâmetro tipado
Function<String, Integer> lengthTyped = (String s) -> s.length();

// dois parâmetros, corpo de bloco
BiFunction<Integer, Integer, Integer> add = (a, b) -> {
    int result = a + b;
    return result;
};
```

Regras:

- Parâmetro único dispensa parênteses; zero ou mais de um exige.
- `(a, b) -> { ... return ...; }` precisa de `return` explícito. Expressão
  `(a, b) -> a + b` devolve direto.
- Lambda captura variáveis **efetivamente finais**: a variável do escopo
  externo precisa ser atribuída uma vez. Reassinar dentro ou fora não compila.

```java
int base = 10;
Function<Integer, Integer> addBase = x -> x + base;   // ok
// base = 20;   // erro: base precisa ser efetivamente final
```

## Method references

Atalho pra lambda que só chama um método existente. Existem quatro formas, e
cada uma tem um lugar certo:

### 1. Método estático: `Classe::metodoEstatico`

```java
Function<String, Integer> parse = Integer::parseInt;      // s -> Integer.parseInt(s)
Supplier<LocalDate> today = LocalDate::now;               // () -> LocalDate.now()
Function<BigDecimal, BigDecimal> negate = BigDecimal::negate;
```

```java
var ids = rawLines.stream()
        .map(Integer::parseInt)
        .toList();
```

### 2. Método de instância num objeto específico: `objeto::metodo`

```java
String prefix = "[log] ";
Function<String, String> withPrefix = prefix::concat;     // s -> prefix.concat(s)

List<String> names = products.stream()
        .map(Product::name)
        .map("[produto] "::concat)                        // receiver fixo
        .toList();
```

```java
Consumer<String> logger = System.out::println;            // s -> System.out.println(s)
```

### 3. Método de instância com o argumento como receptor: `Classe::metodoInstancia`

```java
Function<String, Integer> length = String::length;        // s -> s.length()
BiPredicate<String, String> contains = String::contains;  // (s, sub) -> s.contains(sub)
Function<String, String> upper = String::toUpperCase;

Comparator<String> byLength = Comparator.comparingInt(String::length);
```

Aqui o **primeiro parâmetro vira o receptor** da chamada. Em streams, é o
padrão pra acessor:

```java
var names = products.stream()
        .map(Product::name)        // p -> p.name()
        .map(String::toLowerCase)  // s -> s.toLowerCase()
        .toList();
```

### 4. Construtor: `Classe::new`

```java
Supplier<Product> factory = Product::new;                 // () -> new Product()
Function<String, Product> byName = Product::new;          // name -> new Product(name)

var products = skus.stream()
        .map(Product::new)
        .toList();
```

### Quando usar cada uma

| Forma | Exemplo | Lambda equivalente |
| ----- | ------- | ------------------ |
| Estático | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| Objeto fixo | `prefix::concat` | `s -> prefix.concat(s)` |
| Receptor no arg | `String::toUpperCase` | `s -> s.toUpperCase()` |
| Construtor | `ArrayList::new` | `() -> new ArrayList<>()` |

Regra de bolso: se a lambda só **repassa o parâmetro pra um método**, a
method reference é mais curta e clara. Se você faz mais de uma coisa no corpo,
lambda. O `::` é a única sintaxe que o TS não tem igual; lá o equivalente é
passar a função direto, tipo `array.map(parseInt)`, e o `this` do receptor
fica implícito no contexto.

## Streams: o pipeline

Stream é uma sequência **preguiçosa**: operações intermediárias não rodam até
chegar uma operação terminal.

```java
var result = products.stream()
        .filter(p -> p.price().signum() > 0)       // intermediate
        .map(Product::name)                         // intermediate
        .sorted()                                    // intermediate
        .toList();                                   // terminal, dispara tudo
```

- **Intermediate**: devolvem outro stream, compõem. Não executam nada ainda.
- **Terminal**: executam o pipeline inteiro (`toList`, `forEach`, `count`,
  `reduce`, `collect`, `findFirst`, `anyMatch`).
- Stream é single-use: uma vez que a operação terminal rodou, o stream morre.

### Um pipeline completo

Um caso real: top 5 produtos em estoque, ordenados por preço decrescente.

```java
record Product(String sku, String name, int stock, BigDecimal price) {}

List<Product> top5 = products.stream()
        .filter(p -> p.stock() > 0)                        // só tem no estoque
        .sorted(Comparator.comparing(Product::price).reversed())  // do mais caro
        .limit(5)
        .toList();
```

Versão imperativa do mesmo código (pra você sentir a diferença):

```java
List<Product> inStock = new ArrayList<>();
for (Product p : products) {
    if (p.stock() > 0) {
        inStock.add(p);
    }
}
inStock.sort((a, b) -> b.price().compareTo(a.price()));
List<Product> top5 = inStock.subList(0, Math.min(5, inStock.size()));
```

O stream descreve o resultado; o loop descreve o passo a passo. Os dois fazem
o mesmo. O stream sai menor e sem estado mutável no caminho.

### Operações intermediárias

```java
stream.filter(p -> p.price() > 100)        // mantém quem casa
      .map(Product::name)                  // transforma cada elemento
      .flatMap(category -> category.products().stream())  // achata lista de listas
      .distinct()                          // remove duplicados
      .sorted(Comparator.comparing(Product::price))  // ordena
      .limit(10)                           // corta no décimo
      .skip(2)                             // pula os dois primeiros
      .peek(p -> log(p))                   // espião, só pra debug
```

`flatMap` é o que mais confunde. `List<List<String>>` vira `Stream<String>`:

```java
List<List<Integer>> matrix = List.of(List.of(1, 2), List.of(3, 4));
List<Integer> flat = matrix.stream().flatMap(List::stream).toList();
// [1, 2, 3, 4]
```

### Comparators

`Comparator.comparing` compõe com method references e encadeia:

```java
products.sort(Comparator.comparing(Product::price));                    // crescente
products.sort(Comparator.comparing(Product::price).reversed());         // decrescente
products.sort(Comparator.comparing(Product::category)
        .thenComparing(Product::price));                                 // categoria, depois preço
```

`Comparator.nullsFirst`/`nullsLast` tratam `null` sem quebrar o sort:

```java
products.sort(Comparator.comparing(Product::name, Comparator.nullsFirst(String::compareTo)));
```

Compor lambda com lambda:

```java
Function<String, String> trim = String::trim;
Function<String, String> lower = String::toLowerCase;
Function<String, String> normalized = trim.andThen(lower);   // trim, depois lower

Predicate<Product> cheap = p -> p.price().signum() < 100;
Predicate<Product> inStock = p -> p.stock() > 0;
Predicate<Product> available = cheap.and(inStock);            // os dois
```

### Operações terminais

```java
long count = products.stream().filter(p -> p.price() > 100).count();

Optional<Product> first = products.stream().findFirst();
boolean anyExpensive = products.stream().anyMatch(p -> p.price() > 1000);
boolean allPaid = orders.stream().allMatch(o -> o.status() == PAID);
boolean noneCancelled = orders.stream().noneMatch(o -> o.status() == CANCELLED);

products.stream().forEach(p -> p.notify());
```

`max`/`min` precisam de `Comparator` e devolvem `Optional` (stream pode ser
vazia):

```java
Optional<Product> priciest = products.stream()
        .max(Comparator.comparing(Product::price));
```

`findAny` vs `findFirst`:

- `findFirst` respeita a ordem do stream.
- `findAny` devolve qualquer um; em parallel stream, é mais rápido porque não
  garante ordem. Se a ordem não importa, `findAny`.

`forEachOrdered`:

```java
products.stream().parallel()
        .forEachOrdered(System.out::println);   // respeita a ordem mesmo em parallel
```

`forEach` em parallel não garante ordem; `forEachOrdered` sim. Se a ordem de
processamento importa, use `forEachOrdered` (ou não paralelize).

`peek` serve pra debug, não pra lógica. Se você usa `peek` pra modificar
estado, pare e reconsidere o pipeline.

## Lambdas e checked exceptions

Uma lambda **não pode lançar checked exception** dentro de `map`, `filter`,
`forEach` e cia. O `Function<T, R>` não declara `throws`, então o compilador
rejeita:

```java
// não compila: Files.readString lança IOException
List<String> contents = paths.stream()
        .map(path -> Files.readString(path))   // erro
        .toList();
```

Três saídas:

1. **Envolver em `RuntimeException`** (o comum):

```java
List<String> contents = paths.stream()
        .map(path -> {
            try {
                return Files.readString(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        })
        .toList();
```

2. **Extrair pra método que já trata**:

```java
String readSafely(Path path) {
    try {
        return Files.readString(path);
    } catch (IOException e) {
        throw new UncheckedIOException(e);
    }
}

List<String> contents = paths.stream().map(this::readSafely).toList();
```

3. **Envolver o checked num runtime próprio** no fim do pipeline. Muitas libs
   já fazem isso (`UncheckedIOException` existe na JDK).

O erro aparece em todo código que cruza stream com I/O. O padrão é: a lambda
fica pura, o tratamento fica no wrapper.

## `reduce`

Combina todos os elementos num único valor:

```java
BigDecimal total = prices.stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add);
```

O `reduce(identidade, acumulador)` começa da identidade e aplica o acumulador
elemento a elemento. Em parallel streams o acumulador precisa ser associativo.

## Collectors

O `Collectors` agrupa os resultados comuns:

```java
String joined = names.stream().collect(Collectors.joining(", "));
String wrapped = names.stream().collect(Collectors.joining(", ", "[", "]"));

Map<OrderStatus, List<Order>> byStatus = orders.stream()
        .collect(Collectors.groupingBy(Order::status));

Map<Boolean, List<Order>> paidPartition = orders.stream()
        .collect(Collectors.partitioningBy(o -> o.status() == PAID));

Map<String, BigDecimal> totalByCustomer = orders.stream()
        .collect(Collectors.toMap(
                Order::customerId,
                Order::total,
                BigDecimal::add));            // merge de chaves repetidas

Map<OrderStatus, Long> countByStatus = orders.stream()
        .collect(Collectors.groupingBy(Order::status, Collectors.counting()));

Set<String> uniqueNames = orders.stream()
        .map(Order::customerName)
        .collect(Collectors.toSet());

Map<OrderStatus, List<String>> namesByStatus = orders.stream()
        .collect(Collectors.groupingBy(
                Order::status,
                Collectors.mapping(Order::customerName, Collectors.toList())));

Map<OrderStatus, Optional<Order>> priciestByStatus = orders.stream()
        .collect(Collectors.groupingBy(
                Order::status,
                Collectors.maxBy(Comparator.comparing(Order::total))));
```

### groupingBy com downstream

O segundo argumento do `groupingBy` processa cada grupo:

```java
Map<OrderStatus, BigDecimal> totalByStatus = orders.stream()
        .collect(Collectors.groupingBy(
                Order::status,
                Collectors.mapping(
                        Order::total,
                        Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
```

`toMap` lança `IllegalStateException` se duas chaves se repetem sem merge
function. Se a chave pode repetir, passe o terceiro argumento. O
`Collectors.mapping` transforma cada elemento do grupo antes de coletar;
`maxBy`/`minBy` devolvem `Optional` porque o grupo pode estar vazio.

### Collector customizado com `collect`

Quando o `Collectors` pronto não resolve, o `collect` de três argumentos
(supplier, accumulator, combiner) monta qualquer coisa:

```java
StringBuilder joined = words.stream().collect(
        StringBuilder::new,                                  // supplier
        (builder, word) -> builder.append(word).append('|'), // accumulator
        StringBuilder::append);                              // combiner
```

O combiner existe porque o stream pode rodar paralelo e precisa fundir
resultados parciais.

## Primitivos: IntStream, LongStream, DoubleStream

```java
int sum = IntStream.range(1, 101).sum();              // 5050
long count = IntStream.rangeClosed(1, 100).count();

double avg = products.stream()
        .mapToDouble(Product::price)
        .average()
        .orElse(0);
```

- `range(a, b)` vai de `a` até `b - 1`; `rangeClosed` inclui `b`.
- `mapToDouble`, `mapToInt` abrem stream primitivo com `sum`, `average`,
  `min`, `max` prontos.
- Primitivo não aceita `null`; o stream primitivo é "denso".

## Optional a fundo

`Optional` é o portador de "pode não existir". A cadeia certa:

```java
String displayName = user
        .flatMap(User::profile)
        .map(Profile::name)
        .filter(name -> !name.isBlank())
        .orElse("anônimo");
```

Erros a evitar:

```java
// RUIM: volta pro null
String name = opt.orElse(null);

// RUIM: isPresent + get, o null-check disfarçado
if (opt.isPresent()) {
    return opt.get();
}
```

O ponto do `Optional` é **compor a ausência** no pipeline, não checá-la com
`if`. O `orElseThrow` lança quando o valor é obrigatório:

```java
Order order = orders.stream()
        .filter(o -> o.id() == id)
        .findFirst()
        .orElseThrow(() -> new OrderNotFoundException(id));
```

Compare com TS: `?.` e `??` cobrem a ausência no meio da expressão. O
`Optional` faz o mesmo, com a diferença de que o "vazio" é representado no
tipo, não com `null`/`undefined` solto.

## Stream.ofNullable e coleção pra stream

```java
Stream.ofNullable(config.get("host"))     // vazio se null
        .forEach(System.out::println);

List<String> names = List.of("a", "b");
Stream<String> stream = names.stream();
```

`ofNullable` vira `null` em stream vazio. Menos `if` no caminho.

## Parallel streams

`parallelStream()` divide o trabalho entre threads. O custo:

- Divisão e fusão têm overhead. Trabalho pequeno perde mais do que ganha.
- O acumulador do `reduce` e o combiner do `collect` precisam ser
  **associativos**. `String::concat` não é seguro pra essa combinação.
- Mutar estado compartilhado de fora do stream em paralelo é corrida:

```java
// perigoso
var counter = new AtomicInteger();
IntStream.range(0, 1000).parallel().forEach(i -> counter.incrementAndGet());
```

Regra: paralelize quando os dados são muitos, o processamento de cada
elemento é independente e o ganho foi medido (módulo 23). Caso contrário,
sequencial.

## Comparação com TypeScript

| Conceito | Java | TypeScript |
| -------- | ---- | ---------- |
| Função de tipo | `Function<T, R>` | `(t: T) => R` |
| Filter | `filter(Predicate)` | `filter(pred)` |
| Map | `map(Function)` | `map(fn)` |
| Achatar | `flatMap` | `flatMap` |
| Reduzir | `reduce` | `reduce` |
| Agrupar | `groupingBy` | `groupBy` (sem built-in) |
| Valor ausente | `Optional` | `?.` / `??` |

O stream do Java e o `Array.prototype` do TS compartilham a mesma origem. A
diferença principal: o stream é preguiçoso e single-use, o array é material.
O `groupBy` que você instala de lib no TS, no Java vem no `Collectors`.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| Lambdas e streams (base) | JDK 8 | Permanente |
| `Stream.ofNullable` | JDK 9 | Permanente |
| `Optional.stream()` | JDK 9 | Permanente |
| `Stream.toList()` | JDK 16 | Permanente |

## Exercícios

1. Escreva um método que recebe `List<Product>` e devolve `Map<String,
   BigDecimal>` com o total por categoria, usando `groupingBy` com
   downstream. Teste com lista vazia, categoria repetida e produto com preço
   negativo.
2. Dada `List<List<Integer>>`, achate com `flatMap` e devolva os pares
   distintos em ordem crescente. Teste com lista vazia e com duplicados.
3. Escreva `findFirstLongName(List<String>, int minLength)` que devolve
   `Optional<String>` com o primeiro nome de tamanho mínimo. Teste com lista
   vazia, sem nenhum que satisfaça e com `null` no meio (use `ofNullable`).
4. Usando `reduce`, escreva a concatenação de uma `List<String>` com `" - "`
   como separador. Depois compare com `Collectors.joining`. Por que o
   `joining` é preferível?

## Referências

- [Lesson: Aggregate Operations (Java Tutorials)](https://docs.oracle.com/javase/tutorial/collections/streams/) — pipelines, operações intermediárias e terminais com exemplos
- [Class Stream (Java API docs)](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/util/stream/Stream.html) — a referência completa das operações de stream
- [Class Optional (Java API docs)](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/util/Optional.html) — o contrato do `Optional` e seus métodos
- [Class Collectors (Java API docs)](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/util/stream/Collectors.html) — todos os collectors, de `groupingBy` a `toMap`
- [JEP 269 — Convenience Factory Methods for Collections](https://openjdk.org/jeps/269) — `List.of` e `Stream.toList` (JDK 9 e 16)

## Próximo módulo

**Tratamento de Exceções** — checked vs unchecked, try-with-resources e
exceções customizadas.

[→ 14 — Tratamento de Exceções](./14-excecoes.md)