# 07 — Strings e Text Blocks

`String` no Java é imutável, tem um pool próprio e mais métodos do que você
imagina. Este módulo cobre o que você usa todo dia, o que muda entre versões e
os text blocks do JDK 15.

## Imutabilidade

```java
String name = "java";
String upper = name.toUpperCase();   // name continua "java"
```

`toUpperCase()` devolve uma **nova** String. `name` não muda. Toda operação
sobre String cria outro objeto. Isso dá segurança (compartilhar sem medo de
mutação) e custa memória quando você concatena em loop.

```java
String result = "";
for (int i = 0; i < 1000; i++) {
    result += "x";   // cria 1000 Strings novas
}
```

Pra concatenação repetida, `StringBuilder` (módulo de performance menciona o
porquê):

```java
StringBuilder builder = new StringBuilder();
for (int i = 0; i < 1000; i++) {
    builder.append("x");
}
String result = builder.toString();
```

Existe também o `StringBuffer`, quase idêntico porém thread-safe (métodos
`synchronized`). A segurança custa performance e quase nunca é necessária
(você não compartilha um buffer de montagem entre threads). O `StringBuilder`
é o padrão; `StringBuffer` é legado.

## Pool de strings e interning

```java
String a = "java";
String b = "java";
a == b;            // true, mesmo objeto no pool
```

Literais iguais apontam pro mesmo objeto no string pool. Já `new String("java")`
força um objeto novo fora do pool. Comparar com `==` funciona só por acaso;
compare com `.equals()`. A regra vale sempre: `==` compara referência,
`.equals()` compara conteúdo.

## Métodos que você usa todo dia

```java
String email = "maria@example.com";

email.equals("maria@example.com");     // igualdade de conteúdo
email.equalsIgnoreCase("MARIA@example.com");
email.length();                        // 18
email.isBlank();                       // true só se vazio ou só espaços
email.isEmpty();                       // true só se ""
email.startsWith("maria");
email.endsWith(".com");
email.contains("@");
email.indexOf("@");                    // 5, -1 se não achar
email.substring(0, 5);                 // "maria"
email.replace("maria", "joao");
email.strip();                         // remove espaços nas pontas
email.toLowerCase();
email.chars();                         // IntStream dos code points
```

`isBlank()` (JDK 11) cobre espaço em branco; `isEmpty()` só aceita `""`.
`strip()` (JDK 11) corta espaços Unicode, enquanto `trim()` (JDK 1) só corta
caracteres até 0x20. Prefira `strip()`.

### Cuidado com codepoints e emoji

O `String` é uma sequência de unidades UTF-16. `length()` e `charAt()` contam
**unidades de 16 bits**, não caracteres visíveis. Um emoji (ou caractere
raro) ocupa duas unidades:

```java
String emoji = "🙂";
emoji.length();       // 2, não 1
```

`codePoints()` percorre os caracteres de verdade:

```java
long count = emoji.codePoints().count();   // 1
```

A regra prática: se o texto pode ter emoji ou caracteres não-ASCII, itere com
`codePoints()`, não com `charAt()`.

## Formatando texto

```java
String message = String.format("Pedido %d no total de R$ %.2f", 42, 199.90);
// "Pedido 42 no total de R$ 199.90"

String greeting = "Olá, %s! Você tem %d tarefas pendentes.".formatted("Maria", 3);
```

`formatted()` é o atalho de instância do `String.format` (JDK 15). Mesmo
comportamento, menos cerimônia.

## Split e join

```java
String csv = "java,streams,records";
String[] parts = csv.split(",");       // ["java", "streams", "records"]

String joined = String.join("-", parts);   // "java-streams-records"
```

`split` recebe **regex**. `csv.split(".")` não divide em nada (`.` é curinga);
use `csv.split("\\.")`. Erro clássico.

Juntar uma coleção inteira? `String.join` aceita um `Iterable`, e no meio de
um stream o `Collectors.joining` (módulo 13) faz o mesmo:

```java
String joined = String.join(", ", names);          // Iterable
String streamed = names.stream().collect(Collectors.joining(", "));
```

## Text blocks (JDK 15)

Texto multilinha sem `\n` manual nem escape de aspas:

```java
String sql = """
        SELECT id, name, email
        FROM users
        WHERE status = 'ACTIVE'
        ORDER BY name
        """;

String html = """
        <div class="alert">
          <p>%s</p>
        </div>
        """.formatted(message);
```

Regras:

- Abre e fecha com três aspas. O conteúdo começa na linha seguinte.
- A indentação mínima comum é removida automaticamente. No exemplo, o bloco
  alinha com a posição das aspas de fechamento.
- Não interpolam. Pra valor dentro do texto, `formatted()` ou `String.format`.
  É a diferença do template literal do TS, que interpola com `${}`.
- Escape de aspas vira desnecessário: `"""` fecha o bloco, aspas simples e
  duplas no meio do texto são literais.

O exemplo de SQL acima é o uso mais comum de text block.

Dois métodos-irmãos do text block que aparecem quando você lê/gera texto:

- `stripIndent()`: remove a indentação incidental, o mesmo algoritmo que o
  compilador usa nos text blocks. Útil pra texto vindo de arquivo.
- `translateEscapes()`: interpreta escapes (`\n`, `\t`, `\"`...) como o
  compilador faria. Útil quando o texto chega de fonte externa com escapes
  literais.

```java
String raw = "linha1\\nlinha2";          // \n literal (2 caracteres)
String parsed = raw.translateEscapes();  // quebra de linha de verdade
```

## Convertendo tipos

```java
int number = Integer.parseInt("42");
long big = Long.parseLong("42");
double decimal = Double.parseDouble("3.14");
boolean flag = Boolean.parseBoolean("true");

String text = String.valueOf(42);
```

`Integer.parseInt("abc")` lança `NumberFormatException`. Se o texto pode vir
sujo, trate antes de converter.

## Comparação com TypeScript

| Operação | Java | TypeScript |
| -------- | ---- | ---------- |
| Igualdade | `.equals()` | `===` |
| Multilinha | text block (JDK 15) | template literal com backtick |
| Interpolação | `formatted()` | `${var}` |
| Imutável | sim | primitivo, string também imutável |
| Pool | string pool + interning | interning não exposto |

O ponto que mais confunde quem vem do TS: no TS `${x}` funciona dentro de
template literal, no Java você precisa de `.formatted(x)`.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| `isBlank`, `strip` | JDK 11 | Permanente |
| `formatted()` | JDK 15 | Permanente |
| Text blocks | JDK 15 | Permanente |

## Exercícios

1. Escreva `capitalize(String text)` que deixa a primeira letra maiúscula e o
   resto minúscula. Teste com `null`, string vazia, "OLA MUNDO" e "java".
2. Escreva `maskEmail(String email)` que esconde o meio: `maria@exemplo.com`
   vira `ma***@exemplo.com`. Teste com email curto (`a@b.c`) e sem `@`.
3. Escreva `countWords(String text)` que conta palavras separando por espaços.
   Teste com texto com múltiplos espaços seguidos, com `\t` e com string vazia.
   Depois reescreva usando `split` com `\\s+` e compare os resultados.
4. Escreva um método que recebe um trecho de HTML com text block e substitui
   o placeholder `%s` por um nome usando `formatted()`. Teste com nome que
   contém `%` (o que acontece? Por quê?).

## Referências

- [Class String (Java API docs)](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/String.html) — a referência completa dos métodos de `String`
- [Programmer's Guide to Text Blocks (OpenJDK)](https://openjdk.org/projects/amber/guides/text-blocks-guide) — guia prático de text blocks, indentação e estilos
- [JEP 378 — Text Blocks](https://openjdk.org/jeps/378) — o JEP que adicionou os text blocks no JDK 15
- [Text Blocks (Java Language Updates)](https://docs.oracle.com/en/java/javase/26/language/text-blocks.html) — a doc oficial com `stripIndent` e `translateEscapes`

## Próximo módulo

**Arrays e Coleções** — arrays, `List`, `Set`, `Map`, implementações e as
factories imutáveis do JDK 9.

[→ 08 — Arrays e Coleções](./08-arrays-e-colecoes.md)