# 06 — Métodos e Funções

Métodos no Java: assinatura, sobrecarga, varargs, passagem por valor, métodos estáticos e as diferenças pra funções do
TS.

## Anatomia de um método

```java
public BigDecimal calculateTotal(Order order, Coupon coupon) {
  BigDecimal base = order.total();
  return coupon.applyTo(base);
}
```

As partes, da esquerda pra direita:

| Parte       | Exemplo                        | Papel                                                    |
|-------------|--------------------------------|----------------------------------------------------------|
| Modificador | `public`                       | Quem pode acessar (público, privado, protegido, package) |
| Retorno     | `BigDecimal`                   | O tipo devolvido. `void` = nada                          |
| Nome        | `calculateTotal`               | camelCase, verbo que descreve a ação                     |
| Parâmetros  | `(Order order, Coupon coupon)` | Tipos e nomes                                            |
| Corpo       | `{ ... }`                      | O que executa                                            |

Toda função no Java vive dentro de uma classe (ou record, enum, interface com `default`). Não existe função solta no
topo do arquivo como no TS.

## Sobrecarga

Vários métodos com o **mesmo nome** e **assinaturas diferentes** (tipo ou quantidade de parâmetros):

```java
public BigDecimal applyDiscount(BigDecimal total, double percentage) {
  return total.multiply(BigDecimal.valueOf(1 - percentage));
}

public BigDecimal applyDiscount(BigDecimal total, BigDecimal fixedAmount) {
  return total.subtract(fixedAmount);
}
```

O compilador escolhe a versão pelo tipo dos argumentos na chamada:

```java
applyDiscount(total, 0.10);         // versão double

applyDiscount(total, BigDecimal.TEN); // versão BigDecimal
```

Só mudar o retorno não conta como sobrecarga. O TS faz o mesmo com overloads, mas no Java a resolução é em tempo de
compilação e não existe a versão "de implementação" extra.

## Varargs

Parâmetro que aceita zero ou mais valores, tratado como array:

```java
public String join(String separator, String... parts) {
  return String.join(separator, parts);
}
```

```java
join("-","java","moderno","streams");   // java-moderno-streams

join("-");                                   // "" (zero elementos)
```

O varargs precisa ser o **último** parâmetro. Chamar com array também funciona: `join("-", new String[]{"a", "b"})`.
Compare com o rest/spread do TS (`...parts: string[]`).

## Passagem por valor

O Java passa **cópia do valor** dos argumentos. Parece simples, mas divide a cabeça: pra primitivo, copia o valor; pra
objeto, copia a **referência** (o ponteiro), não o objeto.

```java
void mutate(int number) {
  number = 99;            // não afeta quem chamou
}

void mutate(Order order) {
  order.setStatus(PAID);  // afeta o objeto original
  order = new Order();    // mas reassignar não afeta quem chamou
}
```

```java
Order myOrder = new Order(CREATED);

mutate(myOrder);
// myOrder está PAID, porque mutou o mesmo objeto
// mas continua sendo a mesma instância (não virou o novo Order)
```

A pegadinha: mutar o objeto funciona, reassignar a referência não. Não existe
"ponteiro pra ponteiro" nem passagem por referência de verdade, como `ref` do C# ou `&mut` do Rust.

## Métodos estáticos

Pertencem à classe, não à instância:

```java
public class OrderCalculator {
  public static BigDecimal tax(BigDecimal amount) {
    return amount.multiply(TAX_RATE);
  }
}
```

```java
BigDecimal tax = OrderCalculator.tax(amount);
```

- Chamados pela classe (`OrderCalculator.tax(...)`), sem instanciar.
- Não acessam estado de instância (não existe `this`).
- Equivalem às funções exportadas do TS (`export function tax(...)`).

Métodos utilitários (`Collections`, `Objects`, `Math`) são estáticos. A tendência moderna (com records e programação
funcional) é usar estáticos onde não há estado, em vez de instâncias sem propósito.

### Métodos puros vs efeito colateral

Um método **puro** devolve o mesmo resultado pros mesmos argumentos e não toca em nada fora dele (não muta estado, não
imprime, não grava). Um método com **efeito colateral** faz algo que escapa do retorno.

```java
// puro: mesmo input, mesmo output, nada externo muda
public BigDecimal applyTax(BigDecimal total) {
  return total.multiply(TAX_RATE);
}

// efeito colateral: altera estado externo
public void markAsPaid(Order order) {
  order.setStatus(PAID);
  sendReceipt(order);
}
```

Método puro é mais fácil de testar (não precisa de mock) e de compor (não depende de ordem). O Java moderno favorece
puro onde dá: records e streams são imutáveis e sem efeito colateral por design. Efeito colateral fica na fronteira:
gravar, enviar, logar, ler entrada.

## Métodos default em interfaces (JDK 8)

Uma interface pode ter método com corpo, usado por quem implementa sem reescrever:

```java
public interface Sortable {
  int compareWeight();

  default boolean isHeavierThan(Sortable other) {
    return compareWeight() > other.compareWeight();
  }
}
```

Útil pra evoluir interface sem quebrar implementadores (quem não sobrescreve ganha o comportamento padrão).

Desde o JDK 9, a interface também aceita método `private`, pra compartilhar código entre os `default` sem expor na API
pública:

```java
public interface Formatter {
  default String headline(String text) {
    return decorate(text, "#");
  }

  default String subline(String text) {
    return decorate(text, "-");
  }

  private String decorate(String text, String marker) {
    return marker + " " + text + " " + marker;
  }
}
```

O `private` da interface é detalhe interno: quem implementa não vê. Mesmo papel do `private` helper de classe, só que
dentro da interface.

## Encadeamento de chamadas (fluent)

Métodos que devolvem `this` permitem encadear na mesma linha:

```java
builder.append("a").

append("b").

append("c");   // StringBuilder

orderService.

create()
        .

addItem(item)
        .

addItem(other)
        .

checkout();
```

A regra: cada chamada devolve o próprio objeto (ou um novo), e a próxima chama em cima dele. É o que move builders,
streams e o `HttpClient` do módulo

19. No TS você encadeia igual; a diferença é que no Java o retorno `this` (ou
    `new`) é explícito na assinatura.

## Retorno e `void`

```java
public void log(Order order) {     // não retorna nada
  System.out.println(order);
}

public Optional<Order> findById(long id) {   // retorna Optional
  return repository.findById(id);
}
```

Regra de ouro do Java moderno: ausência de resultado retorna `Optional`, não
`null`. Método que não retorna nada usa `void` (ou `Optional` se a "ausência"
é o resultado esperado).

## Métodos default em interfaces (JDK 8)

Uma interface pode ter método com corpo, usado por quem implementa sem reescrever:

```java
public interface Sortable {
  int compareWeight();

  default boolean isHeavierThan(Sortable other) {
    return compareWeight() > other.compareWeight();
  }
}
```

Útil pra evoluir interface sem quebrar implementadores (quem não sobrescreve ganha o comportamento padrão).

## Comparação rápida com TypeScript

| Conceito     | Java                      | TypeScript                   |
|--------------|---------------------------|------------------------------|
| Onde vive    | dentro de classe/record   | solta ou em módulo           |
| Função solta | não existe                | `export function`            |
| Overload     | sim, por assinatura       | sim, com implementação única |
| Rest/spread  | `String... parts`         | `...parts: string[]`         |
| Passagem     | cópia de valor/referência | cópia de valor/referência    |
| Sem this     | `static`                  | função fora de objeto        |

## Exercícios

1. Escreva uma classe `StringUtils` com um método estático
   `truncate(String text, int maxLength)` que corta em `maxLength` e adiciona
   `...` quando houve corte. Teste com texto menor que o limite (sem corte), exatamente no limite e `null`.

```java
void main() {
  IO.println(StringUtils.truncate("Hello, World!", 5));
  IO.println(StringUtils.truncate("Hello, World!", 12));
  IO.println(StringUtils.truncate("Hello, World!", 13));
  IO.println(StringUtils.truncate("Hello, World!", 20));
  IO.println(StringUtils.truncate(null, 0));
}

class StringUtils {
  private StringUtils() {
  }

  public static String truncate(String text, int maxLength) {
    if (text == null) {
      return null;
    }
    return text.substring(0, Math.min(text.length(), maxLength));
  }
}
```

2. Crie duas sobrecargas de `calculateShipping`: uma que recebe `double
   weight` e outra `double weight, String destination`. Teste cada uma e explique qual o compilador escolhe em
   `calculateShipping(1.5)`.

```java
void main() {
  IO.println(CalculateShipping.execute(1.5));
  IO.println(CalculateShipping.execute(10, "USA"));
}

class CalculateShipping {
  private CalculateShipping() {
  }

  public static double execute(double weight) {
    return weight * 10;
  }

  public static double execute(double weight, String destination) {
    return weight * 15;
  }
}
```

R.Escolhe a primeira

3. Escreva um método `max(int... numbers)` que retorna o maior valor. Teste com zero argumentos (decida o
   comportamento), um argumento e vários.

```java
void main() {
  IO.println(MaxVaargs.execute(1, 2, 3));
  IO.println(MaxVaargs.execute(3, 2, 1));
  IO.println(MaxVaargs.execute(-3, -5, 1));
  IO.println(MaxVaargs.execute());
}

class MaxVaargs {
  private MaxVaargs() {
  }

  public static int execute(int... numbers) {
    if (numbers.length == 0) {
      throw new IllegalArgumentException("Cannot find max of empty array");
    }

    int max = Integer.MIN_VALUE;
    for (int number : numbers) {
      if (number > max) {
        max = number;
      }
    }

    return max;
  }
}
```

4. Escreva `addElement(List<String> list, String element)` que adiciona o
   elemento e retorna a lista. Confira que a lista passada muda (mutação via
   referência). Depois escreva `replaceList(List<String> list)` que, dentro do
   corpo, faz `list = new ArrayList<>()` e retorna a lista nova. Chame de fora
   e confira que a lista original continua intacta. Explique por que a segunda
   não afeta o chamador: o método recebe uma cópia da referência, então
   reatribuir a variável local não mexe no objeto original — mutação funciona,
   substituição da referência não.

## Referências

- [Defining Methods (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/javaOO/methods.html) — assinatura,
  sobrecarga e a regra do retorno
- [Varargs (Oracle docs)](https://docs.oracle.com/javase/8/docs/technotes/guides/language/varargs.html) — argumentos
  variáveis e quando usar (com moderação)
- [The Java Language Specification — Method Invocation](https://docs.oracle.com/javase/specs/jls/se24/html/jls-15.html#jls-15.12) —
  como o compilador resolve a sobrecarga em 3 fases

## Próximo módulo

**Strings e Text Blocks** — imutabilidade, principais métodos, `StringBuilder`
e os text blocks do JDK 15.

[→ 07 — Strings e Text Blocks](./07-strings.md)