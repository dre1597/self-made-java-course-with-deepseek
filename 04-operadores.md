# 04 — Operadores

Os operadores do Java e as diferenças pro TypeScript. Sem conceito de programação: só como o Java se comporta.

## Aritméticos

```java
int sum = 10 + 5;
int difference = 10 - 5;
int product = 10 * 5;
int quotient = 10 / 5;
int remainder = 10 % 5;
```

Detalhes que pegam:

- **Divisão de inteiros trunca**: `7 / 2` é `3`, não `3.5`. Pra decimal, um dos operandos precisa ser `double`:
  `7 / 2.0`.
- **`%` com negativos** segue o sinal do dividendo: `-7 % 3` é `-1` (não `2`).
- **Divisão por zero**: com `int`, lança `ArithmeticException`; com
  `double`, dá `Infinity` ou `NaN`.

```java
int a = 7 / 0;            // ArithmeticException
double b = 7.0 / 0;       // Infinity
double c = 0.0 / 0;       // NaN
```

Comparação com TS: `7 / 2` no TS dá `3.5` (sempre float). No Java você precisa escolher a divisão inteira ou a real.

## Incremento e decremento

```java
int count = 0;
count++;          // count = 1
int result = count++;   // result = 1, count = 2 (pós-incremento)
int result2 = ++count;  // count = 3, result2 = 3 (pré-incremento)
```

O pós-incremento devolve o valor antigo e incrementa depois; o pré-incremento incrementa antes e devolve o novo. Mesma
semântica do TS.

## Relacionais

```java
boolean isEqual = a == b;
boolean isNotEqual = a != b;
boolean isGreater = a > b;
boolean isLess = a < b;
boolean isGreaterOrEqual = a >= b;
boolean isLessOrEqual = a <= b;
```

Armadilha: `==` em tipos de referência compara **identidade**, não valor. Dois `String` diferentes com o mesmo texto são
`== false`:

```java
String name1 = "java";
String name2 = "java";
name1 ==name2;        // false (objetos diferentes)

    Integer x = 128, y = 128;
x ==y;                // false (wrappers além do cach
```

Pra valor, use `.equals()`. Exceção: `enum` compara com `==` sem problema.

## Lógicos

```java
boolean and = a > 0 && b < 10;   // short-circuit: se a é falso, b nem avalia
boolean or = a > 0 || b < 10;    // short-circuit: se a é verdadeiro, b nem avalia
boolean not = !flag;
```

Os curto-circuitos `&&` e `||` não avaliam o lado direito se o resultado já está decidido. Os operadores bit a bit `&` e
`|` **avaliam os dois lados sempre** (e funcionam como lógicos também). No TS, `&&`/`||` seguem a mesma lógica de
curto-circuito, mas retornam o valor do operando (não só `boolean`); no Java, `&&`/`||` retornam sempre `boolean`.

## Ternário

```java
String status = isPaid ? "paid" : "pending";
```

Encadear ternário funciona, mas vira ilegível. Pra mais de duas opções,
`switch` expression (módulo 05) lê melhor:

```java
String label = switch (priority) {
  case 1, 2 -> "baixa";
  case 3, 4 -> "média";
  case 5 -> "alta";
  default -> "desconhecida";
};
```

## `instanceof`

Testa se um objeto é de um tipo (e seus subtipos):

```java
if(payment instanceof CreditCard){
CreditCard card = (CreditCard) payment;
}
```

Desde o JDK 16, o pattern matching elimina o cast manual (módulo 11):

```java
if(payment instanceof
CreditCard card){
    card.

number();
}
```

O `instanceof` do TS é o mesmo conceito, mas o Java estreita o tipo e o compilador valida.

## Bit a bit

```java
int flags = 0b1100;
int shifted = flags << 2;    // deslocamento pra esquerda
int masked = flags & 0b1000; // AND de bits
```

Raro no dia a dia de aplicação. Aparece em código de infraestrutura, flags, permissões e manipulação de bytes. `|` (OR),
`^` (XOR), `~` (NOT) completam.

## Precedência

| Ordem | Operadores                                     |
|-------|------------------------------------------------|
| 1     | `++`, `--`, unários `!`, `-`, cast `(T)`       |
| 2     | `*`, `/`, `%`                                  |
| 3     | `+`, `-`                                       |
| 4     | `<<`, `>>`                                     |
| 5     | relacionais `<`, `<=`, `>`, `>=`, `instanceof` |
| 6     | `==`, `!=`                                     |
| 7     | `&`                                            |
| 8     | `^`                                            |
| 9     | `\|`                                           |
| 10    | `&&`                                           |
| 11    | `\|\|`                                         |
| 12    | ternário `? :`                                 |
| 13    | atribuição `=`, `+=`, etc.                     |

Regra prática: multiplicação/divisão antes de soma, comparação antes de lógico, atribuição por último. Na dúvida,
parênteses. Igual em quase toda linguagem; o Java não tem nada bizarro aqui.

## Comparação rápida com TypeScript

| Operador           | Java                                    | TypeScript                     |
|--------------------|-----------------------------------------|--------------------------------|
| Igualdade de valor | `.equals()` em refs, `==` em primitivos | `===`                          |
| Igualdade frouxa   | não existe                              | `==` (evite)                   |
| Divisão inteira    | `7 / 2 == 3`                            | `Math.trunc(7 / 2)`            |
| Ternário           | `cond ? a : b`                          | `cond ? a : b`                 |
| Curto-circuito     | `&&` / `\|\|` retorna `boolean`         | `&&` / `\|\|` retorna operando |

## Exercícios

1. Escreva um método `calculateRemainder(int dividend, int divisor)` que retorna o resto. Teste com divisor zero (o que
   acontece? decida o comportamento), dividendos negativos e positivos.

```java
void main() {
  IO.println(CalculateRemainder.execute(10, 3));
  IO.println(CalculateRemainder.execute(-10, -30));
  IO.println(CalculateRemainder.execute(10, -30));
  IO.println(CalculateRemainder.execute(-10, 30));
//  IO.println(CalculateRemainder.execute(10, 0));
  IO.println(CalculateRemainder.execute(CalculateRemainder.execute(Integer.MIN_VALUE, -1));
  IO.println(CalculateRemainder.execute(CalculateRemainder.execute(Integer.MAX_VALUE, -1));
  IO.println(CalculateRemainder.execute(10, -1));
}

class CalculateRemainder {
  private CalculateRemainder() {
  }

  public static int execute(int dividend, int divisor) {
    if (divisor == 0) {
      throw new ArithmeticException("Division by zero is not allowed");
    }

    return dividend % divisor;
  }
}
```

2. Escreva um método `safeDivide(int dividend, int divisor)` que retorna o resultado como `double` e trata o caso de
   divisor zero sem lançar exceção. Teste com `10/4`, `-10/4`, `7/0`.

```java
void main() {
  IO.println(SafeDivide.execute(10, 4));
  IO.println(SafeDivide.execute(-10, 4));
  IO.println(SafeDivide.execute(7, 0));
  IO.println(SafeDivide.execute(0, 0));
}

class SafeDivide {
  private SafeDivide() {
  }

  static double execute(int dividend, int divisor) {
    if (divisor == 0) {
      return Double.NaN;
    }
    return (double) dividend / divisor;
  }
}
```

3. Usando `instanceof` com pattern matching, escreva um método que recebe um
   `Object` e retorna o tamanho se for `String` ou `List`, ou `-1` caso contrário. Teste com `null`.

```java
void main() {
  IO.println(LengthCalculator.execute("hello"));
  IO.println(LengthCalculator.execute(List.of(1, 2, 3)));
  IO.println(LengthCalculator.execute(123));
  IO.println(LengthCalculator.execute(null));
}


class LengthCalculator {
  private LengthCalculator() {
  }

  public static int execute(Object object) {
    if (object instanceof String string) {
      return string.length();
    }
    if (object instanceof List<?> list) {
      return list.size();
    }
    return -1;
  }
}
```

## Referências

- [Summary of Operators (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/nutsandbolts/opsummary.html) —
  tabela completa de precedência e operadores
- [Equality, Relational, and Conditional Operators (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/nutsandbolts/op2.html) —
  os operadores de comparação e lógica em detalhe
- [The Java Language Specification — Chapter 15: Expressions](https://docs.oracle.com/javase/specs/jls/se24/html/jls-15.html) —
  a fonte da verdade sobre operadores e resolução

## Próximo módulo

**Estruturas de Controle** — `if`, loops, `switch` expression e as mudanças de controle de fluxo do Java moderno.

[→ 05 — Estruturas de Controle](./05-estruturas-de-controle.md)