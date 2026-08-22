# 09 — OOP — Classes, Herança e Interfaces

A orientação a objetos do Java: encapsulamento, herança, polimorfismo,
classes abstratas e interfaces. Os conceitos você já domina; aqui entra como
cada um funciona no Java, com as convenções e armadilhas.

## Uma classe concreta

```java
public class Order {
    private final String id;
    private final List<Item> items;

    public Order(String id, List<Item> items) {
        this.id = id;
        this.items = new ArrayList<>(items);
    }

    public String id() {
        return id;
    }

    public List<Item> items() {
        return items;
    }

    public BigDecimal total() {
        return items.stream()
                .map(Item::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

Pontos da convenção Java:

- Campos `private`. Acessados por métodos públicos.
- `final` no campo: referência imutável, atribuída uma vez no construtor.
- O construtor copia a lista que chega. Quem recebe `items()` recebe a lista
  interna; se ela fosse a original, quem chamou poderia mutar seu objeto.
- `this` desambigua quando parâmetro tem o mesmo nome do campo.

## Construtores

O construtor é o método de criação. Detalhes que o Java tem e muita gente
passa batido:

**Sobrecarga com `this(...)`**:

```java
public class Order {
    private final String id;
    private final LocalDateTime createdAt;

    public Order(String id) {
        this(id, LocalDateTime.now());
    }

    public Order(String id, LocalDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }
}
```

`this(...)` chama outro construtor da mesma classe, como primeira instrução.
Evita repetir a atribuição dos campos quando há um construtor "canônico".

**Construtor privado (não instanciar / singleton)**:

```java
public class MathUtils {
    private MathUtils() {
        // impede `new MathUtils()`; classe só de métodos estáticos
    }
}
```

**Campo `final` no construtor**: a atribuição precisa acontecer em todo
caminho do construtor. Campo `final` sem valor atribuído em algum branch
não compila. Isso é o que dá a garantia de imutabilidade que os records
usam.

## Encapsulamento: getters e setters

O padrão histórico é getter/setter:

```java
public class User {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

Convenção: `getName()`/`setName()` pra campo `name`. Objeto mutável com
setter público. Boa parte do código Java legado é assim.

O Java moderno anda pra outra direção: campos `final`, objeto imutável,
métodos de acesso sem prefixo, e `record` quando o tipo é só dado (módulo 10).
O getter clássico continua válido em objetos que **têm** estado mutável; em
objeto que **é** dado, `record` substitui.

Decisão prática: se o objeto existe pra carregar dados, use `record`. Se ele
tem comportamento e estado que muda, classe normal com encapsulamento.

## Herança

```java
public class DiscountedOrder extends Order {
    private final BigDecimal discount;

    public DiscountedOrder(String id, List<Item> items, BigDecimal discount) {
        super(id, items);
        this.discount = discount;
    }

    @Override
    public BigDecimal total() {
        return super.total().subtract(discount);
    }
}
```

Regras:

- `extends` define herança. O Java tem herança **simples** de classe: uma
  classe estende só uma.
- O construtor da subclasse chama `super(...)` como primeira instrução (o
  compilador injeta o `super()` sem argumento se você não chama).
- `@Override` é anotação, mas o compilador valida: método com `@Override` que
  não sobrescreve nada é erro de compilação.
- A subclasse herda o que é `public`/`protected`. `private` não herda.
- Todo objeto é `Object` por baixo. `toString`, `equals`, `hashCode` vêm de
  lá e você sobrescreve quando precisa.

Herança é acoplamento forte. A regra da comunidade: favoreça composição e
interfaces. `extends` fica pra relação "é um" de verdade.

## Polimorfismo

```java
public class PaymentProcessor {
    public void process(Payment payment) {
        payment.pay();
    }
}
```

```java
public interface Payment {
    void pay();
}
```

```java
public class CreditCardPayment implements Payment {
    public void pay() {
        chargeCard();
    }
}

public class PixPayment implements Payment {
    public void pay() {
        transferPix();
    }
}
```

`process` recebe a interface. Quem chama decide a implementação:

```java
processor.process(new CreditCardPayment());
processor.process(new PixPayment());
```

O código que usa `Payment` não conhece a implementação concreta. Isso permite
trocar comportamento sem tocar em quem consome.

## Classes abstratas

```java
public abstract class Report {
    public final void generate() {
        String header = buildHeader();
        String body = buildBody();
        save(header + body);
    }

    protected abstract String buildHeader();
    protected abstract String buildBody();

    private void save(String content) {
        // grava o relatório
    }
}
```

Diferença pra interface:

- Classe abstrata pode ter **estado** (campos) e construtor.
- Define um template: o método `generate` é fixo, as partes variáveis são
  `abstract` e as subclasses implementam.
- Herança única vale aqui também: você não pode estender duas classes
  abstratas.

O padrão template method acima é o uso clássico. Se você só precisa de um
contrato sem estado, interface resolve melhor.

## Interfaces

```java
public interface Sortable {
    int weight();

    default int compareTo(Sortable other) {
        return Integer.compare(weight(), other.weight());
    }

    static Sortable byWeight() {
        return (a, b) -> Integer.compare(a.weight(), b.weight());
    }
}
```

Evolução da interface no Java:

| Versão | O que entrou |
| ------ | ------------ |
| JDK 8 | `default` e `static` com corpo |
| JDK 9 | métodos `private` em interface |
| JDK 16 | herança múltipla de interface (não de classe) |

- Uma classe implementa várias interfaces. É o "herança múltipla" do Java:
  só de contrato, sem estado.
- `default` dá implementação opcional. Quem não sobrescreve usa a padrão.
- `static` fica na interface, chamado por ela: `Sortable.byWeight()`.
- Interface sem nenhum método é **marker interface** (`Serializable`), sinal
  pro runtime ou framework.

Onde herança de classe e interface se diferenciam: herança carrega estado e
comportamento; interface só declara contrato. Prefira interfaces pra
polimorfismo e abstratas pro template.

## Composição sobre herança

```java
public class OrderService {
    private final PaymentGateway gateway;
    private final TaxCalculator taxCalculator;

    public OrderService(PaymentGateway gateway, TaxCalculator taxCalculator) {
        this.gateway = gateway;
        this.taxCalculator = taxCalculator;
    }
}
```

Em vez de `OrderService extends PaymentGateway`, receba as dependências e
delegue. Testabilidade melhora (dá pra injetar mock), o acoplamento cai e a
classe faz uma coisa só. Isso é a base do que o Spring faz depois com DI.

## `equals` e `hashCode`

Duas regras que você viola se não seguir:

1. Se sobrescreveu `equals`, sobrescreva `hashCode`. Dois objetos `equals`
   precisam ter o mesmo hash.
2. `equals` deve ser simétrico, reflexivo e transitivo. Comparar uma subclasse
   com `instanceof` na base quebra a simetria.

```java
@Override
public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof User user)) return false;
    return Objects.equals(name, user.name) && Objects.equals(email, user.email);
}

@Override
public int hashCode() {
    return Objects.hash(name, email);
}
```

O `Objects.equals` trata `null` de graça: `a.equals(b)` com `a` nulo estoura
NPE, `Objects.equals(a, b)` não.

Com `record` isso tudo é gerado (módulo 10). Pra classe que você escreve à
mão, o padrão acima vale.

### `toString`

Todo objeto herda `toString` de `Object`, mas a versão padrão mostra só o
nome da classe + hash (`User@1a2b3c`), inútil pra debug. Sobrescreva quando a
classe vira dado:

```java
@Override
public String toString() {
    return "User[" + name + ", " + email + "]";
}
```

Pra campo público, o `record` gera um `toString` legível automaticamente.

## Anotações

`@Override` é uma **anotação**: um marcador que o compilador (ou uma lib)
lê e usa. O Java tem umas prontas e você pode criar as suas:

```java
@Override                    // compiler valida que o método sobrescreve algo
@Deprecated                  // avisa quem usa que vai sair
@SuppressWarnings("unchecked")  // silencia um warning específico
```

- Anotação sem corpo (`@Override`) é um flag. Com parâmetros
  (`@SuppressWarnings("unchecked")`) leva configuração.
- O Java lê anotação em runtime via reflexão; frameworks (Spring, Jackson,
  JUnit) são movidos a anotação. O `@Test` do JUnit (módulo 18) e o
  `@JsonProperty` do Jackson (módulo 19) são anotações.
- Você pode criar anotação própria (`@interface`) e processar com reflexão,
  mas na maioria dos casos você **consome** anotações de libs, não cria.

O que importa aqui: anotação é metadado, não comportamento. Quem age sobre a
anotação é o compilador, o framework ou o seu código de reflexão.

## Comparação com TypeScript

| Conceito | Java | TypeScript |
| -------- | ---- | ---------- |
| Herança | `extends`, simples | `extends`, simples |
| Contrato | `interface` | `interface` / `type` |
| Construtor | `this`, `super` | `constructor`, `super` |
| Visibilidade | `private`/`protected`/`public` | `private`/`protected` (com `#`) |
| Estado em interface | não | `abstract` / propriedades |

O TS permite interface com propriedade e shape; a interface Java só declara
métodos (e constantes). O equivalente TS de "interface com estado" é classe
abstrata.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| `default`/`static` em interface | JDK 8 | Permanente |
| `private` em interface | JDK 9 | Permanente |
| Records (substituem DTO/getter) | JDK 16 | Permanente |
| Sealed classes | JDK 17 | Permanente |

## Exercícios

1. Escreva uma classe `BankAccount` com campo `balance` privado, métodos
   `deposit` e `withdraw`, e validação (não aceitar valor negativo nem
   saque maior que o saldo). Teste os limites.
2. Refatore uma classe com getters/setters pra um `record` e compare: o que
   se perde e o que se ganha? Escreva os dois e liste a diferença.
3. Crie uma interface `Shape` com método `area()` e uma classe `Circle` e
   `Rectangle` que a implementam. Use polimorfismo num método
   `totalArea(List<Shape>)`. Teste com lista vazia e com shapes mistos.
4. Escreva `equals`/`hashCode` pra uma classe `Product` com campos `sku` e
   `name`. Depois teste o comportamento num `HashSet`: dois produtos com
   mesmos valores devem ser o mesmo elemento. O que acontece se só o
   `equals` for sobrescrito?

## Referências

- [Lesson: Object-Oriented Programming Concepts (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/concepts/) — herança, interface e encapsulamento na visão da Oracle
- [Inheritance and Interfaces (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/IandI/) — `extends`, `@Override`, classes abstratas e interfaces
- [Object.equals / hashCode (Java API docs)](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Object.html) — o contrato que todo objeto herda
- [JEP 395 — Records](https://openjdk.org/jeps/395) — o record que substitui DTO/getter (JDK 16)

## Próximo módulo

**Records, Enums e Sealed Classes** — `record` no lugar de DTO, `enum`
avançado e tipos selados com exaustividade.

[→ 10 — Records, Enums e Sealed Classes](./10-records-enums-sealed.md)