# 12 — Generics

Generics no Java: type parameters, bounded types, wildcards, erasure e como o
compilador te protege (e onde não protege). Foco no que você escreve e no que
lê no dia a dia.

## Type parameters

```java
public class Box<T> {
    private final T value;

    public Box(T value) {
        this.value = value;
    }

    public T value() {
        return value;
    }
}
```

```java
Box<String> stringBox = new Box<>("conteudo");   // diamond <> infere
Box<Integer> intBox = new Box<>(42);
```

- `T` é o type parameter. O compilador substitui por um tipo concreto em cada
  uso e valida a compatibilidade.
- O `<>` (diamond, JDK 7) infere o tipo: `new Box<>("x")` já sabe que é
  `Box<String>`.
- Sem generic, `Box` aceitaria qualquer objeto e o cast ficaria pra quem usa.

## Bounded types

Restrinja o que pode entrar no parâmetro:

```java
public <T extends Number> double sumAll(List<T> numbers) {
    return numbers.stream().mapToDouble(Number::doubleValue).sum();
}
```

`T extends Number` diz: `T` é `Number` ou um subtipo (Integer, Double,
BigDecimal...). Dentro do método, você chama métodos de `Number` sem cast.

Regra: `extends` vale tanto pra classe quanto pra interface. `T extends
Comparable<T>` é o formato comum.

## Wildcards

`?` é o "tipo desconhecido". Três usos:

```java
// unbounded: qualquer tipo
int sizeOf(List<?> list) {
    return list.size();
}

// upper bounded: Number ou subtipo
double sum(List<? extends Number> numbers) {
    return numbers.stream().mapToDouble(Number::doubleValue).sum();
}

// lower bounded: Integer ou supertipo
void addIntegers(List<? super Integer> list) {
    list.add(42);
}
```

Por que wildcard em vez de `List<T>`? Pra ler qualquer lista sem saber o tipo,
`List<?>` resolve. Pra ler `Number` de qualquer lista numérica, `List<? extends
Number>` resolve.

### O problema do `? extends`

```java
void example(List<? extends Number> numbers) {
    // pode ler
    Number n = numbers.get(0);

    // não pode escrever
    numbers.add(42);   // erro: o tipo real pode ser List<Float>
}
```

Com `? extends`, você só lê. O compilador não sabe qual subtipo concreto é; se
deixasse adicionar, um `Float` poderia cair numa `List<Integer>`. Isso é o PECS:
**P**roducer **E**xtends, **C**onsumer **S**uper.

## Erasure (type erasure)

Generics em Java é checagem em tempo de compilação. Em runtime, o tipo genérico
desaparece:

```java
List<String> strings = new ArrayList<>();
List<Integer> integers = new ArrayList<>();

strings.getClass() == integers.getClass();   // true
```

Os dois viram `ArrayList` puro. Consequências práticas:

- `new T()` não existe: não dá pra instanciar o type parameter.
- `instanceof List<String>` não compila: use `instanceof List<?>`.
- Cast de genérico lança warnings: `(List<String>) obj` pode falhar em
  runtime se o objeto for `List<Integer>`.
- Arrays e generics não se misturam: `new T[10]` e `new List<String>[10]` são
  ilegais. Use `List<T>` no lugar.

```java
// ilegal
T instance = new T();

// também ilegal
if (obj instanceof List<String>) { }

// legal
if (obj instanceof List<?>) { }
```

## Generics em records, enums e métodos

```java
public record Pair<K, V>(K key, V value) {}
```

```java
Pair<String, Integer> pair = new Pair<>("sku", 42);
```

Método genérico independente da classe:

```java
public static <T> List<T> reversed(List<T> input) {
    var result = new ArrayList<T>();
    for (int i = input.size() - 1; i >= 0; i--) {
        result.add(input.get(i));
    }
    return result;
}
```

`enum` não tem type parameter (constante não tem tipo genérico).

## Inferência

O compilador infere tipo genérico em chamada, então o explícito vira
desnecessário na maioria dos casos:

```java
List<String> names = new ArrayList<>();          // diamond inferido
Map<String, List<Integer>> map = new HashMap<>(); // idem

// inferência na chamada
var result = reversed(List.of("a", "b"));
```

## Comparação com TypeScript

| Conceito | Java | TypeScript |
| -------- | ---- | ---------- |
| Type parameter | `<T>` | `<T>` |
| Bounded | `T extends Number` | `T extends Number` |
| Wildcard | `? extends` / `? super` | `T extends` / `T super` |
| Erasure | sim, em runtime | tipos apagam em runtime (transpile) |
| Runtime checagem | não existe | `typeof`, checagem em runtime |

As duas linguagens apagam tipos em runtime. A diferença: no TS você ainda pode
checar com `typeof`/`instanceof` porque o valor existe; no Java, o tipo
genérico desaparece e não há como recuperar.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| Generics (básico) | JDK 5 | Permanente |
| Diamond `<>` | JDK 7 | Permanente |
| Records genéricos | JDK 16 | Permanente |
| `var` + inferência | JDK 10 | Permanente |

## Exercícios

1. Escreva `swap(Pair<K, V>)` que troca os componentes de um `Pair<K, V>` e
   devolve `Pair<V, K>`. Teste com tipos diferentes (`Pair<String, Integer>`).
2. Escreva um método genérico `max(List<T>)` que devolve o maior elemento,
   com `T extends Comparable<T>`. Teste com lista vazia (o que acontece?),
   com `null` e com ordem invertida.
3. Explique, em código, por que `add` falha numa `List<? extends Number>`.
   Depois faça funcionar com `List<? super Integer>`. Teste os dois.
4. Escreva um método que recebe `List<?>` e devolve uma `List<String>` com o
   `toString` de cada elemento. Teste com lista vazia, com `null` no meio e
   com tipos mistos.

## Referências

- [Lesson: Generics (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/generics/) — type parameters, bounded types e wildcards em detalhe
- [The Java Language Specification — Chapter 4: Types](https://docs.oracle.com/javase/specs/jls/se24/html/jls-4.html) — type erasure e os limites do sistema de tipos
- [Generic Types (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/generics/types.html) — a definição formal de generic types e type parameters
- [Wildcards (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/generics/wildcards.html) — `?`, `? extends`, `? super` e o PECS

## Próximo módulo

**Lambdas e Streams** — interfaces funcionais, lambdas, method references e o
pipeline de streams com collectors.

[→ 13 — Lambdas e Streams](./13-lambdas-e-streams.md)