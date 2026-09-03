# 17 — JDBC e Banco de Dados

JDBC é o acesso a banco relacional puro do Java: conectar, executar SQL e
mapear resultado. Sem framework, sem ORM. Depois, Spring Boot e JPA/Hibernate
embrulham tudo isso, mas o JDBC é a fundação e o que o ORM esconde.

## O que é JDBC

JDBC é uma API da JDK (`java.sql`) pra banco relacional. Cada banco fornece um
**driver** que implementa a API: PostgreSQL, MySQL, SQLite, etc. O driver é a
biblioteca que você adiciona no build.

A dependência no Gradle (detalhe no módulo 22):

```kotlin
dependencies {
    implementation("org.postgresql:postgresql:42.7.11")
}
```

O que você usa em código: `DriverManager` (ou um pool de conexões) pra
conectar, `Connection`, `PreparedStatement`, `ResultSet`.

## Conexão

```java
String url = "jdbc:postgresql://localhost:5432/app";
String user = "app";
String password = "secret";

try (Connection conn = DriverManager.getConnection(url, user, password)) {
    // usa a conexão
} // close() libera
```

O `try-with-resources` fecha a conexão automaticamente. `Connection` é
`AutoCloseable`, assim como `Statement` e `ResultSet`.

A URL JDBC tem formato `jdbc:banco:host:porta/banco`. Cada banco tem o seu.

### Connection pool (HikariCP)

Criar conexão é caro: abre socket, autentica e o banco prepara uma sessão.
Fazer isso a cada requisição derruba qualquer servidor. A solução é o
**connection pool**: um punhado de conexões abertas que a aplicação empresta
e devolve.

O **HikariCP** é a biblioteca de pool mais usada do ecossistema Java — é o
pool padrão do Spring Boot. Ele implementa a interface `javax.sql.DataSource`
(o jeito padrão do JDBC de obter conexão) e por cima faz o pooling: mantém as
conexões abertas, empresta no `getConnection()` e devolve no `close()`. O
`close()` do try-with-resources não fecha a conexão de verdade; devolve pro
pool.

A dependência:

```kotlin
implementation("com.zaxxer:HikariCP:7.1.0")
```

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl(url);
config.setUsername(user);
config.setPassword(password);
config.setMaximumPoolSize(10);

HikariDataSource dataSource = new HikariDataSource(config);
```

```java
try (Connection conn = dataSource.getConnection()) {
    // conexão emprestada do pool, devolvida no close
}
```

Crie um `HikariDataSource` por aplicação (uma instância só, guardada num
singleton ou no container de injeção) e passe pra quem precisa. Se o pool
esgota, `getConnection()` bloqueia até `connectionTimeout` (default 30s) e
então falha. Tamanho do pool importa menos do que parece: pra I/O de banco, o
dimensionamento ideal costuma ficar perto de `núcleos × 2 + discos` (a wiki do
HikariCP tem o cálculo). Pool gigante não acelera nada, só lota o banco.

## `PreparedStatement`

Execute SQL com parâmetros. SEMPRE use `PreparedStatement`, nunca monte SQL
com concatenação:

```java
String sql = "SELECT id, name, email FROM users WHERE status = ? AND age > ?";

try (Connection conn = dataSource.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {

    stmt.setString(1, "ACTIVE");
    stmt.setInt(2, 18);

    try (ResultSet rs = stmt.executeQuery()) {
        while (rs.next()) {
            long id = rs.getLong("id");
            String name = rs.getString("name");
            String email = rs.getString("email");
        }
    }
}
```

Por que `PreparedStatement`:

- **Segurança**: os parâmetros `?` são tratados como dado, não como SQL. A
  concatenação `"SELECT ... WHERE name = '" + name + "'"` abre SQL injection
  (módulo 24).
- **Reuso**: o mesmo statement preparado executa N vezes sem reparse.
- **Tipagem**: `setInt`, `setLong`, `setString`, `setBigDecimal` convertem
  pro tipo do banco.

`executeQuery()` é pra `SELECT`. Pra `INSERT`/`UPDATE`/`DELETE`:

```java
String sql = "INSERT INTO users (name, email) VALUES (?, ?)";

try (PreparedStatement stmt = conn.prepareStatement(sql)) {
    stmt.setString(1, "Maria");
    stmt.setString(2, "maria@example.com");
    int affected = stmt.executeUpdate();   // quantas linhas mudaram
}
```

### Retornar a chave gerada

```java
String sql = "INSERT INTO users (name) VALUES (?)";

try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
    stmt.setString(1, "Maria");
    stmt.executeUpdate();

    try (ResultSet keys = stmt.getGeneratedKeys()) {
        if (keys.next()) {
            long newId = keys.getLong(1);
        }
    }
}
```

## `ResultSet`

O `ResultSet` é a tabela resultante. Você navega com `next()` (true se tem
linha) e lê colunas pelo nome ou índice:

```java
try (ResultSet rs = stmt.executeQuery()) {
    while (rs.next()) {
        long id = rs.getLong("id");
        String name = rs.getString("name");
        BigDecimal total = rs.getBigDecimal("total");
        LocalDateTime createdAt = rs.getTimestamp("created_at").toLocalDateTime();
    }
}
```

- `getString`, `getInt`, `getLong`, `getBigDecimal`, `getBoolean`,
  `getTimestamp`, `getDate`.
- Coluna `NULL`: `getString` devolve `null`, `getInt` devolve 0. Use
  `rs.wasNull()` pra distinguir.
- `getTimestamp(...).toLocalDateTime()` converte pra `LocalDateTime` (o JDBC
  e o Java moderno conversam bem).

## Mapeando pra um record

O JDBC é manual. O padrão: extrair cada linha pra um `record` ou objeto:

```java
public record User(long id, String name, String email) {}

public User mapRow(ResultSet rs) throws SQLException {
    return new User(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email")
    );
}
```

```java
List<User> users = new ArrayList<>();
try (ResultSet rs = stmt.executeQuery()) {
    while (rs.next()) {
        users.add(mapRow(rs));
    }
}
```

Isso é o que ORM faz por você. No JDBC puro, você escreve o `mapRow` e o
repositório na mão. O padrão Repository (módulo 21) organiza isso.

## Transações

O JDBC é autocommit por padrão: cada statement comita sozinho. Pra várias
operações como uma unidade atômica, desligue o autocommit e comite no fim:

```java
String insertUser = "INSERT INTO users (name) VALUES (?)";
String insertProfile = "INSERT INTO profiles (user_id, bio) VALUES (?, ?)";

try (Connection conn = dataSource.getConnection()) {
    conn.setAutoCommit(false);

    try {
        try (PreparedStatement stmt = conn.prepareStatement(insertUser, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, "Maria");
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                long userId = keys.getLong(1);

                try (PreparedStatement profile = conn.prepareStatement(insertProfile)) {
                    profile.setLong(1, userId);
                    profile.setString(2, "bio da Maria");
                    profile.executeUpdate();
                }
            }
        }
        conn.commit();
    } catch (SQLException e) {
        conn.rollback();        // desfaz tudo
        throw e;
    }
}
```

Regras:

- `setAutoCommit(false)` inicia a transação.
- `commit()` confirma; `rollback()` desfaz. Se um `INSERT` falha, o outro
  também desfaz.
- `rollback` no `catch` desfaz tudo quando qualquer statement lança.

### Isolamento

O `Connection.setTransactionIsolation` controla o nível de isolamento:

```java
conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
```

Os níveis (`READ_UNCOMMITTED`, `READ_COMMITTED`, `REPEATABLE_READ`,
`SERIALIZABLE`) definem como transações concorrentes enxergam umas às outras.
O default varia por banco. Leitura fantasma, read skew e race no update
dependem do nível.

## Chamando stored procedures (`CallableStatement`)

Functions, procedures e triggers moram **no banco**, não no JDBC. O que o
JDBC expõe é a chamada:

- `CallableStatement` pra stored **procedure**.
- `SELECT` normal pra função de banco que devolve tabela (`FROM fn(...)`).
- Triggers não se chamam; o banco dispara no `INSERT`/`UPDATE`/`DELETE` e
  passa invisível pro JDBC. Nada no código muda por causa delas.

```java
// PROCEDURE registrar_historico(IN user_id BIGINT, IN mensagem TEXT)
String sql = "{ CALL registrar_historico(?, ?) }";

try (CallableStatement stmt = conn.prepareCall(sql)) {
    stmt.setLong(1, userId);
    stmt.setString(2, "pedido criado");
    stmt.execute();
}
```

Procedure que retorna valor:

```java
// FUNCTION saldo_total(IN user_id BIGINT) RETURNS DECIMAL
String sql = "{ ? = CALL saldo_total(?) }";

try (CallableStatement stmt = conn.prepareCall(sql)) {
    stmt.registerOutParameter(1, Types.DECIMAL);
    stmt.setLong(2, userId);
    stmt.execute();

    BigDecimal total = stmt.getBigDecimal(1);
}
```

`registerOutParameter` declara o tipo do retorno; você lê depois do `execute`.

Função de banco que devolve conjunto vira `SELECT` normal:

```java
// função que devolve tabela
String sql = "SELECT * FROM obter_pedidos_do_mes(?)";
```

O `CallableStatement` só entra quando a procedure **faz** algo no banco
(grava, atualiza, valida) e você quer chamá-la de dentro do Java. Pra
consulta, `PreparedStatement` resolve.

## Batch: executar muitos inserts de uma vez

Inserir linha por linha em loop é N round-trips ao banco. O batch manda tudo
de uma vez:

```java
String sql = "INSERT INTO order_items (order_id, sku, qty) VALUES (?, ?, ?)";

try (PreparedStatement stmt = conn.prepareStatement(sql)) {
    for (OrderItem item : items) {
        stmt.setLong(1, orderId);
        stmt.setString(2, item.sku());
        stmt.setInt(3, item.qty());
        stmt.addBatch();
    }
    stmt.executeBatch();   // manda todos juntos
}
```

`addBatch` acumula; `executeBatch` dispara. O `int[]` devolvido tem as linhas
afetadas de cada statement. Pra volume alto, isso é ordens de magnitude mais
rápido que o loop com `executeUpdate`.

## Lock otimista vs pessimista

- **Pessimista**: `SELECT ... FOR UPDATE` trava a linha até o commit.

```java
String sql = "SELECT balance FROM accounts WHERE id = ? FOR UPDATE";
```

- **Otimista**: coluna `version` (ou `updated_at`); no UPDATE você compara a
  versão que leu e atualiza. Se mudou, devolve 0 linhas afetadas e você
  decide o que fazer.

```java
String sql = "UPDATE accounts SET balance = ?, version = version + 1 WHERE id = ? AND version = ?";
int affected = stmt.executeUpdate();
if (affected == 0) {
    throw new ConcurrentModificationException("dados mudaram desde a leitura");
}
```

O otimista é o padrão em aplicação web: sem lock segurando conexão, e a
colisão é rara.

## Erros comuns

| Erro | Consequência |
| ---- | ------------ |
| Concatenação de SQL | SQL injection, quebra com `'` no dado |
| Não fechar `Connection` | conexão vazando, pool esgota |
| Criar `Connection` por requisição sem pool | lentidão, banco lotado |
| `getInt` em coluna `NULL` | devolve 0, silencioso |
| `stmt.executeQuery()` pra `INSERT` | `SQLException` confusa |
| Statement dentro do try mas sem fechar `ResultSet` | recurso aberto |
| Insert em loop com `executeUpdate` | N round-trips, lento em volume |
| Não checar `affected == 0` no update otimista | conflito passa invisível |

## Comparação com TypeScript

| Conceito | Java | Node/TS |
| -------- | ---- | ------- |
| Driver | driver JDBC | `pg`, `mysql2`, `better-sqlite3` |
| Pool | HikariCP | `pg.Pool` |
| SQL parametrizado | `PreparedStatement` | `$1`, `$2` (pg) |
| Transação | `setAutoCommit(false)` + commit | `client.query('BEGIN')` / transações |

O `PreparedStatement` e o `$1`/`$2` do `pg` cumprem o mesmo papel: parâmetro
separado do SQL, sem concatenação.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| JDBC | JDK 1.1 | Permanente |
| `try-with-resources` pro JDBC | JDK 7 | Permanente |
| Conversão `Timestamp` ↔ `LocalDateTime` | JDK 8 | Permanente |
| JDBC 4.5 (menor) | JDK 26 | Permanente |

## Exercícios

1. Escreva um `UserRepository` com `findById(long id)` e `findAll()` usando
   JDBC e mapeando pra `record User`. Faça `findById` devolver
   `Optional<User>`. Esperado: usuário inexistente → `Optional.empty()`.
2. Escreva `save(User)` que insere com `RETURN_GENERATED_KEYS` e devolve o
   `User` com o id preenchido. Esperado: inserir dois usuários com o mesmo
   email (coluna `UNIQUE`) lança `SQLException` de violação de constraint.
3. Escreva `transfer(conn, fromId, toId, amount)` que move saldo entre duas
   contas numa transação com `setAutoCommit(false)`, `commit` e `rollback`.
   Teste o caso de saldo insuficiente (a transferência deve desfazer tudo).
4. Escreva um update otimista com coluna `version`. Simule duas conexões
   lendo a mesma linha e atualizando; a segunda deve falhar (0 linhas
   afetadas). Trate o conflito com uma exceção própria.
5. Escreva um método que insere 10.000 linhas com batch (`addBatch` +
   `executeBatch`) e meça o tempo contra o mesmo volume com `executeUpdate`
   em loop. Registre a diferença.
6. Crie uma procedure no banco (ex.: `atualizar_estoque` que recebe `sku` e
   `quantidade`), chame com `CallableStatement` e confirme o efeito. Depois
   chame uma função que devolve um valor (`? = CALL ...`) e leia com
   `getBigDecimal`.

## Referências

- [JDBC Basics (Oracle JDBC docs)](https://docs.oracle.com/javase/tutorial/jdbc/basics/) — conexão, statements, ResultSet e transações
- [HikariCP (GitHub)](https://github.com/brettwooldridge/HikariCP/) — o pool de conexões padrão, com a wiki sobre sizing
- [About Pool Sizing (HikariCP wiki)](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing) — por que poucas conexões bem dimensionadas vencem pools grandes
- [PostgreSQL JDBC Driver](https://jdbc.postgresql.org/) — o driver usado nos exemplos

## Próximo módulo

**Testes Automatizados** — JUnit 5, AssertJ, Mockito e a prática de testar
código Java.

[→ 18 — Testes Automatizados](./18-testes.md)