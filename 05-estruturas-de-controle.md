# 05 — Estruturas de Controle

Controle de fluxo no Java: condicionais, loops, o `switch` moderno e o que mudou entre versões.

## Condicionais

```java
if(status ==OrderStatus.PAID){

sendReceipt(order);
}else if(status ==OrderStatus.CANCELLED){

logCancellation(order);
}else{

queueForReview(order);
}
```

Regras do Java (iguais ao TS):

- `if` precisa de `boolean`. Não existe truthiness: `if (count)` não compila se `count` é `int`. O TS aceita
  `if (count)` com coerção; o Java exige
  `if (count > 0)`.
- `switch` também não usa truthiness: só comparação exata.

## Loops

Os três clássicos:

```java
// contador
for(int i = 0;
i< 10;i++){

process(i);
}

// for-each (qualquer Iterable)
    for(
Order order :orders){
total +=order.

total();
}

// while
    while(queue.

hasNext()){
Item item = queue.next();
}

// do-while: roda pelo menos uma vez
int attempts = 0;
do{
attempts++;

retry(attempts);
}while(attempts<MAX_RETRIES);
```

O `for-each` funciona com arrays e com qualquer `Iterable` (todas as coleções). Equivale ao `for...of` do TS. Não há
`for...in` (índice) no Java — se você precisa do índice, use `for` clássico ou `IntStream.range`.

Comparação com TS:

| Tipo de loop | Java                      | TypeScript                  |
|--------------|---------------------------|-----------------------------|
| Contador     | `for (int i = 0; ...)`    | `for (let i = 0; ...)`      |
| Por valor    | `for (Item item : items)` | `for (const item of items)` |
| Por índice   | não tem direto            | `for (const i in items)`    |
| Condicional  | `while`                   | `while`                     |

## `break` e `continue`

```java
for(int i = 0;
i< 10;i++){
    if(i %2==0)continue;   // pula pares
    if(i ==7)break;          // para no 7
    }
```

`continue` passa pra próxima iteração; `break` sai do loop. Funcionam igual ao TS. Existe também `break`/`continue` com
rótulo (labeled break) pra sair de loop aninhado:

```java
outer:
    for(
int i = 0;
i< 10;i++){
    for(
int j = 0;
j< 10;j++){
    if(matrix[i][j]==target){
    break outer;   // sai dos dois loops de uma vez
        }
            }
            }
```

Sem o rótulo, o `break` sairia só do loop interno. O rótulo resolve, mas é raro e fácil de errar; prefira extrair o loop
pra um método e usar `return`, ou reformular com stream.

### Modificar coleção durante o `for-each`

Iterar e modificar a mesma coleção lança `ConcurrentModificationException`:

```java
// RUIM: remove dentro do for-each → ConcurrentModificationException
for(Order order :orders){
    if(order.

status() ==CANCELLED){
    orders.

remove(order);
    }
        }
```

O `for-each` guarda o iterador; mexer na lista invalida a iteração. Caminhos:

```java
// 1. removeIf (o moderno)
orders.removeIf(order ->order.

status() ==CANCELLED);

// 2. coleção imutável na iteração + nova lista pro resultado
List<Order> active = orders.stream()
    .filter(o -> o.status() != CANCELLED)
    .toList();

// 3. Iterator explícito (legado)
Iterator<Order> it = orders.iterator();
while(it.

hasNext()){
    if(it.

next().

status() ==CANCELLED){
    it.

remove();
    }
        }
```

`removeIf` e stream cobrem a maioria. O erro é o mesmo em TS com `for...of` +
`splice` no meio do loop.

## Switch expression (JDK 14)

O `switch` virou **expressão** e ganhou arrow syntax. Sem fallthrough, sem
`break`:

```java
String category = switch (priority) {
  case 1, 2 -> "baixa";
  case 3, 4 -> "média";
  case 5 -> "alta";
  default -> "desconhecida";
};
```

Pontos:

- `case 1, 2 ->` lista vários valores no mesmo case.
- Sem `break`: cada case é independente, não cai no próximo.
- É uma expressão: o valor do case vira o valor da variável.
- `yield` devolve valor de um bloco maior:

```java
String category = switch (priority) {
  case 1, 2 -> "baixa";
  case 3, 4 -> {
    int rounded = Math.min(priority, 4);
    yield "média (" + rounded + ")";
  }
  case 5 -> "alta";
  default -> "desconhecida";
};
```

O `switch` tradicional (com `case:` e `break`) continua existindo e compilando, mas o moderno é o padrão pra código
novo. Se você esbarra no antigo, o perigo é o **fallthrough**: sem `break`, o case "cai" pro próximo e executa junto.

```java
// RUIM: "pao" e "banana" imprimem as duas frases
switch(item){
    case"pao":
    System.out.

println("comida");
    case"banana":
        System.out.

println("fruta");
}
```

O `case:` sem `break` executa o bloco e continua no próximo case. É o bug silencioso que o arrow syntax (`case ... ->`)
elimina de vez. Código novo não escreve isso; código legado pode ter.

## Switch com `String`, `enum` e `null`

Desde o JDK 7 o `switch` aceita `String`; desde sempre, `enum` e inteiros. No JDK 21, o `switch` aceita `null`:

```java
String result = switch (name) {
  case "java" -> "lts";
  case null -> "sem nome";
  default -> "outra";
};
```

Antes do 21, `null` no selector estourava NPE. Agora `case null` trata.

## Pattern matching no switch (JDK 21)

O `switch` aceita **patterns** como case (módulo 11 a fundo). O essencial:

```java
String describe(Object value) {
  return switch (value) {
    case String s -> "string de " + s.length() + " chars";
    case Integer i when i > 100 -> "inteiro grande";
    case Integer i -> "inteiro " + i;
    case null -> "nulo";
    default -> "outro tipo";
  };
}
```

`when` é a guarda: condição extra além do pattern. O compilador exige que o switch seja **exaustivo** (cubra todos os
casos possíveis), especialmente com sealed types (módulo 10).

## Loop ou stream?

Os dois resolvem o mesmo, mas cada um brilha num caso:

| Situação                                     | Escolha                             |
|----------------------------------------------|-------------------------------------|
| Transformar/filtrar/agrupar uma coleção      | stream (módulo 13)                  |
| Loop com efeito colateral e ordem exata      | `for` clássico                      |
| Preciso do índice                            | `for` clássico ou `IntStream.range` |
| Lógica de parada complexa (várias condições) | `while`/`for`                       |
| Somar, contar, agrupar, achar                | stream                              |

Regra de bolso: stream quando o código é "pegar esses, transformar aquilo, juntar"; loop quando é "passo a passo com
efeito colateral e controle fino". O stream é mais curto e sem estado mutável; o loop é mais explícito onde a lógica de
parada é intrincada.

## O que mudou entre versões

| Feature                                           | Versão           | Situação                                |
|---------------------------------------------------|------------------|-----------------------------------------|
| `switch` com `String`                             | JDK 7            | Permanente                              |
| Switch expression + arrow                         | JDK 14           | Permanente                              |
| Pattern matching no switch                        | JDK 21           | Permanente                              |
| `case null`                                       | JDK 21           | Permanente                              |
| `switch` com `long`, `float`, `double`, `boolean` | JDK 23 (JEP 455) | Preview — 3ª rodada no JDK 25 (JEP 507) |

Os tipos primitivos no switch (`long`, `float`, `double`, `boolean`) seguem **preview** no Java 25: rodam só com
`--enable-preview` e podem mudar antes de virar permanente. Até lá, use os wrappers (`Long`, `Float`, `Double`,
`Boolean`) ou `if/else`.

## Comparação rápida com TypeScript

| Conceito       | Java                       | TypeScript                        |
|----------------|----------------------------|-----------------------------------|
| Condicional    | `if` com `boolean` estrito | `if` com truthiness               |
| Multi-branch   | `switch` expression        | `switch` (statement) ou `if/else` |
| Guarda de case | `when`                     | ternário ou `if` dentro do case   |
| Loop por valor | `for-each`                 | `for...of`                        |
| Sem truthiness | `if (count)` não compila   | `if (count)` funciona             |

## Exercícios

1. Escreva `classify(int priority)` que retorna a categoria usando `switch`
   expression com `yield`. Faixas: 1–3 → `"low"`, 4–7 → `"medium"`, 8–10 →
   `"high"`, qualquer outro valor → `"invalid"`. Confira os limites: 1, 3, 4, 7, 8, 10, e fora do range: 0, -5,
   `Integer.MAX_VALUE`.

```java
void main() {
  IO.println(Classify.execute(1));
  IO.println(Classify.execute(3));
  IO.println(Classify.execute(4));
  IO.println(Classify.execute(7));
  IO.println(Classify.execute(8));
  IO.println(Classify.execute(10));
  IO.println(Classify.execute(0));
  IO.println(Classify.execute(-5));
}

class Classify {
  private Classify() {
  }

  public static String execute(int priority) {
    return switch (priority) {
      case 1, 2, 3 -> "low";
      case 4, 5, 6, 7 -> "medium";
      case 8, 9, 10 -> "high";
      default -> "invalid";
    };
  }
}
```

2. Escreva `sumEven(int limit)` que soma os pares de 0 até `limit` (inclusive)
   usando `continue` no loop. Esperado: `limit = 10` → `30` (2+4+6+8+10);
   `limit = 0` → `0`; `limit` negativo → `0`.

```java
void main() {
  IO.println(SumEven.execute(10));
  IO.println(SumEven.execute(0));
  IO.println(SumEven.execute(-10));
}

public class SumEven {
  private SumEven() {
  }

  public static int execute(int limit) {
    var sum = 0;

    for (var i = 0; i <= limit; i++) {
      if (i % 2 != 0) {
        continue;
      }
      sum += i;
    }

    return sum;
  }
}
```

3. Refaça o exercício 2 com `IntStream.rangeClosed(0, limit)` e compare as duas versões. Aponte qual lê melhor e o que
   muda no comportamento quando
   `limit` cresce (a soma de pares passa de `int` com limite alto — o que acontece em cada versão?).

```java
import java.util.stream.IntStream;

void main() {
  IO.println(SumEvenWithStream.execute(10));
  IO.println(SumEvenWithStream.execute(0));
  IO.println(SumEvenWithStream.execute(-10));
}

public class SumEvenWithStream {
  private SumEvenWithStream() {
  }

  public static int execute(int limit) {
    return IntStream.rangeClosed(0, limit)
        .filter(i -> i % 2 == 0)
        .sum();
  }
} 
```

4. Escreva `describe(Object value)` com `switch` pattern matching que retorna:
   `String` → `"string: <length>"`; `Integer` maior que 100 → `"big number"`;
   `Integer` ≤ 100 → `"small number"`; `null` → `"null"`; qualquer outro tipo → `"other: <nome da classe>"`. Teste os
   cinco casos, incluindo `null` e um tipo inesperado (ex.: `Double`).

```java
void main() {
  IO.println(DescribeValue.execute("Java"));
  IO.println(DescribeValue.execute(101));
  IO.println(DescribeValue.execute(100));
  IO.println(DescribeValue.execute(null));
  IO.println(DescribeValue.execute(10.5));
}

public class DescribeValue {
  private DescribeValue() {
  }

  public static String execute(Object value) {
    return switch (value) {
      case null -> "null";
      case String s -> "string: " + s.length();
      case Integer i when i > 100 -> "big number";
      case Integer i -> "small number";
      default -> "other: " + value.getClass().getName();
    };
  }
}
```

## Referências

- [The switch Statement (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/nutsandbolts/switch.html) — o
  switch clássico, fallthrough e `default`
- [Switch Expressions and Statements (Java Language Updates)](https://docs.oracle.com/en/java/javase/23/language/switch-expressions-and-statements.html) —
  arrow syntax, `yield` e exaustividade
- [JEP 361 — Switch Expressions](https://openjdk.org/jeps/361) — o JEP que tornou o switch expressão
- [The for Statement (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/nutsandbolts/for.html) — `for`
  clássico e o enhanced `for`
- [The while and do-while Statements (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/nutsandbolts/while.html) —
  os loops condicionais

## Próximo módulo

**Métodos e Funções** — assinatura, sobrecarga, varargs, passagem por valor, métodos estáticos e o que difere do TS.

[→ 06 — Métodos e Funções](./06-metodos-e-funcoes.md)