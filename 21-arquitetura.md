# 21 — Arquitetura e Boas Práticas

Organização de código Java que sobrevive a projeto grande: pacotes, SOLID na
prática, injeção de dependência sem framework e o que separa código que dá pra
manter de código que dá medo de tocar.

## Estrutura de pacotes

O pacote é o namespace e a fronteira de visibilidade. Convenção: domínio
reverso (`br.com.empresa.produto`) e organização por **feature**, não por
**camada**.

| Feio (por camada) | Melhor (por feature) |
| ----------------- | -------------------- |
| `controller.OrderController` | `order.OrderController` |
| `service.OrderService` | `order.OrderService` |
| `repository.OrderRepository` | `order.OrderRepository` |
| `model.Order` | `order.Order` |

Por camada você espalha o que muda junto (mudou o pedido, mexe em 4 pacotes).
Por feature, a mudança fica numa ilha. Dentro do pacote `order`, o que é
implementação fica `package-private`, o que é contrato é `public`.

## SOLID na prática

Os cinco princípios, no Java real, sem religião:

### S — Single Responsibility

A classe tem um motivo pra mudar. O sintoma clássico: método "faz tudo".

```java
// uma classe fazendo 3 coisas
public class OrderService {
    public void create(Order order) {
        validate(order);                 // regra
        save(order);                     // persistência
        sendEmail(order);                // comunicação
    }
}
```

Versão separada:

```java
public class OrderService {
    private final OrderRepository repository;
    private final EmailSender emailSender;
    private final OrderValidator validator;

    public OrderService(OrderRepository repository, EmailSender emailSender, OrderValidator validator) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.validator = validator;
    }

    public void create(Order order) {
        validator.validate(order);
        repository.save(order);
        emailSender.sendConfirmation(order);
    }
}
```

Não precisa virar um milhão de classes. O limite: se você não consegue dizer o
que a classe faz numa frase, ela tem motivos demais pra mudar.

### O — Open/Closed

Aberto pra extensão, fechado pra modificação. No Java moderno isso costuma ser
um `switch` exaustivo ou um `Map` de estratégia, não herança.

```java
public interface ShippingCalculator {
    BigDecimal calculate(ShippingDetails details);

    static ShippingCalculator combined(List<ShippingCalculator> calculators) {
        return details -> calculators.stream()
                .map(c -> c.calculate(details))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

Adicionar uma modalidade nova = adicionar um calculador, não mexer nos que
existem.

### L — Liskov Substitution

Subclasse precisa se comportar como a classe base. O Java deixa você quebrar
isso sem reclamar: `public class Stack extends Vector`, `class Bird extends
Animal` com `fly()` que lança. A versão moderna: **composição em vez de
herança**, e `sealed` quando a hierarquia é de verdade.

```java
public class OrderHistory {
    private final List<Order> orders = new ArrayList<>();

    public void add(Order order) { orders.add(order); }
    public List<Order> asList() { return List.copyOf(orders); }
}
```

Em vez de `extends ArrayList`, você guarda a lista e entrega cópia imutável.
Ninguém mais quebra a invariante.

### I — Interface Segregation

Interface gorda obriga quem implementa a carregar método que não usa. Separe.

```java
// gorducha: o serviço de leitura é obrigado a implementar save/delete
public interface OrderRepository {
    Optional<Order> findById(long id);
    void save(Order order);
    void delete(long id);
}
```

```java
public interface OrderQuery { Optional<Order> findById(long id); }
public interface OrderWrite { void save(Order order); }
public interface OrderDelete { void delete(long id); }
```

Na prática você não explode em 10 interfaces. Duas ou três por fronteira
(leitura vs escrita) já resolvem a maioria.

### D — Dependency Inversion

O alto nível depende da abstração, o baixo nível implementa. É o `Repository`
do módulo 20: a regra de negócio conhece `OrderRepository`, não o Postgres.

```java
public class OrderService {
    private final OrderRepository repository;   // interface, não a impl

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }
}
```

## Injeção de dependência manual

Sem Spring, o DI é só: a dependência entra pelo construtor e quem monta a
aplicação decide qual implementação passa. O ponto único de montagem chama
**composite root**:

```java
public class Application {
    public static void main(String[] args) {
        DataSource dataSource = createDataSource();
        OrderRepository repository = new JdbcOrderRepository(dataSource);
        OrderValidator validator = new OrderValidator();
        EmailSender emailSender = new SmtpEmailSender();

        OrderService orderService = new OrderService(repository, emailSender, validator);
        OrderController controller = new OrderController(orderService);

        startHttpServer(controller);
    }
}
```

O `main` é o único lugar que sabe o nome das implementações. `OrderService` e
`OrderController` não sabem se os dados moram no Postgres ou em memória. Testar
vira passar um `InMemoryOrderRepository` e pronto.

Regras de ouro:

- **Dependência pelo construtor**, não por `getInstance()` ou campo `static`.
- **Interface pra fronteira que muda** (banco, email, API externa). Pra classe
  de valor, a classe concreta basta.
- **Sem "Service locator"** (`Container.get(OrderService.class)`). Se você
  precisa disso, está recriando um framework pior.

## Imutabilidade e null-safety

O Java moderno empurra pra imutabilidade:

- `record` pra transporte de valor (módulo 10).
- Coleções: `List.of`, `Map.of` e cópias defensivas (`List.copyOf`).
- Objeto mutável só quando o estado precisa mudar de verdade (repository,
  conexão).

Null é a ausência. Trate assim:

- `Optional` pra retorno que pode faltar (módulo 13).
- **Nunca `null` como argumento obrigatório**: valide cedo com `requireNonNull`
  ou `Objects.requireNonNull`.
- Exceção pro que é erro de contrato, `Optional` pro que é ausência esperada.

```java
public class OrderService {
    public Order findById(long id) {
        return repository.findById(id)         // Optional
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
```

## Exceções como parte do contrato

Módulo 14 em forma de regra de arquitetura:

- **Unchecked** na fronteira interna: `OrderNotFoundException`, `DataAccessException`.
- **Checked** só na fronteira externa obrigatória (`IOException`, `SQLException`).
- Nunca deixe exceção da JDK vazar pra cima sem contexto: capture na borda do
  pacote e relance com a causa.

```java
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

## Validação na borda, não no meio

Quem recebe dado de fora (HTTP, arquivo, fila) valida antes de entrar no
domínio. O dado que já está dentro é confiável.

```java
public record CreateOrderRequest(long productId, int quantity) {
    public CreateOrderRequest {
        if (productId <= 0) throw new IllegalArgumentException("productId inválido");
        if (quantity <= 0) throw new IllegalArgumentException("quantity inválido");
    }
}
```

O construtor compacto do record já valida na criação. O domínio não repete a
checagem.

## Testabilidade

Código arquitetado pra teste é código que você consegue testar de verdade:

- Dependências por construtor → mock ou substituto fácil.
- Sem `static` chamando banco/API → sem `mockStatic` pra contornar.
- Funções puras pro que dá (módulo 18).

```java
public class OrderTotalCalculator {
    public BigDecimal total(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

`OrderTotalCalculator` não toca em nada externo: teste direto, sem mock.

## Arquitetura hexagonal (ports and adapters)

O nome assusta, a ideia é simples: o domínio (a regra de negócio) fica no
centro e **não conhece o mundo externo**. Banco, HTTP, fila e email são
**adaptadores** que plugam nas **portas** (interfaces) que o domínio define.

```
        ┌──────────────┐        ┌──────────────────┐
  HTTP  │  adaptador   │        │     domínio      │
────────►  controller  │──porta►│  OrderService    │
        └──────────────┘        │  (só conhece     │
                                │   interfaces)    │
  JDBC  ┌──────────────┐        └────────┬─────────┘
────────►  repository  │◄──porta──────────┘
        └──────────────┘         OrderRepository
```

O que muda de verdade na prática:

- O `OrderService` (domínio) declara a interface `OrderRepository` e a usa. Ele
  não importa nada de JDBC, nem de HTTP.
- O `JdbcOrderRepository` e o `OrderController` ficam fora do domínio, nos
  pacotes de adaptador (`infra`, `web`).
- O `main` (composite root) liga os adaptadores às portas. Trocar banco =
  trocar um adaptador, sem tocar no domínio.

O mini projeto 2 já é hexagonal embrionário: o `ProductHandler` é o adaptador
HTTP, o `JdbcProductRepository` o adaptador de dados, e o record `Product` o
domínio. A diferença pro "Java com framework" é só de organização.

A régua pra não exagerar: se a app não vai trocar de banco nem de transporte,
não precisa de portas pra tudo. Porta entra onde a fronteira **pode mudar**
(banco, API externa, fila). Interface pra classe interna que nunca muda é
cerimônia.

## Logging

Log é observabilidade: sem ele, produção vira caixa preta. A API padrão é o
**SLF4J**, que funciona com qualquer implementação por baixo (Logback,
Log4j2). O código depende da API, não da implementação.

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
}
```

Níveis e quando usar:

- `trace`/`debug`: detalhe de execução, ligado sob demanda.
- `info`: eventos de negócio que interessam (pedido criado, pagamento
  confirmado).
- `warn`: algo inesperado que não quebrou a operação (retry, valor fora do
  padrão).
- `error`: falha que precisa de atenção (exceção tratada).

Boas práticas que separam log útil de log que polui:

- **Use placeholders, não concatenação**: `log.info("pedido {} criado", id)`.
  A concatenação roda mesmo se o nível estiver desligado.
- **Log a exceção com a stack trace**: `log.error("falha ao salvar", e)`, não
  `log.error(e.getMessage())` (perde a causa).
- **Nunca logue dado sensível**: senha, token, CPF, cartão. O log é onde
  vazamento acontece.
- **Contexto ajuda**: inclua o id do pedido, do usuário, da transação pra
  rastrear uma operação de ponta a ponta.

```java
public Order create(Cart cart, long customerId) {
    log.debug("criando pedido para o cliente {}", customerId);
    Order order = repository.save(new Order(cart, customerId));
    log.info("pedido {} criado (total {})", order.id(), order.total());
    return order;
}
```

Configurar o nível por ambiente (`debug` em dev, `info` em prod) é decisão de
configuração, não de código.

## Configuração por ambiente

Dado que muda entre dev/test/prod (URL de banco, credencial, token, feature
flag) vai pra configuração, não pro código. O jeito simples, sem framework:

- **Variáveis de ambiente**: o padrão em deploy moderno (Docker, k8s).
- **Propriedades** (`application.properties`): com o valor default no código.

```java
public class AppConfig {

    public static String databaseUrl() {
        return System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/app");
    }

    public static String apiToken() {
        return System.getenv().getOrDefault("API_TOKEN", "");
    }
}
```

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl(AppConfig.databaseUrl());
config.setUsername(AppConfig.databaseUser());
```

Regras:

- **Segredo nunca no código nem no repo** (módulo 24): senha e token vêm de
  env ou secret manager, nunca hardcoded.
- Default sensato no código (localhost, dev) e override por env em prod.
- Validar na subida: se falta variável obrigatória, falhe cedo, não no meio
  da primeira request.

## Code smells e refactoring

Cheiro é o sintoma; o refactor é a cura. Os que mais aparecem em Java:

| Cheiro | Sintoma | Refactor |
| ------ | ------- | -------- |
| Long method | método com 30 linhas e 3 responsabilidades | extrair métodos |
| Feature envy | método usa mais dados de outra classe que da própria | mover o método |
| Duplicated code | mesmo trecho em 3 lugares | extrair método/classe |
| Data clumps | `(customerId, customerName, customerEmail)` passados juntos | virar record `Customer` |
| Primitive obsession | `String` pra status, `int` pra money | enum + `BigDecimal` |
| Shotgun surgery | mudança pequena mexe em 5 classes | agrupar por feature |
| Lazy class | classe que só repassa chamada | fundir na classe que usa |

```java
// antes: long method + feature envy
public void notifyCustomer(Customer customer, Order order) {
    String name = customer.getName();
    String email = customer.getEmail();
    EmailMessage message = new EmailMessage();
    message.setSubject("pedido " + order.getId() + " confirmado");
    message.setBody("Olá " + name + ", seu pedido chegou");
    emailSender.send(email, message);
}
```

```java
// depois: responsabilidade movida pro record que conhece o dado
public void notifyCustomer(Customer customer, Order order) {
    emailSender.send(customer.email(), customer.confirmationMessage(order));
}
```

O refactor mais seguro: **extrair** com o compilador te vigiando. Cada passo
pequeno (renomear, extrair método, mover) mantém os testes verdes. Refactor
não muda comportamento; se os testes quebrarem, você mudou de tarefa.

## Comparação com TypeScript

| Conceito | Java | TypeScript/Node |
| -------- | ---- | --------------- |
| DI manual | construtor + composite root | módulos + factory functions |
| Imutabilidade | `record`, `List.of` | `Readonly<T>`, `Object.freeze` |
| Null ausente | `Optional` | `?.` / `??` |
| Fronteira | `package` + `package-private` | módulos ES (barreiras) |
| Validação | construtor compacto do record | libs (zod) ou validadores |
| Arquitetura | hexagonal/ports & adapters | mesmo conceito (interfaces) |
| Logging | SLF4J + Logback/Log4j2 | `pino`, `winston` |
| Config | env vars + propriedades | `process.env` + dotenv |
| Refactoring | extrair com testes verdes | mesmas técnicas |

O TS e o Java moderno chegaram em lugares parecidos: imutabilidade por padrão,
null como caso a tratar, composição sobre herança. A diferença prática é a
fronteira: no TS o módulo é a barreira; no Java o `package` com `package-private`
dá controle mais fino. No logging e config os modelos são quase idênticos;
no Node o `process.env` é o padrão direto, no Java o env também mas com a
API de propriedades no meio.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| `record` | JDK 16 | Permanente |
| `sealed` | JDK 17 | Permanente |
| `Optional` | JDK 8 | Permanente |

## Exercícios

1. Reorganize (no papel) um projeto que tem `controller`, `service`, `model`,
   `repository` por camada numa estrutura por feature. Liste os pacotes que
   ficariam e o que é `public` vs `package-private`.
2. Refatore uma classe que faz validação, persistência e email (S). Extraia
   duas colaborações e passe-as por construtor (D). Teste o serviço com
   fakes.
3. Monte um composite root pra um `OrderService` com `JdbcOrderRepository`,
   `SmtpEmailSender` e `OrderValidator`, sem framework. Escreva um teste que
   injeta `InMemoryOrderRepository` e prova que o serviço não sabe qual
   repository está por baixo.
4. Implemente um `CreateOrderRequest` record com validação no construtor
   compacto e teste os casos de borda (quantity zero, productId negativo).
5. Escreva um `OrderHistory` que guarda pedidos e entrega `List.copyOf`
   imutável. Teste que adicionar via `asList()` lança
   `UnsupportedOperationException`.
6. Organize o mini projeto 2 em hexágono: domínio (`Product`, portas),
   adaptadores (`ProductHandler`, `JdbcProductRepository`) e composite root no
   `main`. Liste quais classes mudam de pacote e o que fica `package-private`.
7. Adicione SLF4J + Logback num projeto, logue a criação de um pedido com
   placeholder e o erro com stack trace. Teste que `log.debug` não roda quando
   o nível é `info` (mude o nível e compare).
8. Crie um `AppConfig` que lê `DB_URL` de env com default localhost. Teste
   com a env setada e sem a env.
9. Refatore um método com long method + feature envy (o exemplo do
   `notifyCustomer`) e teste que o comportamento não mudou antes/depois.

## Referências

- [SOLID (Wikipedia)](https://en.wikipedia.org/wiki/SOLID) — os cinco princípios com exemplos
- [Dependency Inversion Principle (Uncle Bob)](https://web.archive.org/web/20150905081103/http://blog.cleancoder.com/uncle-bob/2015/07/28/The-Composite-Root.html) — o composite root e o motivo do DI
- [The Clean Architecture (Uncle Bob)](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html) — dependências apontando pro centro
- [Repository (Martin Fowler, P of EAA)](https://martinfowler.com/eaaCatalog/repository.html) — o padrão de acesso a dados
- [Package Structure (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/package/index.html) — criando e nomeando pacotes

## Próximo módulo

**Build e Empacotamento** — Gradle (e um passe de Maven) pra compilar, testar e
gerar o JAR da aplicação.

[→ 22 — Build e Empacotamento](./22-build.md)