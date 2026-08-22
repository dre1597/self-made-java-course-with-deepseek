# 03 — Tipos de Dados e Variáveis

Os tipos do Java, os primitivos, `var`, casting, null e o que mudou entre
versões. Foco no que difere do TypeScript e nas armadilhas.

## Primitivos

O Java tem **8 tipos primitivos**. São valores diretos, sem objeto envolvido:

| Tipo     | Tamanho | Faixa                       |
| -------- | ------- | --------------------------- |
| `byte`   | 8 bits  | -128 a 127                  |
| `short`  | 16 bits | -32.768 a 32.767            |
| `int`    | 32 bits | ~-2.1bi a ~2.1bi            |
| `long`   | 64 bits | ~-9.2e18 a ~9.2e18          |
| `float`  | 32 bits | ponto flutuante (~7 dígitos)|
| `double` | 64 bits | ponto flutuante (~15 dígitos)|
| `char`   | 16 bits | caractere Unicode (UTF-16)  |
| `boolean`| —       | `true` / `false`            |

Regras práticas:

- Inteiro padrão é `int`; decimal padrão é `double`. Se o valor não cabe em
  `int`, use `long`.
- `float` raramente faz sentido. Precisão financeira nem `double` resolve —
  é caso de `BigDecimal` (módulo 07).
- `char` é um caractere Unicode de 16 bits, não um "byte de texto". Texto de
  verdade vive em `String`.

Sufixos de literal:

```java
long population = 8_200_000_000L;      // L para long
float pi = 3.14f;                      // f para float
double avogadro = 6.02e23;             // notação científica
int million = 1_000_000;               // underscore em literal (JDK 7+)
```

O `_` em literal é só legibilidade, o compilador ignora. TS não tem isso —
você escreve `1_000_000` como número e pronto, sem sufixo de tipo.

## Wrappers

Cada primitivo tem uma classe wrapper para quando você precisa de objeto
(coleções, `Optional`, `null`):

| Primitivo | Wrapper     |
| --------- | ----------- |
| `int`     | `Integer`   |
| `long`    | `Long`      |
| `double`  | `Double`    |
| `boolean` | `Boolean`   |
| `char`    | `Character` |
| `byte`    | `Byte`      |
| `short`   | `Short`     |
| `float`   | `Float`     |

Conversão automática entre os dois é o **autoboxing/unboxing**:

```java
Integer count = 3;         // int → Integer (autoboxing)
int value = count;         // Integer → int (unboxing)
```

Armadilha clássica: unboxing de `null` estoura `NullPointerException`.

```java
Integer count = null;
int value = count;         // NPE em runtime
```

`Integer` também tem cache de -128 a 127: `Integer a = 127; Integer b = 127;
a == b` é `true`; com 128, é `false`. Use `.equals()` pra comparar wrapper,
nunca `==` (com exceção de `enum`, que `==` é correto).

Compare com TS: TS não tem primitivo "de verdade" separado de objeto —
`number`, `string`, `boolean` são um tipo só e o runtime trata como valor. No
Java, a distinção primitivo/wrapper aparece em coleções, `Optional`, `null` e
performance.

## `var` — inferência local (JDK 10)

```java
var orderList = new ArrayList<Order>();          // ArrayList<Order>
var total = order.total();                        // BigDecimal
var numbers = List.of(1, 2, 3);                   // List<Integer>
```

Regras:

- Só pra **variável local** (dentro de método, loop, etc.). Campo de classe,
  parâmetro e retorno de método não usam `var`.
- O tipo é inferido na compilação e **fixo**. Não é `dynamic`/`any`.
- Use quando o tipo é óbvio do lado direito. Evite quando ele esconde o tipo:

```java
var result = service.process(input);   // ??? o que é result?
```

No TS, `let`/`const` já inferem e você pode anotar quando quer. No Java, `var`
nunca tem anotação. Se você precisa do tipo explícito, declare o tipo.

## Casting e conversão

Casting implícito (widen): vai de um tipo menor pra maior sem perda.

```java
int items = 5;
long totalItems = items;          // int → long, ok
double ratio = totalItems;        // long → double, ok
```

Casting explícito (narrow) força pra um tipo menor e pode perder dados:

```java
long bigNumber = 1_000_000_000L;
int truncated = (int) bigNumber;  // ok só se couber; senão, valor truncado
```

```java
double raw = 9.99;
int cents = (int) raw;            // 9 (o 0.99 some)
```

Narrowing sem checagem não lança erro; ele **trunca silenciosamente**. Se a
precisão importa, valide antes de converter. No TS, `Math.trunc()`/`parseInt`
fazem o mesmo corte explícito; o Java usa o cast.

## `null`

`null` é o valor de "nenhuma referência". Só tipos de referência aceitam —
primitivo não tem null (o `int` sempre tem valor).

```java
Product product = null;            // ok, referência nula
int count = null;                  // não compila
```

O `null` é o erro de um bilhão de dólares. As features modernas atacam isso
por dois lados:

- `Optional` (JDK 8) para "valor pode não existir" (módulo 13).
- O `null` é tratado na linguagem com `Objects.requireNonNull`, `Optional`, e
  checagem de null no pattern matching (módulo 11).

Regra prática: método que retorna "não achei" devolve `Optional`, não `null`.
Parâmetro obrigatório valida com `Objects.requireNonNull` ou o null nunca
entra. `List.of` e `Map.of` rejeitam `null` na criação.

Compare com TS: `null` e `undefined` coexistem e o `strictNullChecks` ajuda.
No Java moderno, a cultura é não usar `null` como sinal de ausência; usar
`Optional`.

## `BigDecimal` — o "primitivo" de dinheiro

`double` não representa decimal exato: `0.1 + 0.2` é `0.30000000000000004`.
Pra dinheiro, precisão, juros e qualquer valor que não pode perder um
centavo, use `BigDecimal`.

```java
BigDecimal a = new BigDecimal("0.10");
BigDecimal b = new BigDecimal("0.20");
BigDecimal sum = a.add(b);          // 0.30 exato
```

Regras:

- Construa da `String`: `new BigDecimal("0.10")`. Do `double`
  (`new BigDecimal(0.1)`) vem o erro de novo, porque o `double` 0.1 já é
  inexato.
- `add`, `subtract`, `multiply` retornam um **novo** `BigDecimal`; o objeto é
  imutável.
- `divide` exige precisão: `a.divide(b, 2, RoundingMode.HALF_UP)`.
  Divisão exata que não termina lança `ArithmeticException` sem os
  argumentos de escala.
- Compare com `compareTo`, não com `equals`. `equals` exige mesma escala
  (`1.0` ≠ `1.00`); `compareTo` compara o valor.
- Nunca use `==` nem `>`/`<` com `BigDecimal` (são objetos).

```java
if (total.compareTo(BigDecimal.ZERO) > 0) {   // certo
}

if (total > BigDecimal.ZERO) {                // não compila
}
```

Constantes prontas: `BigDecimal.ZERO`, `ONE`, `TEN`. O `BigDecimal` é o tipo
que você usa pra dinheiro em código Java de verdade, do JDBC (módulo 17) ao
Spring.

## O que mudou entre versões

| Feature          | Versão | Situação                        |
| ---------------- | ------ | ------------------------------- |
| Literal com `_`  | JDK 7  | Permanente                      |
| `var`            | JDK 10 | Permanente                      |
| `List.of` (sem null) | JDK 9 | Permanente                   |
| Value Objects (`record`) | JDK 16 | Permanente                |
| Tipos primitivos em patterns | JDK 23 | Preview (3ª no 25)       |

## Comparação rápida com outras linguagens

| Conceito      | Java         | TypeScript    | Kotlin       |
| ------------- | ------------ | ------------- | ------------ |
| Tipos básicos | 8 primitivos | number/string/boolean | mesmos do Java + `Nothing` |
| Inferência    | `var` (local) | `let`/`const` + anotação | `val`/`var` |
| Null          | `Optional`, null-safe | `undefined`/`null` + strictNullChecks | `?` e elvis `?:` |
| Valor ausente | `Optional`   | `undefined`   | `null` com `?` |

## Exercícios

1. Escreva um método que recebe `Integer` e retorna o valor dobrado. Teste
   com `null` (deve lançar ou tratar), com valor negativo e com `Integer.MAX_VALUE`
   (estouro de `int`). Decida o que o método faz em cada caso.
2. Explique, sem rodar, o que imprime cada linha. Depois confirme no `jshell`:

   ```java
   int a = 7 / 2;          // ?
   double b = 7 / 2;       // ?
   double c = 7 / 2.0;     // ?
   Integer x = 128; Integer y = 128;
   boolean eq = x == y;    // ?
   Integer p = 127; Integer q = 127;
   boolean eq2 = p == q;   // ?
   ```

3. Crie um método que converte um `long` pra `int` com segurança: se não
   couber, lança `ArithmeticException` com mensagem clara. Teste o limite
   (`Integer.MAX_VALUE`, `Integer.MIN_VALUE`, um acima e um abaixo).
4. Escreva um método `formatAmount(double value)` que retorna o valor com
   duas casas usando `String.format`. Depois refaça com `BigDecimal` e diga a
   diferença de comportamento com `0.1 + 0.2`.

## Referências

- [JEP 286 — Local-Variable Type Inference (`var`)](https://openjdk.org/jeps/286) — o JEP do `var`: o que ele é e o que não é
- [Primitive Data Types (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html) — os 8 primitivos e as faixas
- [Autoboxing and Unboxing (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/data/autoboxing.html) — conversão automática entre primitivo e wrapper
- [The Java Language Specification — Chapter 5: Conversions](https://docs.oracle.com/javase/specs/jls/se24/html/jls-5.html) — casting, widening e narrowing em detalhe

## Próximo módulo

**Operadores** — aritmética, comparação, lógica, ternário, `instanceof`,
precedência e os detalhes que diferem do TS.

[→ 04 — Operadores](./04-operadores.md)