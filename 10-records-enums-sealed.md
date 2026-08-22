# 10 — Records, Enums e Sealed Classes

Os três mecanismos que o Java usa pra modelar dados: `record` no lugar de
DTO, `enum` avançado e tipos selados com exaustividade garantida pelo
compilador.

## Records (JDK 16)

Um `record` é uma classe imutável cuja API deriva da declaração. O que você
escrevia como classe cheia de getter, `equals`, `hashCode` e `toString`:

```java
public record Product(String sku, String name, BigDecimal price) {}
```

O compilador gera: construtor, métodos de acesso (`sku()`, `name()`,
`price()`), `equals`, `hashCode` e `toString`. Todos coerentes com os campos.

O que muda em relação a uma classe:

- Campos são `private final`. Não existe setter.
- `equals` e `hashCode` comparam por **valor**, não por referência.
  `new Product("a", "x", 1).equals(new Product("a", "x", 1))` é `true`.
- O nome do método de acesso é o nome do campo, sem prefixo `get`.
- `record` não pode estender classe (já estende `java.lang.Record`) e é
  implicitamente `final`.
- Dá pra implementar interfaces.

### Validação no compact constructor

```java
public record Product(String sku, String name, BigDecimal price) {
    public Product {
        if (sku.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("sku e name são obrigatórios");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("price deve ser positivo");
        }
    }
}
```

O `public Product { ... }` sem parâmetros roda antes da atribuição dos
campos. Você valida na criação, e um `Product` inválido nunca existe.

### Campos derivados e métodos

```java
public record Rectangle(double width, double height) {
    public double area() {
        return width * height;
    }

    public static Rectangle square(double side) {
        return new Rectangle(side, side);
    }
}
```

Componentes (o que está no cabeçalho) são a "cara" do record. Campos
derivados do corpo são `static` ou calculados; você não adiciona campo de
instância fora do cabeçalho.

### Records e coleções

Por comparar por valor e ser imutável, `record` é ideal pra chave de `Map`,
elemento de `Set` e pra destructuring com pattern matching (módulo 11).

### Imutabilidade rasa

O record garante a referência `final`, não a imutabilidade do conteúdo. Um
componente mutável continua mutável:

```java
public record Order(String id, List<Item> items) {}
```

```java
List<Item> mutable = new ArrayList<>();
Order order = new Order("x", mutable);

mutable.add(new Item(...));          // muta por fora
order.items().add(new Item(...));    // muta pela própria referência do record
```

Proteção padrão: copie na entrada (e na saída, se expõe):

```java
public record Order(String id, List<Item> items) {
    public Order {
        items = List.copyOf(items);   // imutável na criação
    }
}
```

Regra: `record` é imutável se **todos** os componentes forem imutáveis
(`String`, `BigDecimal`, primitivo, outro record, `List.of`). Componente
mutável quebra a promessa. Mesma regra vale pra `List.copyOf` no módulo 08:
imutabilidade da coleção, não do elemento.

## Enums

`enum` é mais que lista de constantes. Pode ter campo, construtor e método:

```java
public enum OrderStatus {
    CREATED("criado", true),
    PAID("pago", true),
    SHIPPED("enviado", false),
    CANCELLED("cancelado", false);

    private final String label;
    private final boolean reversible;

    OrderStatus(String label, boolean reversible) {
        this.label = label;
        this.reversible = reversible;
    }

    public String label() {
        return label;
    }

    public boolean reversible() {
        return reversible;
    }
}
```

### Métodos que acompanham enum

```java
OrderStatus status = OrderStatus.valueOf("PAID");     // resolve pelo nome
OrderStatus missing = OrderStatus.valueOf("paid");     // IllegalArgumentException

OrderStatus[] all = OrderStatus.values();              // todas as constantes
```

`valueOf` lança exceção pra nome inexistente. Se o valor vem de fonte externa
(banco, API), trate antes de converter.

### Enum com comportamento

```java
public enum DiscountPolicy {
    NONE {
        @Override
        public BigDecimal apply(BigDecimal total) {
            return total;
        }
    },
    TEN_PERCENT {
        @Override
        public BigDecimal apply(BigDecimal total) {
            return total.multiply(BigDecimal.valueOf(0.90));
        }
    };

    public abstract BigDecimal apply(BigDecimal total);
}
```

Cada constante tem sua implementação. O `switch` sobre enum com pattern
matching (módulo 11) resolve o mesmo problema de forma mais legível em muitos
casos.

### Enum em `switch`

```java
String next = switch (status) {
    case CREATED -> "PAID";
    case PAID -> "SHIPPED";
    case SHIPPED -> "COMPLETED";
    case CANCELLED -> "CANCELLED";
};
```

Como `OrderStatus` é enum, o switch é exaustivo sem `default`. Se você adiciona
uma constante nova ao enum, o compilador reclama que o switch não cobre tudo.
Comparação com TS: o `enum` do TS não tem esse poder; você depende de
`default` ou checagem manual.

Quando o case depende de uma condição do enum (ex.: só mostrar uma label se o
status é reversível), a guarda `when` do pattern matching (módulo 11) encaixa:

```java
String action = switch (status) {
    case CREATED when isLoyalCustomer -> "VIP";
    case CREATED -> "normal";
    case PAID -> "confirmado";
    case SHIPPED, CANCELLED -> "final";
};
```

## Sealed classes (JDK 17)

Uma classe ou interface que restringe quem pode ser subtipo:

```java
public sealed interface Payment permits CreditCard, Pix, Boleto {}
```

```java
public record CreditCard(String number, int installments) implements Payment {}
public record Pix(String key, String provider) implements Payment {}
public record Boleto(String barcode, LocalDate dueDate) implements Payment {}
```

Quem estende um sealed precisa ser `final`, `sealed` ou `non-sealed`. `record`
é implicitamente `final`. Os três casos numa hierarquia só:

```java
public sealed interface Shape permits Circle, Square, FreeForm {}

public record Circle(double radius) implements Shape {}   // final (record)

public final class Square implements Shape { ... }        // final: fecha o galho

public non-sealed class FreeForm implements Shape { ... } // aberto de novo
```

- `final`: nada estende daqui.
- `sealed`: continua restrito (permite outros).
- `non-sealed`: volta a ser aberto, qualquer um estende.

`Square` final e `FreeForm` non-sealed mostram os dois casos que não são
record. O `switch` exaustivo cobre `Circle`, `Square` e `FreeForm`; se um
quarto tipo aparecer, o compilador reclama.

O ganho real aparece no `switch`: o compilador conhece a lista fechada de
subtipos e exige que o switch cubra todos. Sem `default` e sem exceção em
runtime pra caso esquecido.

```java
String describe(Payment payment) {
    return switch (payment) {
        case CreditCard cc -> "cartão " + cc.number();
        case Pix p -> "pix " + p.provider();
        case Boleto b -> "boleto vence " + b.dueDate();
    };
}
```

Se um quarto tipo implementar `Payment`, o compilador para de compilar até o
switch cobrir.

### Relação entre os três

- `record` modela o **produto**: um dado com vários campos.
- `sealed` modela a **soma**: um tipo que é um dentre vários.
- `enum` é a soma **fixa de constantes** (sem campos novos por constante,
  apesar de poder ter comportamento).

O trio juntos forma o que linguagens chamam de *algebraic data types*. O
TS faz isso com union type + objeto, sem checagem de exaustividade no
compilador. No Java, o `switch` garante.

### Serialização

Record é naturalmente seguro pra serialização Java (módulo 15): os componentes
são os campos, e a desserialização usa o construtor canônico (validação do
compact constructor roda de novo). Sem os truques de reflexão que classes
comuns exigem.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| `enum` | JDK 5 | Permanente |
| Records | JDK 16 | Permanente |
| Sealed classes | JDK 17 | Permanente |

## Comparação com TypeScript

| Conceito | Java | TypeScript |
| -------- | ---- | ---------- |
| DTO imutável | `record` | interface + `as const` / classe `readonly` |
| Soma de tipos | `sealed` + `record` | union type |
| Exaustividade | compilador exige | não verifica, você usa `default` |
| Constantes | `enum` | `enum` ou `as const` object |

A diferença que mais sente: no TS, adicionar um caso na union não quebra os
`switch` que você esqueceu. No Java com sealed, quebra na compilação. Isso
muda o jeito de trabalhar: o Java te força a cobrir o novo caso, e o risco de
esquecer some.

## Exercícios

1. Converta uma classe `User` com getters/setters e `equals`/`hashCode`
   manuais pra um `record`. Liste o que o record gera e o que você perde
   (mutabilidade, por exemplo).
2. Crie um `record Transaction(String id, BigDecimal amount, TransactionType type)`
   com um `enum TransactionType { INCOME, EXPENSE }`. Valide no compact
   constructor que `amount` é positivo e que `id` não é vazio. Teste com
   valores inválidos.
3. Crie um `sealed interface Notification` com `Email`, `Sms` e `Push`. Escreva
   um `switch` exaustivo que devolve a mensagem de cada um. Depois adicione um
   quarto tipo `WhatsApp` e observe o erro do compilador. O que o erro diz?
4. Escreva um `enum CardSuit { HEARTS, DIAMONDS, CLUBS, SPADES }` e um método
   que devolve o emoji (♠♥♦♣) por naipe. Teste o caso de `valueOf` com nome
   que não existe (o que lança?) e o switch cobrindo os quatro naipes.

## Referências

- [JEP 395 — Records](https://openjdk.org/jeps/395) — o record como "tupla nominal" de dados imutáveis (JDK 16)
- [JEP 409 — Sealed Classes](https://openjdk.org/jeps/409) — `sealed`, `permits`, `final`/`sealed`/`non-sealed` (JDK 17)
- [Sealed Classes and Interfaces (Java Language Updates)](https://docs.oracle.com/en/java/javase/17/language/sealed-classes-and-interfaces.html) — a doc oficial do `sealed` com exemplos de record
- [Enum Types (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/javaOO/enum.html) — enum com campo, construtor e método

## Próximo módulo

**Pattern Matching e Switch Moderno** — `instanceof` com pattern, `switch`
sobre tipos, guardas e destructuring de records.

[→ 11 — Pattern Matching e Switch Moderno](./11-pattern-matching.md)