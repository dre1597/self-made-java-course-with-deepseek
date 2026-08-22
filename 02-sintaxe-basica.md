# 02 — Sintaxe Básica e Convenções

Como o Java se estrutura por fora: arquivo, `package`, imports, `main`,
convenções e comentários. Nada de teoria de programação: só o idioma Java.

## Estrutura de um arquivo

Um arquivo `.java` tem até quatro partes, nessa ordem:

```java
package br.com.example.inventory;      // 1. package (obrigatório em projeto real)

import java.math.BigDecimal;           // 2. imports
import java.util.List;

public class Product {                 // 3. declaração do tipo
    // 4. corpo: campos, construtores, métodos
}
```

Regras de arquivo:

- O nome do arquivo precisa bater com a classe pública: `Product.java` contém
  `public class Product`. Senão não compila.
- Um arquivo pode ter várias classes, mas só uma pública (e essa define o nome
  do arquivo). Na prática: um tipo por arquivo.
- `package` e `import` não são obrigatórios (um `Hello.java` solto roda sem),
  mas em projeto real, sim.

## `package` e `import`

`package` organiza o namespace e mapeia pra pasta. `br.com.example.inventory`
fica em `br/com/example/inventory/`. É o equivalente ao nome do módulo/pasta em
TypeScript, mas com valor de namespace real.

`import` traz tipos de outros packages. Duas formas:

```java
import java.util.List;              // tipo único
import java.util.*;                 // todos os tipos do package (wildcard)
```

Não confunda com `import` do módulo do Java 25 (JEP 511): `import java.util.*;`
importa os tipos do package; `import module java.base;` importa tudo que um
módulo exporta. Wildcard de package ainda é o uso comum.

Dois packages sempre disponíveis sem import: `java.lang` e o package do
próprio arquivo.

### `import static`

Importa membros **estáticos** (métodos e constantes), não tipos:

```java
import static java.lang.Math.PI;
import static java.lang.Math.max;

double area = PI * r * r;
int bigger = max(a, b);
```

Muito `import static` polui e esconde de onde veio o símbolo. Use com moderação
(em testes, `assertThat`/`assertEquals` estáticos são o uso comum).

## Modificadores de acesso

| Modificador | Acesso |
| ----------- | ------ |
| `public` | todo mundo |
| `protected` | mesmo pacote + subclasses (em qualquer pacote) |
| (sem modificador) | só mesmo pacote (package-private) |
| `private` | só a própria classe |

```java
public class Order {
    private BigDecimal total;      // ninguém fora da classe
    protected String region;       // subclasses e mesmo pacote
    double discount;               // package-private: mesmo pacote
    public String id() { ... }     // qualquer um
}
```

A regra que separa o Java de muita linguagem: **package-private** (sem
modificador) é um nível real de acesso. Se o campo não é `private`, ele é
visível no pacote inteiro, não só na classe. O Java não tem o `#` do TS pra
esconder de "fora da classe mas dentro do módulo".

## Ordem dentro da classe

Convenção da JDK (e da maioria dos codebases):

1. Campos `static` (constantes primeiro)
2. Campos de instância
3. Construtores
4. Métodos

```java
public class PaymentProcessor {
    private static final int MAX_RETRIES = 3;   // 1. constantes
    private final PaymentGateway gateway;        // 2. campos
    private final Logger logger;

    public PaymentProcessor(PaymentGateway gateway) {   // 3. construtor
        this.gateway = gateway;
    }

    public Payment process(Payment payment) {     // 4. métodos
        // ...
    }
}
```

Não é sintaxe, é legibilidade. Ordem fixa deixa o leitor achar campo,
construtor e método sem caçar no arquivo.

## O ponto de entrada: `main`

O `main` clássico, que continua valendo:

```java
public class Application {
    public static void main(String[] args) {
        System.out.println("up");
    }
}
```

- `public`: a JVM precisa acessar de fora.
- `static`: não existe instância da classe ainda.
- `void`: não retorna nada.
- `String[] args`: argumentos da linha de comando (ou `String... args`, varargs, mesmo tipo).

Desde o Java 21 (permanente no 25), `main` de **instância** funciona e o
arquivo solto dispensa a classe (módulo 01). O `main` clássico continua o
padrão em projeto real.

Comparação com TS: no Node, o entry point é o topo do arquivo; no Java, é o
método `main` de uma classe designada.

## Convenções de nomenclatura

| O quê          | Padrão        | Exemplo                          |
| -------------- | ------------- | -------------------------------- |
| Classe         | PascalCase    | `OrderService`, `Product`        |
| Método         | camelCase     | `findById()`, `getTotal()`       |
| Variável       | camelCase     | `orderTotal`, `customerName`     |
| Constante      | UPPER_SNAKE   | `MAX_RETRIES`, `DEFAULT_TIMEOUT` |
| Pacote         | lowercase     | `br.com.example.inventory`       |
| `record`       | PascalCase    | `OrderItem`                      |
| Enum           | PascalCase    | `OrderStatus`                    |
| Constante enum | UPPER_SNAKE   | `OrderStatus.PAID`               |
| Arquivo        | = nome classe | `OrderService.java`              |

São convenção, não sintaxe, mas o código segue. Igual ao ESLint do TS
impondo `camelCase`/`snake_case`, só que a convenção Java é cultura da
linguagem, não uma ferramenta.

## Comentários

Três formas:

```java
// comentário de linha

/*
   comentário de bloco
*/

/**
 * Javadoc: documenta a API pública.
 * Vira HTML na documentação gerada com `javadoc`.
 */
public BigDecimal totalAmount() { ... }
```

Regra de ouro: comentário explica **porquê**, não **o quê**. Nome descritivo
de variável/método já diz o quê. Javadoc é pra API pública (quem consome a
classe); código interno se explica sozinho.

## Palavras-chave que valem conhecer

| Palavra | Papel |
| ------- | ----- |
| `final` | Dependendo do contexto: constante, classe não-herdável, método não-sobreposto, campo imutável, variável de uma atribuição |
| `static` | Pertence à classe, não à instância |
| `var` | Inferência de tipo local (JDK 10) |
| `sealed` | Classe/interface com subtipos restritos (JDK 17) |
| `record` | Classe imutável de dados (JDK 16) |
| `new` | Instancia um objeto |
| `this` | Referência ao objeto atual; desambigua campo x parâmetro |
| `super` | Chama construtor/método da classe pai |
| `void` | Tipo de retorno "nada" |
| `return` | Devolve um valor e sai do método |

Os detalhes de cada um vêm nos módulos certos. Aqui é só pra você reconhecer
no código.

## Comparação rápida com TypeScript

| Conceito        | TypeScript              | Java                            |
| --------------- | ----------------------- | ------------------------------- |
| Namespace       | módulos / pastas        | `package`                       |
| Entry point     | topo do arquivo         | método `main`                   |
| Import          | `import { x } from ...` | `import java.util.List;`        |
| Convenção       | ESLint/Prettier         | convenção cultural + formatter  |
| Documentação    | JSDoc                   | Javadoc                         |

O `package` Java e o módulo TS cumprem o mesmo papel de organizar e evitar
colisão de nomes, mas o Java valida a estrutura em tempo de compilação.

## Exercícios

1. Crie `InventoryApplication.java` com um `main` que imprime a quantidade de
   itens passada por argumento. Rode com `java InventoryApplication.java 42`.
   Teste sem argumento (o que `args` contém? `args.length` é quanto?) e com
   dois argumentos.
2. Crie um `record OrderItem(String sku, int quantity)` e um `main` que
   instancia e imprime. Sem classe pública: o arquivo pode ter `record` e o
   `main` de instância. Veja se compila.
3. Escreva um comentário Javadoc na classe `OrderService` e um comentário
   `//` explicando um porquê (não um o quê) dentro de um método. Mostre a
   diferença dos dois níveis.
4. Corrija o seguinte trecho para seguir as convenções Java (nome de classe,
   método, constante e pacote):

   ```java
   package badexample;
   public class orderHandler {
       int MAX_ITEMS = 10;
       void Process() {}
   }
   ```

## Referências

- [Java Language Specification — Chapter 6: Names](https://docs.oracle.com/javase/specs/jls/se24/html/jls-6.html) — a especificação das convenções de nomes, a fonte da verdade
- [Oracle Code Conventions — Naming Conventions](https://www.oracle.com/java/technologies/javase/codeconventions-namingconventions.html) — as convenções clássicas em tabela
- [Naming a Package (Java Tutorials)](https://docs.oracle.com/javase/tutorial/java/package/namingpkgs.html) — por que o package começa com o domínio invertido
- [JEP 511 — Module Import Declarations](https://openjdk.org/jeps/511) — o `import module` que os compact source files usam por baixo

## Próximo módulo

**Tipos de Dados e Variáveis** — primitivos, wrappers, `var`, casting, null e
o que mudou entre versões.

[→ 03 — Tipos de Dados e Variáveis](./03-tipos-de-dados-e-variaveis.md)