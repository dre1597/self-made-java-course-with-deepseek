# 15 — I/O e Arquivos

`java.nio.file` e as formas modernas de ler e escrever arquivo. Se você veio do
`java.io` (`File`, `FileInputStream`), quase tudo mudou pra melhor.

## `Path` e `Files`

O `java.io.File` virou `java.nio.file.Path`, e as operações viraram métodos
estáticos de `Files`:

```java
Path configPath = Path.of("config", "app.properties");
Path absolute = Path.of("/home/user/config/app.properties");
Path resolved = Path.of("base").resolve("sub/arquivo.txt");
```

O `Path.of` (JDK 11) substitui `Paths.get`. Métodos úteis:

```java
path.getFileName();      // arquivo.txt
path.getParent();        // base/
path.toAbsolutePath();
path.normalize();        // resolve ".." e "."
```

`Path` não verifica se o arquivo existe; é só o endereço. Existência e I/O são
`Files`:

```java
Files.exists(path);
Files.isRegularFile(path);
Files.isDirectory(path);
Files.size(path);
Files.getLastModifiedTime(path);
```

## Lendo arquivo

A forma direta pra arquivo pequeno:

```java
String content = Files.readString(Path.of("arquivo.txt"));
List<String> lines = Files.readAllLines(Path.of("arquivo.txt"));
```

Pra arquivo grande ou streaming, use `Files.lines` que devolve um `Stream`
preguiçoso e fecha o arquivo sozinho:

```java
long count = Files.lines(path)
        .filter(line -> line.contains("ERROR"))
        .count();
```

`Files.lines` precisa ser fechado. Envolva em try-with-resources se o arquivo
é grande e você consome parcialmente. Pra arquivo pequeno, `readAllLines`
simplifica e não vaza recurso.

### BufferedReader/BufferedWriter

`Files.newBufferedReader` e `newBufferedWriter` são o padrão pra processar
linha a linha com buffer, quando você não quer o stream inteiro na memória:

```java
try (BufferedReader reader = Files.newBufferedReader(path)) {
    String line;
    while ((line = reader.readLine()) != null) {
        process(line);
    }
}

try (BufferedWriter writer = Files.newBufferedWriter(path)) {
    writer.write("linha 1");
    writer.newLine();
}
```

O `BufferedReader.lines()` devolve o mesmo `Stream<String>` do `Files.lines`,
mas com a leitura controlada por você. Pra arquivo grande, o `readLine` em
loop processa sem carregar tudo.

## Escrevendo arquivo

```java
Files.writeString(path, content);                       // cria/sobrescreve
Files.writeString(path, content, CREATE, APPEND);       // adiciona
Files.write(path, lines);                               // lista de linhas
```

Criação de diretórios:

```java
Files.createDirectories(Path.of("logs", "2026"));       // cria recursivo
Files.createFile(path);                                 // só se não existir
```

O `createDirectories` cria a árvore inteira. `createFile` lança
`FileAlreadyExistsException` se o arquivo existe.

## Mover, deletar e permissões

```java
Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
Files.delete(path);              // lança NoSuchFileException se não existe
Files.deleteIfExists(path);      // não lança se não existe

Files.isReadable(path);
Files.isWritable(path);
Files.isExecutable(path);
```

- `move` move (e serve de "rename"). Com `REPLACE_EXISTING` sobrescreve o
  destino.
- `delete` estoura se o arquivo sumiu; `deleteIfExists` é a versão tolerante.
  Pra "apagar se existir" use o `deleteIfExists`.
- Os `isReadable`/`isWritable` checam permissão real do sistema de arquivos
  (não só o atributo), útil antes de operações que vão falhar por permissão.

## Arquivos temporários

```java
Path temp = Files.createTempFile("relatorio", ".csv");   // /tmp/relatorio123.csv
Path tempDir = Files.createTempDirectory("app-");

Files.writeString(temp, "conteudo");
Files.deleteIfExists(temp);      // limpeza manual
```

`createTempFile` gera um arquivo único num diretório de temporários, sem
colisão de nome. Pra teste e processamento descartável. O `deleteOnExit` do
`java.io.File` continua existindo, mas o `Files.deleteIfExists` explícito é
mais confiável (roda na hora, não na saída da JVM).

## NIO: canais e buffers

Para cópia e processamento de binário, `Files.copy` resolve sem você montar
buffer:

```java
Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
```

Para streams de bytes você usa `InputStream`/`OutputStream`, mas sempre
via `Files`:

```java
try (InputStream in = Files.newInputStream(sourcePath);
     OutputStream out = Files.newOutputStream(targetPath)) {
    in.transferTo(out);       // JDK 9
}
```

`transferTo` copia o stream inteiro, sem loop de buffer manual.

## Serialização de objetos

`ObjectOutputStream` grava objetos Java (o tipo precisa implementar
`Serializable`):

```java
public class Config implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    // campos
}
```

```java
try (ObjectOutputStream out = new ObjectOutputStream(
        Files.newOutputStream(path))) {
    out.writeObject(config);
}

try (ObjectInputStream in = new ObjectInputStream(
        Files.newInputStream(path))) {
    Config loaded = (Config) in.readObject();
}
```

Pontos que pegam:

- Campos que mudam de nome/tipo entre versões quebram a desserialização. O
  `serialVersionUID` controla a compatibilidade.
- Segurança: desserializar dado não confiável é vetor de ataque (gadgets).
  Serialização Java é pra estado interno seu, não pra trocar dado com API.
- O JDK 17 (JEP 415) adicionou **filtros de desserialização**: você configura
  o `ObjectInputStream` pra aceitar só classes específicas. Pra dado não
  confiável, desserialize com filtro ou não desserialize:

```java
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
        "br.com.example.*;java.util.*;!*");
ObjectInputStream in = new ObjectInputStream(Files.newInputStream(path));
in.setObjectInputFilter(filter);
```

- Pra dado trocado com sistema externo, prefira JSON (módulo 19).

## `Files.walk` e `find`

Percorrer árvore de diretórios:

```java
try (var paths = Files.walk(Path.of("src"))) {
    List<Path> javaFiles = paths
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .toList();
}
```

`walk` devolve `Stream<Path>` preguiçoso; feche com try-with-resources. O
`Files.find` é o `walk` com predicado de atributo embutido.

## `Files.mismatch` (JDK 12)

Compara o conteúdo de dois arquivos byte a byte:

```java
long position = Files.mismatch(source, target);
// -1 se idênticos, ou a posição do primeiro byte diferente
```

Cobre o caso que `isSameFile` não resolve: `isSameFile` diz se dois `Path`
apontam pro mesmo arquivo físico; `mismatch` compara conteúdo. Útil pra
validar cópia, download ou se um arquivo mudou de fato.

## Charset e encoding

O Java lê e escreve no charset padrão da plataforma se você não especificar.
Pra não depender do ambiente, declare:

```java
Files.readString(path, StandardCharsets.UTF_8);
Files.writeString(path, content, StandardCharsets.UTF_8);
```

Dado corrompido de encoding é o erro mais silencioso que existe. Sempre passe
o charset em código que lida com arquivo de texto.

## Comparação com TypeScript

| Operação | Java | Node.js |
| -------- | ---- | ------- |
| Ler texto | `Files.readString` | `fs.readFileSync` / `readFile` |
| Escrever | `Files.writeString` | `fs.writeFileSync` |
| Percorrer | `Files.walk` | diretório manual com `readdir` |
| Caminho | `Path` imutável | `path` do Node (string) |
| Binário | `InputStream` / `Files.copy` | `Buffer` |

O `fs` do Node é assíncrono por padrão; o Java é síncrono por padrão. Se você
precisa de concorrência, `virtual threads` (módulo 16) rodam I/O bloqueante
sem custo de thread.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| `java.nio.file` | JDK 7 | Permanente |
| `InputStream.transferTo` | JDK 9 | Permanente |
| `Files.readString` / `writeString` | JDK 11 | Permanente |
| `Path.of` | JDK 11 | Permanente |
| `Files.mismatch` | JDK 12 | Permanente |
| Filtros de desserialização (JEP 415) | JDK 17 | Permanente |

## Exercícios

1. Escreva um método que lê um arquivo de texto e devolve o número de linhas
   não vazias. Teste com arquivo vazio, só linhas em branco e com BOM UTF-8
   na primeira linha (o que o BOM faz com a primeira linha?).
2. Escreva um método que copia um diretório recursivamente com `Files.walk`,
   preservando a estrutura. Teste com diretório vazio, aninhado e com
   arquivo grande.
3. Escreva um método `appendLog(Path, String line)` que adiciona uma linha com
   timestamp ao arquivo, criando o diretório se faltar. Teste com caminho
   inexistente e com permissão de escrita negada.
4. Serialize uma `Config` com `ObjectOutputStream`, mude a classe (renomeie
   um campo), e desserialize a versão antiga. O que acontece? Explique o
   papel do `serialVersionUID`.

## Referências

- [File I/O (Featuring NIO.2) (Java Tutorials)](https://docs.oracle.com/javase/tutorial/essential/io/fileio.html) — `Path`, `Files` e as operações de arquivo em detalhe
- [Path Operations (Java Tutorials)](https://docs.oracle.com/javase/tutorial/essential/io/pathOps.html) — `normalize`, `resolve`, `toRealPath`
- [Class Files (Java API docs)](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/nio/file/Files.html) — a referência completa dos métodos de `Files`
- [I/O Streams (Java Tutorials)](https://docs.oracle.com/javase/tutorial/essential/io/streams.html) — streams de bytes e caracteres

## Próximo módulo

**Concorrência** — threads, `ExecutorService`, virtual threads e
`CompletableFuture`.

[→ 16 — Concorrência](./16-concorrencia.md)