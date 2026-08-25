# 08 — Arrays e Coleções

Arrays e o framework de coleções: `List`, `Set`, `Map`, as implementações que importam e as factories imutáveis do JDK

9. Foco no que você escolhe no dia a dia.

## Arrays

Arrays são fixos em tamanho e digitados:

```java
int[] numbers = new int[5];            // preenchido com 0
String[] names = {"ana", "bia", "caio"};
names[0]="ada";
int length = names.length;             // propriedade, não método
```

Diferenças do TS: o tamanho não muda depois de criado, e não existe
`push`/`pop`. `names[5] = "x"` lança `ArrayIndexOutOfBoundsException` em runtime.

Pra crescer, copie com `Arrays.copyOf`:

```java
int[] bigger = Arrays.copyOf(numbers, 10);
```

O `java.util.Arrays` traz utilidades: `sort`, `binarySearch`, `fill`,
`equals`, `toString`, `stream`.

Arrays aparecem em código de baixo nível (bytecode, buffers, parsing). No domínio de aplicação, prefira coleções.

## A hierarquia de coleções

```
Collection
├── List        (ordenada, permite duplicados, acesso por índice)
├── Set         (sem duplicados)
└── Queue/Deque (fila)

Map              (chave → valor, não é Collection)
```

```java
List<String> names = new ArrayList<>();
Set<String> uniqueTags = new HashSet<>();
Map<String, Integer> stockBySku = new HashMap<>();
```

Escolha por **comportamento**, não por hype:

| Interface | Quando usar                                     |
|-----------|-------------------------------------------------|
| `List`    | ordem importa, pode repetir, acessar por índice |
| `Set`     | garantir unicidade, ordem não importa           |
| `Map`     | lookup por chave                                |

## List

Implementações principais:

```java
List<String> arrayList = new ArrayList<>();   // acesso por índice rápido
List<String> linkedList = new LinkedList<>(); // inserção no meio "rápida"
```

`ArrayList` é o padrão. Array redimensionável por baixo. `LinkedList` parece vantagem por "inserir no meio rápido", mas
o overhead de cache geralmente enterra essa vantagem. Use `ArrayList` até ter motivo pra outra.

## Set

```java
Set<String> hashSet = new HashSet<>();         // ordem não garantida
Set<String> linkedHashSet = new LinkedHashSet<>(); // mantém ordem de inserção
Set<String> treeSet = new TreeSet<>();         // ordenado pelo comparador
```

`HashSet` é o padrão: unicidade rápida, ordem não garantida. `LinkedHashSet`
mantém a ordem em que você inseriu. `TreeSet` ordena na hora da inserção, com custo de comparação.

Unicidade usa `hashCode` + `equals`. Se você cria uma classe e a usa em `Set`
(ou como chave de `Map`), os dois precisam estar consistentes. Com `record`
isso vem pronto (módulo 10).

### `null` em coleções

Nem toda coleção aceita `null`:

| Coleção             | `null`                   |
|---------------------|--------------------------|
| `ArrayList`         | aceita                   |
| `HashSet`           | aceita (1 elemento)      |
| `TreeSet`           | **NPE** na comparação    |
| `ConcurrentHashMap` | **NPE** em chave e valor |
| `List.of`/`Map.of`  | **NPE** na criação       |

A pegadinha: `HashSet.add(null)` funciona e `TreeSet.add(null)` estoura. Se você troca uma implementação pela outra, o
comportamento muda. Se `null`
não é dado válido, rejeite na borda (validação ou `record`); se é, declare.

## Ordenação

`Collections.sort` e `Arrays.sort` ordenam pelo `compareTo` natural:

```java
List<String> names = new ArrayList<>(List.of("bia", "ana", "caio"));
Collections.

sort(names);                 // ["ana", "bia", "caio"]

Arrays.

sort(numbers);
```

Pra ordenar por outro critério, o `Comparator`:

```java
List<Product> products = new ArrayList<>(...);

    products.

sort(Comparator.comparing(Product::price));              // por preço
    products.

sort(Comparator.comparing(Product::price).

reversed());   // decrescente
    products.

sort(Comparator.comparing(Product::category)
        .

thenComparing(Product::price));                          // categoria, depois preço
```

O `Comparator.comparing` é o idioma moderno: recebe uma function (módulo 13) e encadeia critérios com `thenComparing`.
`sort` em `List` é método de instância desde o JDK 8; `Collections.sort` é o legado.

## `Collections` utilitárias

Além do `sort`, o `Collections` tem operações que aparecem em código de produção:

```java
List<String> view = Collections.unmodifiableList(inner);  // "congela" a lista
Collections.

reverse(names);                                // inverte no lugar
Collections.

shuffle(cards);                                // embaralha
```

`unmodifiableList` devolve uma **view** imutável sobre a lista original:
quem recebe a view não muta, mas se a original mudar, a view muda junto.
`List.of`/`copyOf` são cópias imutáveis de verdade. Prefira os factories (módulo 08) quando der; o `unmodifiable*` entra
pra embrulhar lista que já existe e muta.

## Map

```java
Map<String, Integer> stock = new HashMap<>();

stock.

put("sku-1",10);

Integer qty = stock.get("sku-1");      // 10
Integer missing = stock.get("sku-x"); // null

stock.

getOrDefault("sku-x",0);        // 0
stock.

computeIfAbsent("sku-y",sku ->

loadFromDb(sku));
```

`HashMap` é o padrão. `TreeMap` mantém chaves ordenadas. `LinkedHashMap`
mantém ordem de inserção (útil pra cache LRU simples).

Armadilha do `get`: retorna `null` pra chave ausente, mas também pro valor
`null` armazenado. `Map.of` nem aceita `null` em chave nem valor, o que mata a ambiguidade. Em `HashMap`, se `null` é um
valor legítimo, distinga ausência com `containsKey` ou use `Optional`.

## Queue e Deque

Aparecem na hierarquia mas quase nunca nos exemplos. A implementação que você usa é o `ArrayDeque`, que serve de fila e
de pilha:

```java
Deque<String> queue = new ArrayDeque<>();
queue.

offer("primeiro");      // entra no fim
queue.

offer("segundo");

String next = queue.poll();   // "primeiro", sai da frente (FIFO)

Deque<String> stack = new ArrayDeque<>();
stack.

push("a");              // entra no topo
stack.

push("b");

String top = stack.pop();     // "b" (LIFO)
```

- `offer`/`poll` são a fila (FIFO). `push`/`pop` são a pilha (LIFO).
- `ArrayDeque` não aceita `null` (lança NPE) e não tem limite de capacidade indexada.
- `Queue` bloqueante (produtor-consumidor) é o `BlockingQueue`, que mora no módulo 16 de concorrência.

Pra "processar na ordem que chegou", fila; pra "desfazer última ação", pilha. O `ArrayDeque` cobre os dois.

## Factories imutáveis (JDK 9)

```java
List<String> tags = List.of("java", "streams");
Set<String> statuses = Set.of("CREATED", "PAID");
Map<String, String> config = Map.of("host", "localhost", "port", "8080");
```

Diferenças pro `new ArrayList`:

- Imutável. `tags.add("x")` lança `UnsupportedOperationException`.
- Null não entra. `List.of(null)` lança NPE na hora.
- Implementação compacta, menos memória.

`Map.of` aceita até 10 pares. Acima disso:

```java
Map<String, String> big = Map.ofEntries(
    Map.entry("a", "1"),
    Map.entry("b", "2")
);
```

Precisa mutar? Crie a imutável e envolva:

```java
List<String> mutable = new ArrayList<>(List.of("java", "streams"));
```

Compare com TS: `Object.freeze` congela de forma rasa e opcional. No Java a imutabilidade é o default das factories, e
mutabilidade é a escolha explícita.

## Copiando coleções

```java
List<String> copy = List.copyOf(original);   // cópia imutável (JDK 10)
List<String> shallow = new ArrayList<>(original);
```

`List.copyOf` não copia os elementos em si, só a coleção. Se os elementos são mutáveis, a "imutabilidade" é rasa. O
mesmo vale pra `Set.copyOf` e
`Map.copyOf`.

## Stream pra coleção e o inverso

```java
List<String> result = source.stream()
    .filter(s -> s.length() > 3)
    .toList();                    // JDK 16, imutável

List<String> collect = source.stream()
    .filter(s -> s.length() > 3)
    .collect(Collectors.toList());   // mutável (na implementação padrão)
```

`toList()` (JDK 16) retorna lista imutável. `Collectors.toList()` não garante nada. Se o consumidor precisa mutar, use
`toCollection(ArrayList::new)`.

## Comparação com TypeScript

| Conceito             | Java                | TypeScript                   |
|----------------------|---------------------|------------------------------|
| Array mutável        | `ArrayList`         | `Array`                      |
| Tupla/lista imutável | `List.of`           | `as const` / `Object.freeze` |
| Set                  | `HashSet`           | `Set`                        |
| Map                  | `HashMap`           | `Map`                        |
| Sem null             | `List.of`, `Map.of` | `strictNullChecks`           |

O `Map` do TS e o `HashMap` do Java cobrem o mesmo uso. A diferença de comportamento aparece no `get`: TS devolve
`undefined`, Java devolve `null`.

## O que mudou entre versões

| Feature                       | Versão | Situação   |
|-------------------------------|--------|------------|
| `List.of`, `Set.of`, `Map.of` | JDK 9  | Permanente |
| `List.copyOf`                 | JDK 10 | Permanente |
| `Stream.toList()`             | JDK 16 | Permanente |
| Streams (base)                | JDK 8  | Permanente |

## Exercícios

1. Escreva `deduplicate(List<String> items)` que devolve uma lista sem repetidos mantendo a ordem de inserção. Teste com
   lista vazia, tudo repetido, e itens com `null` no meio.

```java
void main() {
  IO.println(Deduplicate.execute(List.of("a", "b", "c", "a", "b", "c")));
  IO.println(Deduplicate.execute(Arrays.asList("a", "b", null, "a", null, "c")));
  IO.println(Deduplicate.execute(List.of("a", "a", "a")));
  IO.println(Deduplicate.execute(List.of()));
}

static class Deduplicate {
  private Deduplicate() {
  }

  public static List<String> execute(List<String> items) {
    return items.stream().distinct().toList();
  }
}
```

2. Escreva `invert(Map<String, Integer> input)` que troca chave e valor. Teste com valores repetidos (o que acontece?
   decidir a política) e com valor `null`.

```java
void main() {
  IO.println(Invert.execute(Map.of("a", "b", "c", "d")));
  IO.println(Invert.execute(Map.of("b", "a", "d", "c")));

  var map = new HashMap<String, String>();
  map.put("a", "b");
  map.put("c", null);

  IO.println(Invert.execute(map));
  IO.println(Invert.execute(Map.of()));
}

static class Invert {
  private Invert() {
  }

  public static Map<String, String> execute(Map<String, String> items) {
    return items.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
  }
}
```

3. Escreva um método que recebe `List<Integer>` e devolve um `Map<Integer,
   Long>` com a contagem de cada número, usando streams. Teste com lista vazia e com números repetidos.

```java
void main() {
  IO.println(Count.execute(List.of(1, 1, 1, 2, 5)));
  IO.println(Count.execute(List.of(1, 2, 3)));
  IO.println(Count.execute(List.of()));
}

static class Count {
  private Count() {
  }

  public static Map<Integer, Long> execute(List<Integer> items) {
    return items.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
  }
} 
```

4. Explique em uma linha a diferença entre `List.copyOf(list)` e
   `new ArrayList<>(list)` em relação a mutabilidade. Demonstre com um teste que tenta `add` nas duas.

```java
void main() {
  var original = List.of("a", "b", "c");

  IO.println(Copy.execute(original));
}

static class Copy {
  private Copy() {
  }

  public static String execute(List<String> items) {
    var immutable = List.copyOf(items);
    var mutable = new ArrayList<>(items);

    try {
      immutable.add("d");
    } catch (UnsupportedOperationException e) {
      IO.println("List.copyOf: não permite add");
    }

    try {
      mutable.add("d");
      IO.println("ArrayList: permite add");
    } catch (UnsupportedOperationException e) {
      IO.println("ArrayList: não permite add");
    }

    return "imutável vs mutável";
  }
}
```

## Referências

- [Lesson: Collections (Java Tutorials)](https://docs.oracle.com/javase/tutorial/collections/) — a hierarquia
  `Collection`/`Map` e as implementações
- [The Collections Framework Overview](https://docs.oracle.com/en/java/javase/26/core/collections-framework.html) —
  visão geral oficial do framework de coleções
- [List.of / Map.of (Java API docs)](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/util/List.html) —
  as factories imutáveis e seus contratos
- [JEP 269 — Convenience Factory Methods for Collections](https://openjdk.org/jeps/269) — o JEP do `List.of` e companhia
  (JDK 9)

## Próximo módulo

**OOP — Classes, Herança e Interfaces** — encapsulamento, herança, polimorfismo, classes abstratas e interfaces no Java.

[→ 09 — OOP — Classes, Herança e Interfaces](./09-oop.md)