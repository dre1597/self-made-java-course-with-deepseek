# 01 — Introdução e Setup

## O ecossistema Java

Java roda em duas camadas: você escreve código, o `javac` compila pra **bytecode**, e a **JVM** executa esse bytecode. A
JVM faz JIT (compila bytecode quente pra código nativo durante a execução) e cuida da coleta de lixo.

| Peça    | O que é                                                                                  |
|---------|------------------------------------------------------------------------------------------|
| **JDK** | Kit de desenvolvimento: `javac`, JVM, bibliotecas, ferramentas (`jar`, `jshell`, `jcmd`) |
| **JRE** | Não existe mais desde o Java 11 (JEP 320). Pra rodar, você usa o JDK                     |
| **JVM** | Máquina virtual que executa bytecode, faz JIT e gerencia memória                         |
| **JIT** | Compilação do bytecode pra código nativo em tempo de execução                            |

O Java segue um ciclo fixo: **uma release a cada 6 meses**, e uma **LTS a cada 4 anos**. As LTS até hoje: 8 (2014), 11
(2018), 17 (2021), 21 (2023), **25 (2025)**. O curso usa o **Java 25 LTS**.

Entre uma LTS e outra saem releases intermediárias (22, 23, 24, 26, 27...) com features que podem ou não virar
permanentes. Onde algo mudou de versão, o módulo cita o número.

## Instalação com asdf

asdf gerencia múltiplas versões de JDK igual gerencia Node. Fluxo:

```bash
# Instala o plugin (uma vez só)
asdf plugin add java https://github.com/halcyon/asdf-java.git

# Lista as versões disponíveis
asdf list all java | grep openjdk-25

# Instala a última LTS (o número exato muda com o tempo)
asdf install java openjdk-25.0.1

# Fixa a versão na pasta do projeto
asdf local java openjdk-25.0.1

java -version
```

O `asdf local` cria um `.tool-versions` na raiz. Cada projeto pode ter um JDK diferente, igual `nvm use`.

> ⚠️ **Atenção**: JDK não é igual Node. Sem o JDK inteiro, você não tem
> `javac` nem `jshell`. Instalar só o "runtime" não basta.

## Ferramentas essenciais

### `jshell` — REPL

Testar trecho de código sem criar arquivo:

```text
$ jshell
jshell> List<String> languages = List.of("java", "kotlin", "scala");
languages ==> [java, kotlin, scala]

jshell> languages.stream().map(String::toUpperCase).toList()
$6 ==> [JAVA, KOTLIN, SCALA]

jshell> /exit
```

### Compact source files (JDK 21, permanente no 25)

Desde o Java 21, um `.java` solto roda sem classe declarada. O compilador envolve campos e métodos soltos numa classe
implícita (JEP 512, permanente no Java 25): sem `public class`, sem `public static void main(String[] args)`, sem
`System.out`.

Campos e métodos ficam soltos no arquivo, e o `main` de instância (sem
`static`) é o ponto de entrada:

```java
String greeting = "Hello";

String greet(String name) {
  return greeting + ", " + name;
}

void main() {
  IO.println(greet("world"));
}
```

O `main` é obrigatório: sem ele, o compilador rejeita o arquivo. Statements soltos no topo também não compilam, só
campos e métodos (os previews do Java 21 ao 24 tinham statements soltos, o JEP 512 final removeu).

Roda assim:

```bash
java Hello.java
# Hello, world
```

O `readln` e o resto da classe `IO` estão no módulo 15.

Isso é pra script rápido e programa pequeno. Projeto sério usa classe normal e build tool.

### `javac` e `java`

```bash
javac Main.java       # compila pra Main.class
java Main             # roda a classe compilada
```

Flags que você vai usar cedo ou tarde:

```bash
# compila pro alvo de uma versão específica (evita usar API mais nova)
javac --release 17 Main.java

# define onde os .class vão (default: mesma pasta do .java)
javac -d build/classes Main.java

# roda um jar executável
java -jar app.jar
```

O `--release` é o mais importante: compila garantindo que o código roda numa versão mais antiga. `-d` organiza os
`.class` fora da pasta de código-fonte.

### Classpath e JAR

O `javac` gera um `.class` por classe, espalhado em pastas. O **classpath** é a lista de onde a JVM procura esses
`.class` (e libs). Default: a pasta atual.

```bash
# adiciona uma pasta e um jar à procura
java -cp build/classes:lib/util.jar Main
```

Sem o classpath certo, o erro clássico: `ClassNotFoundException` ou
`NoClassDefFoundError`. O `.` (pasta atual) nem sempre tá no classpath; seja explícito.

O **JAR** é um `.class` (ou vários) compactado num arquivo só, com um
`META-INF/MANIFEST.MF` opcional. É o "pacote" do Java pré-build-tool:

```bash
jar --create --file app.jar -C build/classes .
jar --list --file app.jar
```

O `META-INF/MANIFEST.MF` é um texto simples, formato `Chave: Valor`, uma entrada por linha. A entrada `Main-Class` diz
qual classe tem o `main` que o
`java -jar` chama:

```
Manifest-Version: 1.0
Main-Class: Main
```

O `jar` gera o manifest sozinho quando você passa `--main-class`:

```bash
jar --create --file app.jar --main-class Main -C build/classes .
```

Ou você cria o arquivo na mão e passa com `--manifest`:

```bash
jar --create --file app.jar --manifest manifest.mf -C build/classes .
```

Outra entrada útil é `Class-Path`, que adiciona dependências ao classpath do jar:

```
Manifest-Version: 1.0
Main-Class: Main
Class-Path: lib/util.jar lib/other.jar
```

No `java -jar app.jar`, o classpath vem do manifest — os `-cp` da linha de comando são ignorados.

```bash
# manifest apontando a classe com main → roda com -jar
java -jar app.jar
```

Na prática você raramente monta JAR na mão: o Gradle/Maven fazem isso (módulo 22). O que importa aqui é entender o
modelo: bytecode em `.class`, procura por classpath, empacotamento em JAR.

## Estrutura de projeto

Pra projeto com build tool (detalhe no módulo 22):

```
projeto/
├── build.gradle.kts        # config do build (Kotlin DSL)
├── settings.gradle.kts     # nome do projeto
├── gradlew                 # wrapper — não precisa Gradle instalado
├── src/
│   ├── main/
│   │   └── java/           # código de produção
│   └── test/
│       └── java/           # testes
└── build/                  # artefatos gerados (não versionar)
```

Analogia com TS: `src/main/java` é o `src/`, `src/test/java` é onde o Jest/Vitest lê, `gradlew` é o `npm`, `build/` é o
`dist/`.

## Comparando com TypeScript

| Conceito     | TypeScript / Node       | Java                          |
|--------------|-------------------------|-------------------------------|
| Runtime      | V8 (JIT)                | JVM (JIT)                     |
| Compilação   | `tsc` gera JS           | `javac` gera bytecode         |
| REPL         | `node` no terminal      | `jshell`                      |
| Script solto | `node arquivo.ts`       | `java Arquivo.java` (JDK 21+) |
| Versão       | `nvm` / `fnm`           | `asdf` / `sdkman`             |
| Dependências | `npm` / `pnpm` / `yarn` | Maven ou Gradle               |

Diferença estrutural: TS roda do fonte, Java compila pra bytecode e só então roda. O compilador Java checa tipo na
compilação e rejeita o que não compila; tipo errado é erro de build, não de runtime.

## Linha de comando útil

```bash
java -version              # versão do JDK em uso
jshell                     # REPL
java Arquivo.java          # roda compact source file
javac Main.java            # compila
java Main                  # roda classe compilada
javac --release 17 -d build/classes Main.java   # compila pra um alvo, em pasta
java -cp build/classes:lib/*.jar Main           # roda com classpath explícito
jar --create --file app.jar -C build/classes .  # empacota
jar --list --file app.jar  # inspeciona um jar
java -jar app.jar          # roda jar executável
jps                        # lista processos JVM
jcmd -l                    # lista processos com mais detalhe
```

## Exercícios

1. Rode `jshell` e crie um `record Product(String sku, double price)`. Instancie, chame `sku()` e `price()`, e veja o
   `toString()` gerado. ✅

```java
void main() {
  var product1 = new Product("0123", 10);

  IO.println(product1.sku());
  IO.println(product1.price());
}

record Product(String sku, double price) {
}
```

2. FizzBuzz: de 1 a 100, imprima `Fizz` pra múltiplo de 3, `Buzz` pra múltiplo de 5, `FizzBuzz` pra múltiplo dos dois, e
   o número caso contrário. Use um loop com `IO.println` num compact source file e rode com
   `java FizzBuzz.java`. Guarde o teto numa variável `ceiling` e teste os limites: com `ceiling = 1` sai só `1`; com
   `ceiling = 0` o loop não roda e nada é impresso. ✅

```java
void main() {
  var ceiling = 100;
//  var ceiling = 1;
//  var ceiling = 0;
  for (var number = 1; number <= ceiling; number++) {
    var output = "";
    if (number % 3 == 0 && number % 5 == 0) {
      output += "FizzBuzz";
    }

    if (number % 3 == 0) {
      output += "Fizz";
    }
    if (number % 5 == 0) {
      output += "Buzz";
    }
    if (output.isEmpty()) {
      output = String.valueOf(number);
    }
    IO.println(output);
  }
}
```   

3. Crie `Sum.java` que recebe dois inteiros como argumento de linha de comando e imprime a soma. Rode com
   `java Sum.java 10 20`. Compare com o equivalente em TypeScript.

```java
void main(String[] args) {
  var firstNumber = Integer.parseInt(args[0]);
  var secondNumber = Integer.parseInt(args[1]);

  IO.println(firstNumber + secondNumber);
}
```

```ts
const firstNumber = process.argv[2];
const secondNumber = process.argv[3];

console.log(Number(firstNumber) + Number(secondNumber));
```

```
No nodejs recebemos nos args o caminho absoluto do node e do arquivo q ta sendo executado e dps os extras, o java limpa isso.
No nodejs também fica no process e nao no ponto de entrada que seria a main.
```

4. Rode `jps` e `jcmd -l` com um `jshell` aberto em outro terminal. O que cada um lista? Depois feche o `jshell` e rode
   de novo. ✅

```
➜  java-course git:(main) ✗ jps                     
7616 SonarLintServerCli
10659 JShellToolProvider
12231 Jps
10794 RemoteExecutionControl
5565 
7421 Main
➜  java-course git:(main) ✗ jcmd -l
7616 org.sonarsource.sonarlint.core.backend.cli.SonarLintServerCli
10659 jdk.jshell/jdk.internal.jshell.tool.JShellToolProvider
12393 jdk.jcmd/sun.tools.jcmd.JCmd -l
10794 jdk.jshell.execution.RemoteExecutionControl 44827
7421 com.intellij.idea.Main
➜  java-course git:(main) ✗ jps    
7616 SonarLintServerCli
12567 Jps
5565 
7421 Main
➜  java-course git:(main) ✗ jcmd -l
7616 org.sonarsource.sonarlint.core.backend.cli.SonarLintServerCli
12725 jdk.jcmd/sun.tools.jcmd.JCmd -l
7421 com.intellij.idea.Main
➜  java-course git:(main) ✗ 

```

## Referências

- [JEP 512 — Compact Source Files and Instance Main Methods](https://openjdk.org/jeps/512) — o JEP que finaliza os
  compact source files e o `main` de instância, e explica por que statements soltos foram descartados
- [JShell User's Guide (JDK 25)](https://docs.oracle.com/en/java/javase/25/jshell/) — guia oficial do REPL, com scripts
  e comandos
- [JDK 25 release notes](https://www.oracle.com/java/technologies/javase/25-relnote-issues.html) — o que mudou na LTS
  atual
- [asdf-java](https://github.com/halcyon/asdf-java) — plugin do asdf pra gerenciar JDKs
- [JEP 320 — Remove the Java EE and CORBA Modules](https://openjdk.org/jeps/320) — por que o JRE sumiu do JDK 11 em
  diante

## Próximo módulo

**Sintaxe Básica e Convenções** — estrutura de arquivo, `package`, imports,
`main`, convenções de nomenclatura e comentários no Java.

[→ 02 — Sintaxe Básica e Convenções](./02-sintaxe-basica.md)