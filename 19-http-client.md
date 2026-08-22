# 19 — HTTP Client e JSON

Chamar API externa e trocar JSON sem framework. O JDK tem um cliente HTTP
moderno desde o 11 (`java.net.http`), e o Jackson resolve a serialização. Junta
os dois e você tem o equivalente do `fetch` + `JSON.parse` do TS na JDK pura.

## `HttpClient` (JDK 11)

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

HttpClient client = HttpClient.newHttpClient();
```

O `HttpClient` é imutável e reutilizável: crie um, use pra várias chamadas. A
configuração (timeout, proxy, versão do protocolo) fica no builder:

```java
HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
```

- `connectTimeout`: quanto tempo espera pra abrir conexão (não cobre o tempo de
  resposta do servidor; isso é request timeout, que não existe pronto no
  HttpClient, você controla com `CompletableFuture.orTimeout` ou com um
  `ExecutorService`).
- `followRedirects`: por padrão é `NEVER`. O `NORMAL` segue todos os redirects
  exceto de HTTPS pra HTTP (não rebaixa pra conexão insegura).
- O HTTP/2 é o preferido por padrão; se o servidor não suporta, cai pra
  HTTP/1.1 automaticamente. HTTP/3 só chegou no JDK 26 (JEP 517) e continua
  opt-in.

## Montando a request

```java
HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.example.com/products"))
        .timeout(Duration.ofSeconds(30))
        .header("Accept", "application/json")
        .GET()
        .build();
```

Métodos com corpo:

```java
HttpRequest post = HttpRequest.newBuilder()
        .uri(URI.create("https://api.example.com/orders"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
        .build();
```

`BodyPublishers` converte o corpo em bytes: `ofString`, `ofByteArray`,
`ofFile`, `ofInputStream`. Pra JSON em texto, `ofString` resolve.

## Autenticação

API que exige auth manda o token no header. Dois esquemas comuns:

**Basic** (usuário e senha base64):

```java
String credentials = "user:password";
String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(UTF_8));

HttpRequest request = HttpRequest.newBuilder()
        .uri(uri)
        .header("Authorization", "Basic " + encoded)
        .GET()
        .build();
```

**Bearer** (token, o padrão em API moderna):

```java
HttpRequest request = HttpRequest.newBuilder()
        .uri(uri)
        .header("Authorization", "Bearer " + accessToken)
        .GET()
        .build();
```

O token fica no header de cada request, não em cookie. Pra montar sem repetir
o header em cada chamada, um helper:

```java
HttpRequest.Builder authenticated(String token, HttpRequest.Builder builder) {
    return builder.header("Authorization", "Bearer " + token);
}
```

O segredo do token nunca vai pra log, pra query string nem pra código
versionado. Vem de variável de ambiente ou de um secret manager (módulo 24).

## Enviando e lendo a resposta

```java
HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
```

- `send` bloqueia a thread até a resposta. 
- `BodyHandlers` decide o que fazer com o corpo: `ofString` (texto),
  `ofByteArray`, `ofFile(path)` (download direto), `ofInputStream` (stream),
  `discarding` (só status, ignora corpo).

```java
int status = response.statusCode();
String body = response.body();
HttpHeaders headers = response.headers();
```

O status code não lança exceção automática. `200`, `404`, `500` chegam do mesmo
jeito; você decide o que fazer com cada código.

## Resposta em JSON

O `HttpClient` entrega texto. Quem vira JSON é você, com o Jackson:

```java
import tools.jackson.databind.json.JsonMapper;

JsonMapper mapper = JsonMapper.builder().build();

Product product = mapper.readValue(response.body(), Product.class);
```

## Jackson 3: o `ObjectMapper` ficou imutável

O Jackson 2 (pacote `com.fasterxml.jackson`) reinou por 15 anos. O Jackson 3
(estável desde out/2025) trocou os pacotes pra `tools.jackson` e tornou o
mapper **imutável**: configurar virou coisa de builder. A maioria dos tutoriais
da web ainda mostra Jackson 2; o código do curso usa o 3.

```java
// Jackson 3: imutável, config via builder
JsonMapper mapper = JsonMapper.builder()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();
```

No Jackson 2 você fazia `mapper.configure(...)` depois de criar. No 3, tudo no
builder. E o `JsonMapper` é thread-safe: a instância compartilhada sobrevive a
várias threads sem config nova.

### Serializar e desserializar

```java
public record Product(long id, String name, BigDecimal price) {}
```

```java
// objeto -> JSON
String json = mapper.writeValueAsString(product);

// JSON -> objeto
Product product = mapper.readValue(json, Product.class);
```

O Jackson 3 mapeia records de graça (por componentes do record). `BigDecimal`,
`List`, `Map`, enums, `LocalDate`/`LocalDateTime` (via `jackson-datatype-jsr310`,
que no 3 foi fundida no core) funcionam direto.

```java
String json = """
    {"id": 42, "name": "Teclado", "price": 350.50}""";

Product product = mapper.readValue(json, Product.class);
// product.id() == 42
// product.name().equals("Teclado")
```

### Configurações que importam

```java
JsonMapper mapper = JsonMapper.builder()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .build();
```

- `WRITE_DATES_AS_TIMESTAMPS` desligado: datas saem como string ISO, não como
  número epoch.
- `FAIL_ON_UNKNOWN_PROPERTIES` desligado: campo que a API devolveu e você não
  mapeou não derruba a desserialização. Útil quando a API evolui mais rápido
  que o seu record.
- `INDENT_OUTPUT`: JSON bonito pra log, resposta de API.

Se a API devolve array no topo:

```java
List<Product> products = mapper.readValue(
        response.body(),
        mapper.getTypeFactory().constructCollectionType(List.class, Product.class));
```

(Em Jackson 2 era `TypeReference`. No 3 o `TypeFactory` continua, e o caminho
mais simples é esse.)

### Anotações que você usa todo dia

O nome do campo Java nem sempre bate com o JSON da API. As anotações do
Jackson 3 (`tools.jackson.annotation`) ajustam isso:

```java
import tools.jackson.annotation.JsonFormat;
import tools.jackson.annotation.JsonIgnore;
import tools.jackson.annotation.JsonProperty;

public record Order(
        @JsonProperty("order_id") long orderId,
        String status,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        @JsonIgnore boolean internal) {}
```

- `@JsonProperty("order_id")`: o campo Java `orderId` vira `order_id` no JSON
  (e aceita `order_id` na entrada).
- `@JsonFormat(pattern = ...)`: formata a data no padrão que a API espera.
- `@JsonIgnore`: o campo não vai pro JSON (senha, dado interno).
- O `@JsonFormat` pra `LocalDateTime` usa o padrão que você define; sem ele,
  sai no formato ISO do `jackson-datatype-jsr310`.

Pra desserializar um campo que some e não derrubar tudo, o
`FAIL_ON_UNKNOWN_PROPERTIES` desligado já cuida. O `@JsonIgnore` é pro
sentido oposto: você não quer nem serializar.

### Polimorfismo: sealed class + `@JsonSubTypes`

Quando a resposta pode ser de vários tipos (uma `Notification` que é `Email`,
`Sms` ou `Push`), o Jackson precisa saber qual classe instanciar. Com sealed,
o Jackson 3 detecta os subtipos automaticamente (módulo 10):

```java
@JsonTypeInfo(use = Id.DEDUCTION)
public sealed interface Notification permits EmailNotification, SmsNotification {}

public record EmailNotification(String to, String subject) implements Notification {}
public record SmsNotification(String phone, String message) implements Notification {}
```

O `@JsonTypeInfo(use = Id.DEDUCTION)` deduz o tipo pelo conjunto de campos
presentes. Alternativa explícita, com um campo `type` no JSON:

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = EmailNotification.class, name = "email"),
    @JsonSubTypes.Type(value = SmsNotification.class, name = "sms")
})
public sealed interface Notification permits EmailNotification, SmsNotification {}
```

```java
String json = """
    {"type": "email", "to": "a@b.com", "subject": "oi"}""";

Notification notification = mapper.readValue(json, Notification.class);
// instância de EmailNotification
```

O sealed permite o `@JsonSubTypes` refletir a hierarquia fechada: se adicionar
um subtipo novo no `permits`, o Jackson acusa que falta a entrada no
`@JsonSubTypes` (em vez de falhar silencioso em runtime).

### JSON malformado

O JSON que não obedece o record lança `JacksonException` (checked no Jackson
2, unchecked `JacksonException` no 3). Duas coisas pra lembrar:

```java
// campo faltando ou tipo errado -> exceção
// tipo primitivo null -> exceção (long não aceita null)
Product product = mapper.readValue(json, Product.class);
```

Pra validar antes de usar, o `readValue` já lança; você decide o que fazer no
`catch`. Num `map` de stream, envolva num runtime (módulo 13) ou trate no
fim do pipeline. O campo `price` como `BigDecimal` evita o erro clássico de
ponto flutuante (módulo 03).

## Tratando erro

```java
public Optional<Product> findById(long id) {
    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/products/" + id))
            .GET()
            .build();
    try {
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return Optional.of(mapper.readValue(response.body(), Product.class));
        }
        return Optional.empty();
    } catch (IOException e) {
        throw new UncheckedIOException("falha ao consultar produto " + id, e);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("interrompido ao consultar produto " + id, e);
    }
}
```

`send` lança `IOException` (rede) e `InterruptedException` (thread
interrompida). Restaurar o interrupt flag antes de relançar é o que se espera
de código que respeita cancelamento (módulo 16).

O JSON inválido vira exceção do Jackson (`JacksonException`, unchecked).

## Assíncrono com `sendAsync`

`sendAsync` devolve um `CompletableFuture<HttpResponse<T>>` imediatamente; você
encadeia o que quiser e o resultado chega quando puder (módulo 16):

```java
CompletableFuture<List<Product>> productsFuture = client.sendAsync(
        request,
        BodyHandlers.ofString())
    .thenApply(HttpResponse::body)
    .thenApply(body -> readProducts(body));
```

Várias chamadas em paralelo:

```java
List<CompletableFuture<HttpResponse<String>>> futures = urls.stream()
        .map(url -> client.sendAsync(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                BodyHandlers.ofString()))
        .toList();

List<String> bodies = futures.stream()
        .map(CompletableFuture::join)   // junta tudo, na ordem da lista
        .map(HttpResponse::body)
        .toList();
```

`join()` bloqueia até o future completar. A mágica: os `sendAsync` rodam
paralelos, o join no final só espera. O HttpClient usa o executor de
virtual threads por padrão no JDK 21+, então N requisições paralelas não
estouram o pool.

### Timeout do request

O `connectTimeout` cobre só a conexão. O tempo total do request (conectar +
enviar + esperar a resposta) você controla com `CompletableFuture.orTimeout`:

```java
CompletableFuture<HttpResponse<String>> responseFuture = client.sendAsync(
        request,
        BodyHandlers.ofString())
    .orTimeout(Duration.ofSeconds(10));

try {
    HttpResponse<String> response = responseFuture.join();
    // ...
} catch (CompletionException e) {
    // TimeoutException na causa se estourou os 10s
}
```

`orTimeout` cancela o future e devolve ele mesmo (pra encadear mais
operações). O `join` então lança `CompletionException` com `TimeoutException`
na causa. Sem isso, uma API lenta segura a thread indefinidamente.

Se você prefere `send` síncrono com timeout, não tem `orTimeout` (não há
future). A saída: use `sendAsync` + `orTimeout` + `join`, que dá o mesmo
bloqueio com limite de tempo. É o padrão pra chamada de API com prazo.

## Download de arquivo

```java
HttpResponse<Path> response = client.send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofFile(Path.of("relatorio.pdf")));
```

O `ofFile` grava o corpo direto no disco, sem carregar em memória.

## `HttpServer` do JDK — o par do cliente

O JDK também tem um servidor HTTP simples (`com.sun.net.httpserver`, módulo
`jdk.httpserver`). Nada de framework: você registra handlers por caminho e
responde na mão. É o que o mini projeto 2 usa:

```java
import com.sun.net.httpserver.HttpServer;

HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
server.createContext("/api/products", exchange -> {
    String body = "{\"message\": \"olá\"}";
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, body.getBytes().length);
    try (var os = exchange.getResponseBody()) {
        os.write(body.getBytes());
    }
});
server.start();
```

```java
HttpResponse<String> response = client.send(
        HttpRequest.newBuilder(URI.create("http://localhost:8080/api/products")).GET().build(),
        BodyHandlers.ofString());
```

Roda em qualquer `main`, sem war, sem container. Serve pro mini projeto e pra
testar o `HttpClient` localmente.

## WebSocket: o canal bidirecional do mesmo cliente

O `HttpClient` também fala **WebSocket** (`java.net.http.WebSocket`): uma
conexão persistente onde cliente e servidor trocam mensagens nos dois
sentidos, sem o ciclo request/response do HTTP. Caso de uso: chat, feed de
notificações em tempo real, streaming de eventos.

```java
public class PriceListener implements WebSocket.Listener {

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        System.out.println("preço recebido: " + data);
        webSocket.request(1);    // pronto pra próxima mensagem
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("conexão fechada: " + statusCode + " " + reason);
        return null;
    }
}
```

```java
WebSocket socket = client.newWebSocketBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .buildAsync(URI.create("wss://api.exemplo.com/prices"), new PriceListener())
        .join();

socket.sendText("{\"symbol\": \"PETR4\"}", true);
```

Pontos que pegam:

- `onText` recebe a mensagem em pedaços (`last` diz se é o último). Mensagem
  curta chega inteira numa chamada.
- `webSocket.request(1)` é o backpressure: sem ele, o cliente para de
  receber. O servidor envia só quando você pede.
- `sendText` e `sendBinary` enviam; o `true` no fim marca mensagem completa.
- `onClose` avisa o encerramento; pra derrubar de propósito,
  `socket.sendClose(WebSocket.NORMAL_CLOSURE, "ok")`.

O URI é `ws://` (inseguro) ou `wss://` (TLS). O listener roda no executor do
`HttpClient`; não bloqueie dentro dele, trate a mensagem e retorne.

## Comparação com TypeScript

| Conceito | Java | TypeScript/Node |
| -------- | ---- | --------------- |
| Cliente HTTP | `HttpClient` (JDK 11) | `fetch` / `axios` |
| Enviar GET | `send(request, ofString())` | `fetch(url)` |
| Corpo JSON | `BodyPublishers.ofString` | `JSON.stringify` |
| Parsear JSON | Jackson `readValue` | `JSON.parse` / `json()` |
| Assíncrono | `sendAsync` → `CompletableFuture` | `await` / promise |
| Request body streaming | `BodyPublishers` | `ReadableStream` |
| Erro de rede | `IOException` (checked) | `fetch` rejeita promise |
| WebSocket | `java.net.http.WebSocket` | `ws` / `WebSocket` do browser |
| Auth token | header `Authorization` manual | `headers` do fetch / interceptors |

Diferença central: no TS o `fetch` já resolve JSON com `res.json()`. No Java,
`HttpClient` entrega texto e o Jackson converte. São duas responsabilidades
separadas, e isso é por design: o cliente HTTP não sabe nada sobre JSON.

O WebSocket existe nos dois lados; no TS ele é outro objeto (`ws`), no Java
ele nasce do mesmo `HttpClient` (`newWebSocketBuilder`).

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| `HttpClient` (HTTP/1.1 e HTTP/2) | JDK 11 | Permanente |
| WebSocket | JDK 11 | Permanente |
| HTTP/3 (JEP 517) | JDK 26 | Permanente (opt-in) |
| Jackson 3 (`tools.jackson`, mapper imutável) | out/2025 | Estável, recomendado p/ projeto novo |

## Exercícios

1. Escreva um `ProductApiClient` com `findById(long)` que devolve
   `Optional<Product>` e `listAll()` que devolve `List<Product>`, usando
   `HttpClient` + `JsonMapper`. Teste contra um `HttpServer` local que
   responde JSON (monta o server num teste, para de usar API real). Cubra:
   status 404, corpo JSON inválido, campo desconhecido no JSON.
2. Adicione `create(Product)` que faz POST com o produto em JSON e devolve o
   status code. Teste o POST com corpo JSON correto e com body malformado.
3. Escreva `fetchAll(List<URI>)` que busca várias URLs em paralelo com
   `sendAsync` e devolve `List<String>` dos corpos na mesma ordem da entrada.
   Teste com lista vazia, uma URL que falha e 10 URLs.
4. Escreva `download(URI, Path)` que baixa um arquivo com `BodyHandlers.ofFile`
   e devolve o `Path` gravado. Teste o conteúdo gravado e o que acontece com
   URL que devolve 404.
5. Configure um `JsonMapper` com `INDENT_OUTPUT`, serialize um `Product` com
   `LocalDate`, e valide que a data saiu em ISO e o JSON formatado. Depois
   desserialize o mesmo JSON de volta e compare.
6. Adicione `withTimeout(URI, Duration)` que usa `sendAsync` + `orTimeout` e
   devolve `Optional<HttpResponse<String>>` (vazio se estourou o tempo). Teste
   com um `HttpServer` que dorme mais que o timeout.
7. Crie um `Notification` sealed (`Email`, `Sms`) com `@JsonSubTypes`, serialize
   as duas e desserialize de volta validando a instância certa por `type`.
8. Escreva um `ProductApiClient` que envia `Authorization: Bearer` num helper e
   teste contra um `HttpServer` local que valida o header e responde 401 sem
   token, 200 com token.

## Referências

- [HttpClient (Java API docs)](https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpClient.html) — builder, `send`/`sendAsync`, configurações
- [Introduction to the Java HTTP Client (OpenJDK)](https://openjdk.org/groups/net/httpclient/intro.html) — modelo de request/response, BodyHandlers e BodyPublishers
- [Java HTTP Client: Examples and Recipes (OpenJDK)](https://openjdk.org/groups/net/httpclient/recipes.html) — GET/POST assíncrono, download e JSON com Jackson
- [JEP 517 — HTTP/3 for the HTTP Client API](https://openjdk.org/jeps/517) — HTTP/3 no HttpClient (JDK 26)
- [Jackson 3 migration guide (GitHub)](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md) — pacote `tools.jackson`, mapper imutável, o que mudou do 2 pro 3
- [JsonMapper (jackson-databind 3 API)](https://javadoc.io/doc/tools.jackson.core/jackson-databind/latest/tools.jackson.databind/tools/jackson/databind/json/JsonMapper.html) — o mapper JSON do Jackson 3
- [HttpServer (Java API docs)](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html) — o servidor HTTP embutido do JDK

## Próximo módulo

**Design Patterns** — os padrões de projeto no Java moderno, com records,
sealed classes e lambdas.

[→ 20 — Design Patterns](./20-design-patterns.md)