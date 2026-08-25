# 20 — Design Patterns

Padrões de projeto no Java moderno. Os clássicos do GoF continuam válidos, mas o Java dos records, sealed classes, enums
e lambdas mudou como vários deles se escrevem. Este módulo mostra os padrões que aparecem na prática, com a
implementação que faz sentido hoje, e quando cada um é golpe de marketing.

## O que mudou na escrita

Dois recursos reescreveram a implementação de vários padrões:

- **`record`** elimina o boilerplate de valor: onde o GoF escrevia classes com getters, equals e hashCode (JavaBean),
  você escreve um record.
- **Lambda + interface funcional** substitui a "classe que implementa um método" por uma função. Estratégia, Observer e
  Template Method ganham versões de uma linha.
- **`enum`** já é um pattern: instâncias singulares com comportamento. Muitos
  "singletons" viram enum.
- **`sealed`** deixa o compilador saber os subtipos possíveis, o que elimina o
  `if (instanceof)` genérico e habilita pattern matching (módulo 11).

## Criacional

### Factory Method

A fábrica que decide qual objeto criar. Em Java moderno, com interface funcional:

```java
public interface TaskValidator {
  boolean isValid(String value);

  static TaskValidator byType(TaskType type) {
    return switch (type) {
      case TODO -> value -> !value.isBlank();
      case EPIC -> value -> value.length() >= 10;
      case BUG -> value -> value.contains("BUG-");
    };
  }
}
```

O GoF modelava isso com uma classe por variante. Aqui cada `case` é uma instância. O `switch` vira a fábrica.

### Builder

Quando um objeto tem muitos parâmetros opcionais, o construtor fica ilegível. O `record` não resolve isso (construtor
canônico pede tudo). Duas saídas:

```java
public record Query(Table table, List<String> columns, String where, int limit) {
  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private Table table;
    private List<String> columns = List.of("*");
    private String where = "";
    private int limit = 100;

    public Builder table(Table table) {
      this.table = table;
      return this;
    }

    public Builder columns(List<String> columns) {
      this.columns = columns;
      return this;
    }

    public Builder where(String where) {
      this.where = where;
      return this;
    }

    public Builder limit(int limit) {
      this.limit = limit;
      return this;
    }

    public Query build() {
      if (table == null) throw new IllegalStateException("table é obrigatória");
      return new Query(table, columns, where, limit);
    }
  }
}
```

```java
Query q = Query.builder()
    .table(Table.PRODUCTS)
    .where("price > 100")
    .limit(20)
    .build();
```

O builder valida no `build()`, não no meio do encadeamento. Se uma combinação não faz sentido, o erro aparece cedo.

### Singleton

O singleton clássico (instância privada + `getInstance`) quase nunca é a solução certa em Java. Razões: estado global
escondido, difícil de testar, difícil de trocar a implementação. A forma honesta de "uma instância" no Java moderno:

- **enum**: instância única garantida pela linguagem, com comportamento.
- **injeção de dependência**: o framework ou o `main` cria uma instância e entrega pra quem precisa (módulo 21).
- **`static` puro**: se não tem estado, `Util.toJson(obj)` não precisa de instância alguma.

O enum singleton ainda existe em código real:

```java
public enum AppConfig {
  INSTANCE;

  private final int maxConnections = 10;

  public int maxConnections() {
    return maxConnections;
  }
}
```

Mas ele é a exceção, não o padrão. Se você pensou "vou fazer um singleton", primeiro pergunte: a instância tem estado?
Se sim, alguém precisa trocar ou mockar esse estado num teste. Aí injete.

## Estrutural

### Adapter

Um tipo que você tem não bate com o tipo que o consumidor espera. O adapter traduz entre os dois. Em Java, isso costuma
virar um record que envolve o original:

```java
public record JsonApiError(int status, String message) {
  public static JsonApiError from(Throwable error, int status) {
    return new JsonApiError(status, error.getMessage());
  }
}
```

### Repository

O padrão que isola acesso a dados (o mini projeto 2 usa). A interface declara o contrato, a implementação fala com o
banco:

```java
public interface ProductRepository {
  Optional<Product> findById(long id);

  List<Product> findAll();

  Product save(Product product);
}

public class JdbcProductRepository implements ProductRepository {
  private final DataSource dataSource;

  public JdbcProductRepository(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Optional<Product> findById(long id) {
    try (var conn = dataSource.getConnection();
         var stmt = conn.prepareStatement("SELECT id, name, price FROM products WHERE id = ?")) {
      stmt.setLong(1, id);
      try (var rs = stmt.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(map(rs));
      }
    } catch (SQLException e) {
      throw new DataAccessException("falha ao buscar produto " + id, e);
    }
  }

  private Product map(ResultSet rs) throws SQLException {
    return new Product(rs.getLong("id"), rs.getString("name"), rs.getBigDecimal("price"));
  }
}
```

Quem usa o `ProductRepository` não sabe se os dados estão no Postgres, num arquivo ou em memória. Trocar a implementação
não toca no chamador.

### Facade

Uma fachada esconde a complexidade de um subsistema atrás de uma interface simples. No Java moderno isso costuma ser uma
classe que orquestra vários colaboradores com um método curto:

```java
public class OrderFacade {

  private final OrderRepository repository;
  private final InventoryService inventory;
  private final PaymentGateway payment;
  private final EmailSender email;

  public OrderFacade(OrderRepository repository, InventoryService inventory,
                     PaymentGateway payment, EmailSender email) {
    this.repository = repository;
    this.inventory = inventory;
    this.payment = payment;
    this.email = email;
  }

  public Order placeOrder(Cart cart) {
    inventory.reserve(cart.items());
    PaymentReceipt receipt = payment.charge(cart.total());
    Order order = repository.save(new Order(cart, receipt));
    email.sendConfirmation(order);
    return order;
  }
}
```

O chamador não sabe da ordem das operações, nem que existe inventário, gateway e email. O Facade é o "um botão" que liga
o subsistema. Ele não esconde arquitetura: separa o que o cliente precisa da complexidade interna.

### Proxy

Um substituto que controla o acesso ao objeto real. Diferença pro Decorator:
o proxy decide *se* e *quando* delegar; o decorator adiciona comportamento sempre que delega. Usos clássicos: lazy
loading, cache, controle de acesso, logging transparente.

```java
public interface UserService {
  User findById(long id);
}

public class UserServiceProxy implements UserService {

  private final UserService delegate;
  private final Map<Long, User> cache = new HashMap<>();

  public UserServiceProxy(UserService delegate) {
    this.delegate = delegate;
  }

  @Override
  public User findById(long id) {
    return cache.computeIfAbsent(id, delegate::findById);
  }
}
```

O proxy de cache evita bater no banco pra id já buscado. O cliente continua usando `UserService`; não sabe que tem cache
no meio. Em Java, proxies assim também nascem com `java.lang.reflect.Proxy` (proxy dinâmico), mas o mais comum hoje é o
proxy de frameworks (Spring AOP, por exemplo).

### Decorator

Adiciona comportamento a um objeto sem herança, envolvendo-o. O Java tem um exemplo famoso na JDK: `BufferedReader`
decora um `Reader` pra adicionar buffering e leitura de linha.

```java
public interface ReportRenderer {
  String render(Report report);
}

public class HtmlReportRenderer implements ReportRenderer {
  @Override
  public String render(Report report) {
    return "<h1>" + report.title() + "</h1>";
  }
}

public class TimestampDecorator implements ReportRenderer {

  private final ReportRenderer delegate;

  public TimestampDecorator(ReportRenderer delegate) {
    this.delegate = delegate;
  }

  @Override
  public String render(Report report) {
    return delegate.render(report) + "<small>" + Instant.now() + "</small>";
  }
}

public class HighlightDecorator implements ReportRenderer {

  private final ReportRenderer delegate;

  public HighlightDecorator(ReportRenderer delegate) {
    this.delegate = delegate;
  }

  @Override
  public String render(Report report) {
    return "<b>" + delegate.render(report) + "</b>";
  }
}
```

```java
ReportRenderer renderer = new HighlightDecorator(new TimestampDecorator(new HtmlReportRenderer()));
```

A ordem dos decorators muda o resultado, e cada um é testável isolado. A alternativa de "uma classe com flag pra cada
recurso" cresce sem limite; o decorator empilha comportamentos.

## Comportamental

### Strategy

Algoritmos intercambiáveis. No GoF: uma interface e N classes. Em Java moderno:
uma interface funcional e lambdas no ponto de uso.

```java
public interface PriceCalculator {
  BigDecimal apply(BigDecimal price);

  static PriceCalculator standard() {
    return price -> price;
  }

  static PriceCalculator withDiscount(BigDecimal percent) {
    return price -> price.subtract(price.multiply(percent));
  }

  static PriceCalculator taxed(BigDecimal rate) {
    return price -> price.add(price.multiply(rate));
  }
}
```

```java
BigDecimal finalPrice = calculator.apply(price);
```

O chamador escolhe a estratégia sem criar classe nenhuma. O padrão não mudou de nome, mudou de peso.

### Template Method

O esqueleto fixo, os passos variáveis. A interface com método `default`:

```java
public interface FileImporter {
  default ImportResult importFile(Path path) {
    String content = readFile(path);
    List<String> lines = parse(content);
    int saved = persist(lines);
    return new ImportResult(lines.size(), saved);
  }

  String readFile(Path path);

  List<String> parse(String content);

  int persist(List<String> lines);
}
```

O fluxo (ler, parsear, gravar) é um só; cada implementação define as partes. No GoF isso era uma classe abstrata; a
interface `default` faz o mesmo com menos herança.

### Chain of Responsibility

Uma requisição passa por uma cadeia de handlers até alguém tratar. O idioma moderno: `List` de lambdas e um loop.

```java
List<UnaryOperator<Order>> steps = List.of(
    order -> order.withStatus(OrderStatus.RECEIVED),
    order -> order.total() > 1000 ? order.withPriority(High) : order,
    order -> order.withStatus(OrderStatus.PROCESSED));

Order result = steps.stream().reduce(Order::andThen).orElseThrow().apply(order);
```

### Observer

Evento → quem escuta. Em Java moderno a interface tem um método e o
"observador" é um lambda:

```java
public interface OrderListener {
  void onOrderPlaced(Order order);
}
```

```java
orderService.subscribe(order ->emailSender.

sendConfirmation(order));
    orderService.

subscribe(order ->metrics.

record("order.placed"));
```

### State

O comportamento muda conforme o estado. Com sealed + pattern matching, o estado vira um tipo fechado e o comportamento
um `switch`:

```java
public sealed interface OrderState permits Pending, Paid, Cancelled {
}

public record Pending() implements OrderState {
}

public record Paid() implements OrderState {
}

public record Cancelled(String reason) implements OrderState {
}
```

```java
public boolean canRefund(Order order) {
  return switch (order.state()) {
    case Pending ignored -> false;
    case Paid ignored -> true;
    case Cancelled ignored -> false;
  };
}
```

O GoF escrevia um método por transição em cada classe de estado (um monte de boilerplate). O switch exaustivo entrega a
mesma garantia com muito menos código: se adicionar um estado, o compilador aponta onde o switch quebrou.

## Quando não usar

Padrão é resposta a um problema que você tem, não decoração. Sinais de que o padrão virou golpe de marketing:

- **Singleton pra "garantir uma instância"**: quase sempre esconde estado global. Injete.
- **Factory pra criar um objeto com construtor trivial**: `new` já resolve.
- **Builder quando o objeto tem 2-3 campos**: construtor + parâmetros nomeados... que o Java não tem, mas 3 campos ainda
  não justificam 40 linhas de builder. Use record.
- **Repository genérico com CRUD de tudo**: se cada entidade tem regra própria, um repository por agregado é mais
  honesto que um `BaseRepository<T>`.

A pergunta certa antes de aplicar um padrão: o que esse padrão torna mais fácil que o código atual? Se não houver
resposta, não aplique.

## Anti-patterns que você vai ver em código legado

Se padrão é uma solução testada, anti-pattern é o problema que parece solução. Os mais comuns em Java:

### God Object

Uma classe que faz de tudo: valida, persiste, formata, envia email, calcula. É o oposto do SRP (módulo 21). Sintoma: a
classe tem mais de 10 dependências ou métodos que não têm relação entre si. A cura é extrair colaborações (o mesmo
`OrderService` inchado que o SRP separa).

### Service Locator

Um objeto estático que devolve qualquer serviço:

```java
ServiceLocator.get(OrderRepository .class);
```

Parece injeção de dependência, mas esconde as dependências: a classe usa o locator e ninguém sabe o que ela precisa até
rodar. DI por construtor (módulo 21) resolve: a dependência aparece na assinatura e o teste passa o mock na mão.

### Getter/Setter culture

Classe com `getX`/`setX` pra todo campo, sem regra, exposta como um balcão de atributos:

```java
order.setStatus("PENDING");
order.

getItems().

add(item);
```

Isso transforma o objeto num `Map` disfarçado. O record + método de domínio (`order.markPaid()`) esconde o estado e
valida a transição (módulo 10). O setter público que não valida nada é o anti-pattern: qualquer um quebra a invariante.

### BaseRepository genérico

`BaseRepository<T>` com CRUD de tudo, e cada entidade herda:

```java
class BaseRepository<T> {
  void save(T e);

  void delete(long id);
}

class OrderRepository extends BaseRepository<Order> {
}
```

Parece DRY, mas acopla tudo: mudou uma regra de um agregado, afeta todos. Repository por agregado, com métodos que
descrevem a operação (`save`,
`findByCustomer`), é mais honesto que o genérico vazio.

### class8.Copy-paste de código

O sintoma não é o padrão, é a ausência de extração. Se o mesmo trecho aparece três vezes, extraia o método. Cada cópia é
um bug em potencial que você vai corrigir três vezes.

## Comparação com TypeScript

| Padrão     | Java                                | TypeScript                             |
|------------|-------------------------------------|----------------------------------------|
| Strategy   | interface funcional + lambda        | `(x) => y` direto                      |
| Singleton  | enum ou injeção                     | module singleton / `static`            |
| Builder    | builder no record                   | mesmo pattern, objetos literais ajudam |
| State      | sealed + switch exaustivo           | discriminated union + `switch`         |
| Observer   | interface + lambda                  | `EventEmitter` / callback              |
| Repository | interface + impl                    | interface + impl (igual)               |
| Decorator  | classe envolvendo outra             | HOFs / middleware (compose)            |
| Proxy      | classe ou `java.lang.reflect.Proxy` | Proxy/Reflect, getters                 |
| Facade     | classe orquestrando                 | module público escondendo internals    |

O TS e o Java moderno convergiram: functional interfaces/union types + lambdas achatam a maioria dos padrões
comportamentais. O que diferencia o Java é o
`sealed` + pattern matching, que devolve pro compilador o que no TS o
`switch` exaustivo sobre union não garante por tipo (o TS não exige exaustividade).

## O que mudou entre versões

| Feature                      | Versão | Situação   |
|------------------------------|--------|------------|
| `record`                     | JDK 16 | Permanente |
| `sealed` classes             | JDK 17 | Permanente |
| Pattern matching em `switch` | JDK 21 | Permanente |

## Exercícios

1. Implemente um `PaymentProcessor` que aceita estratégias de cálculo de imposto por lambda (Strategy) e um `switch`
   sobre o tipo de pagamento (Factory Method). Teste cada variante com valor zero, negativo e alto.
2. Escreva um builder pra um record `Report(String title, List<String> rows,
   boolean includeTotals, Path outputPath)` com validação no `build()`. Teste builder incompleto lançando
   `IllegalStateException` e builder completo.
3. Modele um pedido com `OrderState` sealed (`Pending`, `Paid`, `Cancelled`)
   e um método `canRefund(Order)` por switch exaustivo. Teste os três estados e prove que adicionar um estado quebra a
   compilação do switch (sem
   `default`).
4. Crie um `OrderRepository` (interface) com `JdbcOrderRepository` e uma implementação em memória
   `InMemoryOrderRepository`. Teste o serviço que usa o repository contra as duas implementações sem mudar o serviço.
5. Monte uma cadeia de transformações de `Order` (Chain of Responsibility)
   com 3 etapas e teste a ordem de aplicação e o que acontece com lista vazia.
6. Implemente um `ReportRenderer` com `HtmlReportRenderer`, `TimestampDecorator`
   e `HighlightDecorator`. Teste as três combinações de ordem e o decorator sozinho.
7. Escreva um `UserServiceProxy` com cache (sem chamar o delegate duas vezes pro mesmo id) e teste que a segunda chamada
   não toca o delegate (use um fake que conta chamadas).
8. Refatore uma classe "God Object" pequena (valida + persiste + formata)
   extraindo 2 colaborações e passe-as por construtor. Teste o resultado com fakes.

## Referências

- [Design Patterns: Elements of Reusable Object-Oriented Software (GoF)](https://en.wikipedia.org/wiki/Design_Patterns) —
  o livro original dos 23 padrões
- [Record Classes (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/javaOO/records.html) — records como
  transportadores de valor
- [Sealed Classes and Interfaces (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/oop/sealed.html) —
  subtipos controlados
- [Pattern Matching for Switch (JEP 441)](https://openjdk.org/jeps/441) — switch exaustivo sobre tipos sealed (JDK 21)
- [Repository pattern (Martin Fowler)](https://martinfowler.com/eaaCatalog/repository.html) — o catálogo de arquitetura
  da Fábrica de Software

## Próximo módulo

**Arquitetura e Boas Práticas** — organização de pacotes, SOLID na prática, injeção de dependência manual e os
princípios que valem ouro.

[→ 21 — Arquitetura e Boas Práticas](./21-arquitetura.md)