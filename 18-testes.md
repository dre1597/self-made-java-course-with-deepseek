# 18 — Testes Automatizados

Testes de unidade com JUnit 5, asserções fluentes com AssertJ e mocks com
Mockito. A prática de testar no Java: o que testar, como organizar e como
isolar dependências.

## A stack de testes

| Biblioteca | Papel | Equivalente TS |
| ---------- | ----- | -------------- |
| JUnit 5 | framework de testes (`@Test`, lifecycle) | Jest |
| AssertJ | asserções fluentes (`assertThat`) | `expect(...).toBe` |
| Mockito | mocks, spies, captors | `vi.mock` / `jest.mock` |

No Gradle:

```kotlin
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.mockito:mockito-core:5.17.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.17.0")
}
```

## JUnit 5 — anatomia

```java
import static org.junit.jupiter.api.Assertions.*;

class OrderCalculatorTest {

    @Test
    void appliesDiscountForLoyalCustomer() {
        OrderCalculator calculator = new OrderCalculator();

        BigDecimal total = calculator.totalWithDiscount(BigDecimal.valueOf(100), 0.10);

        assertEquals(0, total.compareTo(BigDecimal.valueOf(90)));
    }
}
```

Estrutura:

- A classe de teste termina em `Test`. O padrão `ClassNameTest`.
- Métodos de teste são `@Test`, não retornam nada, nome descreve o
  comportamento em frases (`appliesDiscountForLoyalCustomer`).
- Convenção de nome: `should_`/verbo + condição. `given_when_then` também é
  comum. Use o que a equipe padronizar.

### Ciclo de vida

```java
class OrderRepositoryTest {

    private Database db;

    @BeforeEach
    void setUp() {
        db = new Database();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void findsOrderById() {
        // usa db
    }
}
```

- `@BeforeEach`: roda antes de cada teste (setup).
- `@AfterEach`: depois de cada teste (limpeza).
- `@BeforeAll`/`@AfterAll`: uma vez pra classe toda (estático).
- Cada teste roda num contexto novo. Teste não pode depender de outro.

### `@DisplayName` e `@Nested`

```java
class OrderServiceTest {

    @Nested
    class ConfirmPayment {

        @Test
        @DisplayName("marca pedido como pago quando pagamento confirmado")
        void marksOrderPaid() {
        }
    }

    @Nested
    class CancelOrder {

        @Test
        @DisplayName("lança exceção se o pedido não existe")
        void throwsWhenOrderMissing() {
        }
    }
}
```

`@Nested` agrupa testes por cenário e o relatório lê como árvore.
`@DisplayName` escreve o cenário em texto corrido; o nome do método vira
identificador técnico.

## Testes parametrizados

Um teste que roda pra vários valores de entrada:

```java
@ParameterizedTest
@ValueSource(ints = {0, 1, 5, Integer.MAX_VALUE})
void acceptsNonNegativeTotals(int total) {
    assertThat(calculator.totalWithDiscount(BigDecimal.valueOf(total), 0.10))
            .isNotNegative();
}
```

Fontes de dados:

```java
@ParameterizedTest
@CsvSource({
        "100, 0.10, 90",
        "0, 0.10, 0",
        "100, 1.00, 0"
})
void appliesDiscountCorrectly(double total, double discount, double expected) {
    assertThat(calculator.totalWithDiscount(BigDecimal.valueOf(total), BigDecimal.valueOf(discount)))
            .isEqualByComparingTo(BigDecimal.valueOf(expected));
}
```

```java
@ParameterizedTest
@MethodSource("invalidInputs")
void rejectsInvalidInput(BigDecimal total, double discount) {
    assertThatThrownBy(() -> calculator.totalWithDiscount(total, discount))
            .isInstanceOf(IllegalArgumentException.class);
}

static Stream<Arguments> invalidInputs() {
    return Stream.of(
            Arguments.of(null, 0.10),
            Arguments.of(BigDecimal.TEN, -0.1),
            Arguments.of(BigDecimal.TEN, 1.5)
    );
}
```

- `@ValueSource`: valores simples do mesmo tipo.
- `@CsvSource`: várias linhas, cada uma vira uma execução do teste.
- `@MethodSource`: casos estruturados, o mais flexível. O método estático
  devolve `Stream<Arguments>`.

O teste parametrizado substitui N métodos `@Test` quase idênticos. O relatório
mostra cada caso separado, e o caso que falha aparece sozinho.

## `@Timeout`

Limite de execução. Um teste que trava (deadlock, chamada externa que não
retorna) falha em vez de pendurar a suíte:

```java
@Test
@Timeout(2)
void doesNotBlockForever() throws Exception {
    client.fetchWithTimeout();
}
```

`@Timeout` por método ou `@Timeout(value = 5, unit = TimeUnit.SECONDS)`.

## Asserções AssertJ

O AssertJ encadeia asserções legíveis:

```java
import static org.assertj.core.api.Assertions.*;

assertThat(result).isEqualTo(90);
assertThat(result).isGreaterThan(0);
assertThat(users).hasSize(3);
assertThat(users).extracting(User::name).containsExactly("ana", "bia", "caio");
assertThat(users).filteredOn(u -> u.status() == ACTIVE).isNotEmpty();
assertThat(opt).contains("valor");
assertThat(opt).isEmpty();
assertThatThrownBy(() -> calculator.totalWithDiscount(null, 0.10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("total");
```

Vantagens sobre o `assertEquals` do JUnit:

- Erro de asserção descreve a diferença real ("expected 90 but was 100").
- Encadeia e lê como frase.
- `assertThatThrownBy` testa exceção de forma declarativa.

## Testando exceções

```java
@Test
void rejectsNullTotal() {
    OrderCalculator calculator = new OrderCalculator();

    assertThatThrownBy(() -> calculator.totalWithDiscount(null, 0.10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("total não pode ser nulo");
}
```

Compare com o jeito antigo `@Test(expected = ...)` ou `try/catch`:
`assertThatThrownBy` declara o que lança e deixa você inspecionar a mensagem e
a causa.

Além da mensagem, dá pra assertar a **causa** (módulo 14) e o tipo exato:

```java
assertThatThrownBy(() -> service.transfer(fromId, toId, amount))
        .isInstanceOf(InsufficientFundsException.class)
        .hasMessageContaining("saldo insuficiente")
        .hasCauseInstanceOf(SQLException.class);
```

E pra garantir que **não** lança:

```java
assertThatCode(() -> service.transfer(fromId, toId, amount))
        .doesNotThrowAnyException();
```

`assertThatCode` não lança a exceção da assertiva no meio do teste; ele captura
e permite a assertiva `doesNotThrowAnyException`. Útil quando a ausência de
exceção é o próprio comportamento esperado.

## Mockito — isolando dependências

O teste de unidade isola a classe sob teste. As dependências externas (banco,
API, fila) viram **mocks**:

```java
class OrderServiceTest {

    @Mock
    private OrderRepository repository;

    @InjectMocks
    private OrderService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void marksOrderAsPaidWhenPaymentConfirmed() {
        Order order = new Order(42L, CREATED);
        when(repository.findById(42L)).thenReturn(Optional.of(order));

        service.confirmPayment(42L);

        verify(repository).save(order);
        assertThat(order.status()).isEqualTo(PAID);
    }
}
```

### Stubbing: definir comportamento

```java
when(repository.findById(42L)).thenReturn(Optional.of(order));
when(repository.findByName("x")).thenThrow(new NotFoundException());
```

### Verificação: provar interação

```java
verify(repository).save(order);                       // salvou uma vez
verify(repository, never()).delete(order);            // nunca deletou
verify(repository, times(2)).save(any());             // salvou duas vezes
```

### Argument captors

Pra inspecionar o que foi passado pro mock:

```java
@Captor
private ArgumentCaptor<Order> orderCaptor;

// no teste
verify(repository).save(orderCaptor.capture());
Order captured = orderCaptor.getValue();
assertThat(captured.total()).isEqualTo(BigDecimal.valueOf(100));
```

### Spies

O spy embrulha um objeto real: chama o método de verdade, mas permite
sobrescrever:

```java
List<String> list = new ArrayList<>();
List<String> spy = spy(list);

spy.add("a");                 // chama o ArrayList real
when(spy.size()).thenReturn(100);   // sobrescreve só o size

assertThat(spy.size()).isEqualTo(100);
```

### DoNothing e void methods

```java
doNothing().when(notificationSender).send(any());
doThrow(new NotificationException()).when(notificationSender).send(any());
```

`doThrow`/`doNothing` são pra métodos `void`, que o `when(...).thenThrow`
não compila.

### Argument matchers

`any()` e amigos cobrem valores que o teste não quer fixar:

```java
when(repository.findById(anyLong())).thenReturn(Optional.of(order));
when(repository.findByName(anyString())).thenReturn(Optional.empty());
when(repository.findByStatus(any(OrderStatus.class))).thenReturn(List.of());

verify(repository).save(any(Order.class));
```

Matchers comuns: `any()`, `anyString()`, `anyInt()`, `anyLong()`,
`any(Clazz.class)`, `eq(valor)`, `contains("parte")`, `startsWith`, `endsWith`,
`isNull()`, `nullable(Clazz.class)`.

Regra: se um argumento usa matcher, **todos** os argumentos da chamada usam.
`findById(42L, any())` não compila; `findById(eq(42L), any())` compila.

### `doAnswer` — comportamento calculado

Quando o stub precisa calcular o retorno a partir do argumento:

```java
when(repository.findById(anyLong()))
        .thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            return Optional.of(new Order(id, CREATED));
        });
```

Útil pra gerar resposta por chamada, simular sequência ou derivar do
argumento. Pra valor fixo, `thenReturn` é mais simples.

### `inOrder` — provar ordem de chamada

```java
InOrder inOrder = inOrder(repository, emailSender);

inOrder.verify(repository).save(order);
inOrder.verify(emailSender).sendConfirmation(order);
```

`inOrder` verifica que as chamadas aconteceram na sequência declarada, não só
que aconteceram. Quando a ordem importa (gravar antes de notificar), é o
caminho.

### `verifyNoMoreInteractions` / `verifyNoInteractions`

```java
verify(repository).save(order);
verifyNoMoreInteractions(repository);   // não pode ter chamado mais nada
```

`verifyNoInteractions` afirma que o mock nem foi usado. Usado com moderação:
verificar demais prende o teste à implementação. O ponto é pegar chamada
acidental (ex.: método chamado duas vezes por engano).

### Mock de `static` (Mockito inline)

Pra método estático, o Mockito precisa do `mockito-inline`:

```kotlin
testImplementation("org.mockito:mockito-inline:5.17.0")
```

```java
try (MockedStatic<LocalDate> mocked = mockStatic(LocalDate.class)) {
    mocked.when(LocalDate::now).thenReturn(LocalDate.of(2026, 8, 22));

    assertThat(service.today()).isEqualTo(LocalDate.of(2026, 8, 22));
}
```

Mock de `static` deve ser raro. Prefira injetar o `Clock` ou passar a data por
parâmetro. Quando o código legado usa `static` direto, o `mockStatic` desbloqueia
o teste sem refatorar tudo.

## BDD: given/when/then com Mockito BDD

Mockito tem modo BDD pra ler como especificação:

```java
import static org.mockito.BDDMockito.*;

class OrderServiceBddTest {

    @Test
    void confirmsOrderWhenStockAvailable() {
        // given
        given(repository.findById(42L)).willReturn(Optional.of(order));

        // when
        service.confirmPayment(42L);

        // then
        then(repository).should().save(order);
    }
}
```

`given`/`willReturn`/`then().should()` é a mesma mecânica com verbo de
especificação. Se a equipe usa BDD, esse é o formato.

## Testes de borda e stress

O que o curso inteiro pede em cada exemplo:

- Valor nulo, vazio, negativo, no limite.
- Lista vazia, só um elemento, elementos repetidos.
- Exceção esperada: o erro certo, a mensagem certa.

```java
@Test
void totalWithDiscountHandlesZero() {
    assertThat(calculator.totalWithDiscount(BigDecimal.ZERO, 0.10))
            .isEqualByComparingTo(BigDecimal.ZERO);
}

@Test
void totalWithDiscountHandlesFullDiscount() {
    assertThat(calculator.totalWithDiscount(BigDecimal.valueOf(100), 1.00))
            .isEqualByComparingTo(BigDecimal.ZERO);
}
```

## Testes de integração

O teste de unidade isola; o teste de integração sobe a pilha real (banco,
HTTP). No módulo de JDBC os exemplos usam um banco de verdade. Em projetos
Gradle:

```kotlin
dependencies {
    testImplementation("org.testcontainers:postgresql:1.20.6")
}
```

O Testcontainers sobe um banco em container Docker pro teste. Separar teste
de unidade (Mockito, sem I/O) de teste de integração (banco real) mantém o
unitário rápido e determinístico.

## Organizando a suíte com tags

`@Tag` separa teste rápido de teste lento:

```java
@Tag("unit")
class OrderCalculatorTest {
}

@Tag("integration")
class OrderRepositoryIntegrationTest {
}
```

No Gradle, roda cada grupo separado:

```bash
# só testes de unidade
./gradlew test --tests "*Unit*" --tests "*Test" -Ptags=unit

# só integração
./gradlew test -PincludeTags=integration
```

Na prática:

- **Unit**: Mockito, sem I/O, roda em segundos. É a maior parte.
- **Integration**: banco real, container, HTTP real. Roda menos, mais devagar.
- **E2E**: o sistema inteiro (depois, no curso com frameworks).

O commit/prod costuma rodar unit sempre; integração em horário ou CI separado.
Assim o feedback do unitário vem rápido e o integração não trava o loop.

## O que NÃO testar

- Código de terceiros (libs, framework). Teste o seu.
- `getters`/`setters` triviais.
- `toString` que só imprime campos.

Teste comportamento: regra de negócio, decisão, cálculo, integração com a
fronteira. Se o teste só repete o código, ele não prova nada.

## Comparação com TypeScript

| Conceito | Java | Jest/Vitest |
| -------- | ---- | ----------- |
| Framework | JUnit 5 | Jest |
| Asserção | AssertJ `assertThat` | `expect` |
| Mock | Mockito `when`/`verify` | `vi.mock`, `jest.spyOn` |
| Setup | `@BeforeEach` | `beforeEach` |
| Teste de exceção | `assertThatThrownBy` | `expect(...).toThrow` |
| Parametrizado | `@ParameterizedTest` + `@CsvSource` | `test.each([...])` |
| Timeout | `@Timeout` | `test(..., 1000)` |

Os dois são parecidos em estrutura. A diferença de costume: no TS o mock é
feito no módulo (`vi.mock('fs')`), no Java é por objeto (injeção da
dependência mockada). A parametrização existe nas duas; o `@CsvSource` do
JUnit e o `test.each` do Vitest fazem o mesmo papel.

## Exercícios

1. Escreva testes pra `OrderCalculator.totalWithDiscount` cobrindo: total
   nulo (exceção), desconto negativo (exceção), desconto 0, desconto 1,
   total 0, desconto 0.5. Use AssertJ.
2. Refaça o exercício 1 como um único teste parametrizado com `@CsvSource`
   pros casos felizes e `@MethodSource` pros casos de exceção. Compare com a
   versão com métodos `@Test` separados.
3. Teste um `OrderService.confirmPayment(orderId)` com Mockito: repository
   mockado, e o teste verifica `save` chamado com o status `PAID`. Teste
   também o caso de pedido inexistente (lança exceção e `save` não é chamado).
4. Use `ArgumentCaptor` pra capturar o `Order` salvo e assertar o total
   calculado dentro dele. Depois use `inOrder` pra provar que `save` roda
   antes de `emailSender.sendConfirmation`.
5. Escreva um teste de borda pra `max(List<Integer>)`: lista vazia (decida o
   comportamento, exceção ou `Optional`), um elemento, `Integer.MAX_VALUE`,
   elementos negativos, `null` no meio.
6. Use `@Timeout` num teste que chama um método que pode travar (ex.:
   `CompletableFuture` que nunca completa). Verifique que o teste falha por
   timeout em vez de pendurar a suíte.

## Referências

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/) — anotações, ciclo de vida e assertions
- [AssertJ User Guide](https://assertj.github.io/doc/) — asserções fluentes e os matchers disponíveis
- [Mockito Docs](https://site.mockito.org/) — mocks, spies, captors e BDD
- [Testcontainers](https://java.testcontainers.org/) — banco de verdade em container pra teste de integração

## Próximo módulo

**HTTP Client e JSON** — `java.net.http`, `HttpClient`, `BodyHandlers` e a
serialização com Jackson.

[→ 19 — HTTP Client e JSON](./19-http-client.md)