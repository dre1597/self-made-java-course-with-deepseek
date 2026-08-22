# Projeto 02: Servidor HTTP puro com JDBC

## Objetivo

Uma API REST em Java sem framework: `HttpServer` do JDK (módulo 19), JDBC +
HikariCP (módulo 17), Jackson 3 (módulo 19) e repository pattern (módulos 20
e 21). O servidor expõe CRUD de produtos com JSON, e o banco é H2 em memória
pra rodar sem instalar nada.

## Estrutura de Arquivos

```
projetos/servidor-http/
    build.gradle.kts
    settings.gradle.kts
    src/main/java/br/com/exemplo/
        Main.java
        product/
            Product.java
            ProductRepository.java
            JdbcProductRepository.java
            ProductHandler.java
            ProductNotFoundException.java
    src/test/java/br/com/exemplo/product/
        ProductHandlerTest.java
        JdbcProductRepositoryTest.java
```

## Como Executar

```bash
cd projetos/servidor-http
./gradlew run
# servidor em http://localhost:8080
```

Testar:

```bash
curl http://localhost:8080/api/products
curl http://localhost:8080/api/products/1
curl -X POST http://localhost:8080/api/products \
     -H "Content-Type: application/json" \
     -d '{"name":"Teclado","price":350.50}'
curl -X DELETE http://localhost:8080/api/products/1
./gradlew test   # roda os testes
```

## Código Completo

### `settings.gradle.kts`

```kotlin
rootProject.name = "servidor-http"
```

### `build.gradle.kts`

```kotlin
plugins {
    java
    application
}

group = "br.com.exemplo"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation("tools.jackson.core:jackson-databind:3.2.0")
    implementation("com.zaxxer:HikariCP:7.1.0")
    runtimeOnly("com.h2database:h2:2.3.232")

    testImplementation(platform("org.junit:junit-bom:5.14.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

application {
    mainClass = "br.com.exemplo.Main"
}

tasks.test {
    useJUnitPlatform()
}
```

### `src/main/java/br/com/exemplo/product/Product.java`

```java
package br.com.exemplo.product;

import java.math.BigDecimal;

public record Product(long id, String name, BigDecimal price) {

    public Product {
        if (id < 0) {
            throw new IllegalArgumentException("id não pode ser negativo");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nome não pode ser vazio");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("preço não pode ser negativo");
        }
    }
}
```

O `id` aceita `0` como "ainda não persistido" (o banco gera o valor real). O
produto que vem da API não informa id; quem cria é o repositório.

### `src/main/java/br/com/exemplo/product/CreateProductRequest.java`

```java
package br.com.exemplo.product;

import java.math.BigDecimal;

public record CreateProductRequest(String name, BigDecimal price) {

    public CreateProductRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nome não pode ser vazio");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("preço não pode ser negativo");
        }
    }

    public Product toProduct() {
        return new Product(0, name, price);
    }
}
```

A entrada da API é um DTO próprio (`CreateProductRequest`), separado do
`Product` que trafega pela aplicação. O JSON do POST não tem id; o `toProduct`
marca `0` pra o repositório gerar.

### `src/main/java/br/com/exemplo/product/ProductRepository.java`

```java
package br.com.exemplo.product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    List<Product> findAll();

    Optional<Product> findById(long id);

    Product save(Product product);

    boolean deleteById(long id);
}
```

### `src/main/java/br/com/exemplo/product/JdbcProductRepository.java`

```java
package br.com.exemplo.product;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcProductRepository implements ProductRepository {

    private final DataSource dataSource;

    public JdbcProductRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Product> findAll() {
        String sql = "SELECT id, name, price FROM products ORDER BY id";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            List<Product> products = new ArrayList<>();
            while (rs.next()) {
                products.add(map(rs));
            }
            return products;
        } catch (SQLException e) {
            throw new DataAccessException("falha ao listar produtos", e);
        }
    }

    @Override
    public Optional<Product> findById(long id) {
        String sql = "SELECT id, name, price FROM products WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("falha ao buscar produto " + id, e);
        }
    }

    @Override
    public Product save(Product product) {
        String sql = "INSERT INTO products (name, price) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, product.name());
            stmt.setBigDecimal(2, product.price());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                long id = keys.getLong(1);
                return new Product(id, product.name(), product.price());
            }
        } catch (SQLException e) {
            throw new DataAccessException("falha ao salvar produto " + product.name(), e);
        }
    }

    @Override
    public boolean deleteById(long id) {
        String sql = "DELETE FROM products WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("falha ao remover produto " + id, e);
        }
    }

    private Product map(ResultSet rs) throws SQLException {
        return new Product(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getBigDecimal("price"));
    }
}
```

O `RETURN_GENERATED_KEYS` recupera o id que o banco gerou no `INSERT`. As
exceções `SQLException` viram `DataAccessException` na borda do repositório
(módulo 21): quem chama não vê detalhe de JDBC.

### `src/main/java/br/com/exemplo/product/DataAccessException.java`

```java
package br.com.exemplo.product;

public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### `src/main/java/br/com/exemplo/product/ProductNotFoundException.java`

```java
package br.com.exemplo.product;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(long id) {
        super("produto não encontrado: " + id);
    }
}
```

### `src/main/java/br/com/exemplo/product/ProductHandler.java`

```java
package br.com.exemplo.product;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ProductHandler implements HttpHandler {

    private final ProductRepository repository;
    private final JsonMapper mapper;

    public ProductHandler(ProductRepository repository, JsonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (ProductNotFoundException e) {
            sendJson(exchange, 404, "{\"error\": \"" + e.getMessage() + "\"}");
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, "{\"error\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\": \"erro interno\"}");
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/api/products")) {
            switch (method) {
                case "GET" -> handleList(exchange);
                case "POST" -> handleCreate(exchange);
                default -> sendJson(exchange, 405, "{\"error\": \"método não permitido\"}");
            }
        } else if (path.matches("/api/products/\\d+")) {
            long id = parseId(path);
            switch (method) {
                case "GET" -> handleFind(exchange, id);
                case "DELETE" -> handleDelete(exchange, id);
                default -> sendJson(exchange, 405, "{\"error\": \"método não permitido\"}");
            }
        } else {
            sendJson(exchange, 404, "{\"error\": \"rota não encontrada\"}");
        }
    }

    private void handleList(HttpExchange exchange) throws IOException {
        List<Product> products = repository.findAll();
        sendJson(exchange, 200, mapper.writeValueAsString(products));
    }

    private void handleFind(HttpExchange exchange, long id) throws IOException {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        sendJson(exchange, 200, mapper.writeValueAsString(product));
    }

    private void handleCreate(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        CreateProductRequest request = mapper.readValue(body, CreateProductRequest.class);
        Product created = repository.save(request.toProduct());
        sendJson(exchange, 201, mapper.writeValueAsString(created));
    }

    private void handleDelete(HttpExchange exchange, long id) throws IOException {
        boolean removed = repository.deleteById(id);
        if (!removed) {
            throw new ProductNotFoundException(id);
        }
        sendJson(exchange, 204, "");
    }

    private long parseId(String path) {
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return Long.parseLong(lastSegment);
    }

    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
```

O handler é puro HTTP: rota, método e JSON na entrada; JSON na saída. A regra
de negócio mora no repositório e nos records.

### `src/main/java/br/com/exemplo/Main.java`

```java
package br.com.exemplo;

import br.com.exemplo.product.JdbcProductRepository;
import br.com.exemplo.product.ProductHandler;
import br.com.exemplo.product.ProductRepository;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.Statement;

public class Main {

    public static void main(String[] args) throws Exception {
        DataSource dataSource = createDataSource();
        createSchema(dataSource);

        ProductRepository repository = new JdbcProductRepository(dataSource);
        JsonMapper mapper = JsonMapper.builder().build();

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/products", new ProductHandler(repository, mapper));
        server.setExecutor(null);
        server.start();
        System.out.println("servidor em http://localhost:8080/api/products");
    }

    private static DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:products;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(10);
        return new HikariDataSource(config);
    }

    private static void createSchema(DataSource dataSource) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE products (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        name VARCHAR(200) NOT NULL,
                        price DECIMAL(12, 2) NOT NULL
                    )
                    """);
        }
    }
}
```

O `DB_CLOSE_DELAY=-1` mantém o H2 vivo enquanto a JVM rodar. É o composite
root (módulo 21): o `main` monta banco, repositório e handler.

## Testes incluídos

### `src/test/java/br/com/exemplo/product/ProductHandlerTest.java`

O teste do handler usa um repositório fake em memória (sem Mockito): o handler
só precisa do contrato, e um `InMemoryProductRepository` na pasta de teste
deixa o comportamento explícito.

```java
package br.com.exemplo.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class ProductHandlerTest {

    private ProductHandler handler;
    private InMemoryProductRepository repository;
    private JsonMapper mapper;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProductRepository();
        mapper = JsonMapper.builder().build();
        handler = new ProductHandler(repository, mapper);
    }

    @Test
    @DisplayName("GET /api/products devolve a lista em JSON")
    void listProducts() throws Exception {
        repository.save(new Product(1, "Teclado", new BigDecimal("350.50")));

        Response response = get("/api/products");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json()).contains("Teclado", "350.50");
    }

    @Test
    @DisplayName("GET /api/products/1 devolve o produto")
    void findProduct() throws Exception {
        repository.save(new Product(1, "Mouse", new BigDecimal("89.90")));

        Response response = get("/api/products/1");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json()).contains("Mouse");
    }

    @Test
    @DisplayName("GET de produto inexistente devolve 404")
    void findMissingProduct() throws Exception {
        Response response = get("/api/products/99");

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.json()).contains("não encontrado");
    }

    @Test
    @DisplayName("POST cria produto e devolve 201 com o id gerado")
    void createProduct() throws Exception {
        Response response = post("/api/products", "{\"name\":\"Monitor\",\"price\":\"1200.00\"}");

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.json()).contains("Monitor", "1");
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("POST com corpo inválido devolve 400")
    void createInvalidProduct() throws Exception {
        Response response = post("/api/products", "{\"name\":\"\",\"price\":\"-5\"}");

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("DELETE de produto existente devolve 204")
    void deleteProduct() throws Exception {
        repository.save(new Product(1, "Teclado", new BigDecimal("350.50")));

        Response response = delete("/api/products/1");

        assertThat(response.status()).isEqualTo(204);
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("rota desconhecida devolve 404")
    void unknownRoute() throws Exception {
        Response response = get("/api/outro");

        assertThat(response.status()).isEqualTo(404);
    }

    // --- helpers ---

    private Response get(String path) throws Exception {
        return send("GET", path, "");
    }

    private Response post(String path, String body) throws Exception {
        return send("POST", path, body);
    }

    private Response delete(String path) throws Exception {
        return send("DELETE", path, "");
    }

    private Response send(String method, String path, String body) throws Exception {
        FakeHttpExchange exchange = new FakeHttpExchange(method, path, body);
        handler.handle(exchange);
        return new Response(exchange.status, new String(exchange.responseBody, java.nio.charset.StandardCharsets.UTF_8));
    }

    private record Response(int status, String json) {}

    private static final class InMemoryProductRepository implements ProductRepository {
        private final List<Product> products = new ArrayList<>();
        private final AtomicLong nextId = new AtomicLong(1);

        @Override
        public List<Product> findAll() {
            return List.copyOf(products);
        }

        @Override
        public Optional<Product> findById(long id) {
            return products.stream()
                    .filter(p -> p.id() == id)
                    .findFirst();
        }

        @Override
        public Product save(Product product) {
            Product created = new Product(nextId.getAndIncrement(), product.name(), product.price());
            products.add(created);
            return created;
        }

        @Override
        public boolean deleteById(long id) {
            return products.removeIf(p -> p.id() == id);
        }
    }
}
```

O teste monta um `FakeHttpExchange` (implementação mínima de `HttpExchange`)
pra chamar o handler sem socket. O `HttpExchange` é abstrato, e só os métodos
que o handler usa precisam ser reais:

```java
package br.com.exemplo.product;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;

class FakeHttpExchange extends HttpExchange {

    final String method;
    final URI uri;
    final Headers responseHeaders = new Headers();
    int status;
    byte[] responseBody = new byte[0];

    FakeHttpExchange(String method, String path, String body) {
        this.method = method;
        this.uri = URI.create("http://localhost:8080" + path);
        this.requestBody = new ByteArrayInputStream(body.getBytes());
    }

    private final InputStream requestBody;

    @Override
    public InputStream getRequestBody() {
        return requestBody;
    }

    @Override
    public Headers getResponseHeaders() {
        return responseHeaders;
    }

    @Override
    public void sendResponseHeaders(int status, long length) {
        this.status = status;
    }

    @Override
    public OutputStream getResponseBody() {
        return new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                responseBody = toByteArray();
                super.close();
            }
        };
    }

    @Override
    public String getRequestMethod() {
        return method;
    }

    @Override
    public URI getRequestURI() {
        return uri;
    }

    // métodos não usados pelo handler; devolvem default pra compilar
    @Override
    public void close() {}
    @Override
    public Object getAttribute(String name) { return null; }
    @Override
    public void setAttribute(String name, Object value) {}
    @Override
    public void setStreams(InputStream i, OutputStream o) {}
    @Override
    public InetSocketAddress getRemoteAddress() { return null; }
    @Override
    public int getResponseCode() { return 0; }
    @Override
    public InetSocketAddress getLocalAddress() { return null; }
    @Override
    public String getProtocol() { return "HTTP/1.1"; }
    @Override
    public HttpContext getHttpContext() { return null; }
    @Override
    public HttpPrincipal getPrincipal() { return null; }
}
```

### `src/test/java/br/com/exemplo/product/JdbcProductRepositoryTest.java`

O teste do repositório roda contra o H2 em memória (teste de integração,
módulo 18):

```java
package br.com.exemplo.product;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcProductRepositoryTest {

    private HikariDataSource dataSource;
    private JdbcProductRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new HikariDataSource(hikariConfig());
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE products (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        name VARCHAR(200) NOT NULL,
                        price DECIMAL(12, 2) NOT NULL
                    )
                    """);
        }
        repository = new JdbcProductRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
    }

    private HikariConfig hikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        return config;
    }

    @Test
    @DisplayName("save gera id, findAll devolve ordenado")
    void saveAndFindAll() {
        Product teclado = repository.save(new Product(0, "Teclado", new BigDecimal("350.50")));
        Product mouse = repository.save(new Product(0, "Mouse", new BigDecimal("89.90")));

        assertThat(teclado.id()).isPositive();
        assertThat(mouse.id()).isGreaterThan(teclado.id());
        assertThat(repository.findAll())
                .extracting(Product::name)
                .containsExactly("Teclado", "Mouse");
    }

    @Test
    @DisplayName("findById acha e devolve empty pra id inexistente")
    void findById() {
        Product saved = repository.save(new Product(0, "Monitor", new BigDecimal("1200.00")));

        assertThat(repository.findById(saved.id())).hasValue(saved);
        assertThat(repository.findById(99L)).isEmpty();
    }

    @Test
    @DisplayName("deleteById remove e devolve false pra id inexistente")
    void deleteById() {
        Product saved = repository.save(new Product(0, "Cabo", new BigDecimal("15.00")));

        assertThat(repository.deleteById(saved.id())).isTrue();
        assertThat(repository.findAll()).isEmpty();
        assertThat(repository.deleteById(saved.id())).isFalse();
    }

    @Test
    @DisplayName("produto inválido é rejeitado antes de tocar o banco")
    void rejectsInvalidProduct() {
        assertThatThrownBy(() -> repository.save(new Product(0, "", new BigDecimal("10"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("lista vazia devolve lista vazia")
    void emptyTable() {
        assertThat(repository.findAll()).isEmpty();
    }
}
```

## Desafios Extras

### 1. `PUT` de atualização
Adicione `Product update(long id, Product product)` no repositório e a rota
`PUT /api/products/{id}` no handler. Teste o 200, o 404 (produto que não
existe) e o 400 (body inválido).

### 2. Concorrência com virtual threads
No `HttpServer.create`, passe um `ExecutorService` de virtual threads
(módulo 16) via `server.setExecutor(...)` e prove, com um teste que dispara
100 criações em paralelo, que nenhum id repete.

### 3. Migrar pra PostgreSQL
Troque a URL do Hikari pelo `jdbc:postgresql://localhost:5432/app`, adicione
`org.postgresql:postgresql` e rode o teste de integração contra o banco real.
O que muda no código Java? Nada além da URL e do driver.

## Conceitos Aplicados

- `HttpServer` do JDK com `HttpHandler` por rota
- JDBC com `PreparedStatement` e `RETURN_GENERATED_KEYS`
- HikariCP como connection pool
- Jackson 3 (`tools.jackson`) pra serializar/deserializar JSON
- Repository pattern: interface no domínio, implementação JDBC escondida
- Exceções próprias (`ProductNotFoundException`, `DataAccessException`) e
  mapa de status HTTP
- Teste de unitário com `FakeHttpExchange` + teste de integração com H2
- Composite root no `main` montando as dependências