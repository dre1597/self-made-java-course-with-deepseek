# 14 — Tratamento de Exceções

Como o Java lida com erro: checked vs unchecked, `try` nas suas formas,
try-with-resources e exceções customizadas. O `null` de ausência você trata com
`Optional` (módulo 13); o erro de fluxo, com exceção.

## A hierarquia

```
Throwable
├── Error          (sem tratamento: OutOfMemoryError, StackOverflowError)
└── Exception
    ├── RuntimeException   (unchecked)
    └── checked exceptions (IOException, SQLException, ...)
```

- `Error`: problema da JVM ou do ambiente. Você não captura nem lança.
- `RuntimeException` e subclasses: **unchecked**. O compilador não exige
  tratamento. `NullPointerException`, `IllegalArgumentException`,
  `ArithmeticException` entram aqui.
- Demais `Exception`: **checked**. O compilador obriga você a tratar ou
  declarar com `throws`.

```java
public void readFile(String path) throws IOException {
    Files.readAllLines(Path.of(path));
}
```

`readAllLines` lança `IOException`, checked. Sem `throws` na assinatura ou
`try/catch`, não compila.

## try-catch-finally

```java
try {
    List<String> lines = Files.readAllLines(Path.of("dados.txt"));
    return lines;
} catch (IOException e) {
    log.error("falha ao ler arquivo", e);
    return List.of();
}
```

Formas:

```java
// vários catch, do mais específico pro mais genérico
catch (FileNotFoundException e) { ... }
catch (IOException e) { ... }

// multi-catch (JDK 7): mesmos tipos em um só catch
catch (FileNotFoundException | NumberFormatException e) { ... }

// finally roda sempre, com ou sem exceção
} finally {
    closeResource();
}
```

Regra do multi-catch: os tipos do mesmo `catch` não podem ser um subtipo do
outro. `catch (FileNotFoundException | IOException e)` não compila porque o
primeiro é subtipo do segundo.

## try-with-resources (JDK 7)

Fechar recurso automático. O recurso implementa `AutoCloseable`:

```java
try (BufferedReader reader = Files.newBufferedReader(Path.of("dados.txt"))) {
    return reader.readLine();
} catch (IOException e) {
    log.error("falha ao ler arquivo", e);
    return null;
}
```

O `reader` fecha sozinho ao sair do bloco, na ordem inversa da declaração.
Sem `finally` manual, sem `close()` esquecido. Vários recursos:

```java
try (Connection conn = dataSource.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql);
     ResultSet rs = stmt.executeQuery()) {
    // ...
}
```

O `finally` clássico ainda existe pra recurso que não é `AutoCloseable`.
`AutoCloseable` você implementa na própria classe quando ela segura recurso
(arquivo, conexão, socket).

### Extended try-with-resources (JDK 9)

Além de declarar recurso novo entre parênteses, você pode usar uma variável
**já existente**:

```java
BufferedReader reader = openReader();
try (reader) {
    // usa o reader; fecha no fim do bloco
}
```

Antes do JDK 9, isso não compilava: o `try` exigia que o recurso fosse
declarado ali dentro. Agora o `reader` declarado antes entra no `try` e fecha
na saída, sem precisar reatribuir. A exceção do `close()` continua entrando
como suppressed, igual no caso de declaração interna.

## Unchecked vs checked na prática

Java tradicional (e a JDK) divide em checked e unchecked. A prática moderna
pende pro unchecked na maioria dos casos:

| Situação | Tipo |
| -------- | ---- |
| Erro de programação (argumento inválido, estado ilegal) | unchecked |
| Falha de infraestrutura que a app não recupera | unchecked ou checked |
| Recurso externo obrigatório (arquivo, banco, rede) | checked (na JDK) |

Exceções de framework (Spring, Jackson) são runtime. `RuntimeException` não
pede `throws`, então você não espalha `throws Exception` pela assinatura.

Armadilha do checked: capturar e engolir.

```java
// RUIM: engoliu o erro, ninguém sabe
try {
    Files.readAllLines(path);
} catch (IOException e) {
    // nada
}
```

Registre ou re-lance. Engolir exceção é como apagar o alarme de incêndio.

## Exceções customizadas

```java
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(long orderId) {
        super("pedido não encontrado: " + orderId);
    }
}
```

```java
Order order = orders.stream()
        .filter(o -> o.id() == orderId)
        .findFirst()
        .orElseThrow(() -> new OrderNotFoundException(orderId));
```

Práticas:

- `RuntimeException` pra a maioria dos casos. Checked só quando o chamador
  precisa ser **obrigado** a lidar com a falha.
- O sufixo `Exception` no nome é convenção.
- Mensagem descreve o problema e o dado relevante (`orderId`), não uma frase
  genérica.
- Prefira as exceções da JDK quando encaixam: `IllegalArgumentException` pra
  argumento inválido, `IllegalStateException` pra estado ilegal,
  `UnsupportedOperationException` pra operação não suportada.

## Re-lançar e encadear

```java
try {
    parseOrder(line);
} catch (NumberFormatException e) {
    throw new OrderProcessingException("linha inválida: " + line, e);
}
```

Passar a exceção original no construtor preserva a stack trace completa. O
problema raiz não some, vira a causa.

## Armadilhas

- **Capturar `Exception` genérico**: esconde o que pode falhar e o que a
  app pode ou não recuperar. Capture tipos específicos.
- **Capturar e retornar valor "padrão" sem avisar**: quem chama pensa que
  tudo deu certo. Se você vai degustar o erro, registre antes.
- **Exceção no construtor do `catch`**: se o bloco de tratamento lança, a
  exceção original vira suppressed. O try-with-resources também suprime.
- **`throws Exception` na assinatura**: mostra que você não sabe o que pode
  falhar. Seja específico.

## Comparação com TypeScript

| Conceito | Java | TypeScript |
| -------- | ---- | ---------- |
| Checked | compilador obriga | não existe |
| Unchecked | `RuntimeException` | qualquer `Error`/`throw` |
| Recurso | `AutoCloseable` + try-with-resources | `finally`, ou libs de dispose |
| Causa | `throw new X(msg, cause)` | `Error` com `cause` |
| Ausência | `Optional` | `?.` / `null` |

O TS não separa checked de unchecked; qualquer `throw` é runtime. No Java o
compilador decide o que te obriga a tratar. O `try/finally` do TS e o
try-with-resources do Java resolvem o mesmo problema, mas o Java fecha recurso
no escopo, sem você lembrar.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| try-with-resources | JDK 7 | Permanente |
| Multi-catch | JDK 7 | Permanente |
| `Optional.orElseThrow` | JDK 10 | Permanente |
| Exceções padrão da JDK | vários | Permanente |

## Exercícios

1. Escreva um método `parseConfig(String line)` que lança
   `IllegalArgumentException` com mensagem descritiva se o formato não for
   `chave=valor`. Teste com linha vazia, sem `=` e com chave vazia.
2. Implemente uma classe `FileProcessor` com um método que lê um arquivo com
   try-with-resources e um método que escreve, ambos tratando `IOException`
   de forma específica. Teste com arquivo inexistente.
3. Crie uma exceção customizada `AccountBlockedException` que carrega o
   `accountId` e o motivo. Lance-a e teste capturando e lendo os campos.
4. Escreva um método que abre uma conexão JDBC com try-with-resources e
   explique o que acontece com a exceção do `close()` quando o bloco lança
   uma exceção (suppressed). Demonstre com um teste.

## Referências

- [The try-with-resources Statement (Java Tutorials)](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html) — como fechar recursos e a ordem de fechamento
- [The try-with-resources Statement (Oracle language guide)](https://docs.oracle.com/javase/8/docs/technotes/guides/language/try-with-resources.html) — suppressed exceptions e a regra do `close` invertido
- [Lesson: Exceptions (Java Tutorials)](https://docs.oracle.com/javase/tutorial/essential/exceptions/) — checked vs unchecked, `catch`, `finally` e custom exceptions

## Próximo módulo

**I/O e Arquivos** — `java.nio.file`, `Path`, `Files` e leitura/escrita de
arquivos no Java moderno.

[→ 15 — I/O e Arquivos](./15-io-e-arquivos.md)