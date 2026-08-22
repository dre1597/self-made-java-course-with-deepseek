# Curso de Java — Java Completo e Moderno (Agosto 2026)

Curso completo de Java, do básico ao avançado, focado em **Java 25 LTS**.
Cobre a linguagem de ponta a ponta: sintaxe, tipos, OOP, coleções, streams,
concorrência, JDBC, testes, padrões de projeto, performance e o que mudou nas
versões. Sem frameworks nessa fase: só a linguagem, o JDK e bibliotecas
famosas (Jackson, HikariCP, JUnit, Mockito, AssertJ, JMH).

## Como usar este curso

Cada módulo é autocontido: teoria, exemplos de código e exercícios **dentro do
próprio módulo**. Os mini projetos do final integram vários módulos de uma vez.

**Pré-requisito:** programação no geral (constante, classe, POO, loop não são
ensinados; só como funcionam no Java). O curso assume que você já conhece o
Java clássico e quer cobrir a linguagem de forma completa, do básico ao que
chegou depois do Java 17.

## Laboratório (`labs/`)

A pasta `labs/` guarda o código reproduzido e testado durante o curso,
separado por aula. Cada aula tem sua subpasta (`labs/class1/` pra aula 1,
`labs/class2/` pra aula 2, e assim por diante). É o caderno de código do
curso: exemplos reproduzidos, respostas de exercícios e experimentos soltos.

## Comparações com outras linguagens

O curso compara Java com o que você já conhece. A referência principal é o
**TypeScript** (linguagem do teu dia a dia), com paralelos diretos de
sintaxe, tipos e modelo mental. Quando o conceito pedir, entra também uma
comparada rápida com Ruby, Python, Scala, Kotlin, C#, PHP, C e C++.

## Índice

| #   | Módulo                                    | Arquivo                                             |
| --- | ----------------------------------------- | --------------------------------------------------- |
| 01  | Introdução e Setup                        | [01-introducao-e-setup.md](01-introducao-e-setup.md) |
| 02  | Sintaxe Básica e Convenções               | [02-sintaxe-basica.md](02-sintaxe-basica.md)         |
| 03  | Tipos de Dados e Variáveis                | [03-tipos-de-dados-e-variaveis.md](03-tipos-de-dados-e-variaveis.md) |
| 04  | Operadores                                | [04-operadores.md](04-operadores.md)                 |
| 05  | Estruturas de Controle                    | [05-estruturas-de-controle.md](05-estruturas-de-controle.md) |
| 06  | Métodos e Funções                         | [06-metodos-e-funcoes.md](06-metodos-e-funcoes.md)   |
| 07  | Strings e Text Blocks                     | [07-strings.md](07-strings.md)                       |
| 08  | Arrays e Coleções                         | [08-arrays-e-colecoes.md](08-arrays-e-colecoes.md)   |
| 09  | OOP — Classes, Herança e Interfaces       | [09-oop.md](09-oop.md)                               |
| 10  | Records, Enums e Sealed Classes           | [10-records-enums-sealed.md](10-records-enums-sealed.md) |
| 11  | Pattern Matching e Switch Moderno         | [11-pattern-matching.md](11-pattern-matching.md)     |
| 12  | Generics                                  | [12-generics.md](12-generics.md)                     |
| 13  | Lambdas e Streams                         | [13-lambdas-e-streams.md](13-lambdas-e-streams.md)   |
| 14  | Tratamento de Exceções                    | [14-excecoes.md](14-excecoes.md)                     |
| 15  | I/O e Arquivos                            | [15-io-e-arquivos.md](15-io-e-arquivos.md)           |
| 16  | Concorrência                              | [16-concorrencia.md](16-concorrencia.md)             |
| 17  | JDBC e Banco de Dados                     | [17-jdbc.md](17-jdbc.md)                             |
| 18  | Testes Automatizados (JUnit 5 + Mockito)  | [18-testes.md](18-testes.md)                         |
| 19  | HTTP Client e JSON                        | [19-http-client.md](19-http-client.md)               |
| 20  | Design Patterns                           | [20-design-patterns.md](20-design-patterns.md)       |
| 21  | Arquitetura e Boas Práticas               | [21-arquitetura.md](21-arquitetura.md)               |
| 22  | Build e Empacotamento (Gradle + intro Maven) | [22-build.md](22-build.md)                        |
| 23  | Performance e JVM                         | [23-performance.md](23-performance.md)               |
| 24  | Segurança                                 | [24-seguranca.md](24-seguranca.md)                   |
| 25  | Evolução do Java e Previews               | [25-evolucao-do-java.md](25-evolucao-do-java.md)     |

## Mini Projetos

| #   | Projeto                                   | Conceitos aplicados                          |
| --- | ----------------------------------------- | -------------------------------------------- |
| 1   | Gerenciador de Tarefas CLI                | Records, coleções, streams, JUnit + Mockito  |
| 2   | Servidor HTTP puro com JDBC               | `HttpServer` do JDK, JDBC, Jackson, repository pattern |

Os enunciados com código completo, testes e desafios extras estão na pasta
[`projetos/`](projetos/):

| #   | Enunciado                                      |
| --- | ---------------------------------------------- |
| 1   | [Gerenciador de Tarefas CLI](projetos/01-gerenciador-de-tarefas-cli.md) |
| 2   | [Servidor HTTP puro com JDBC](projetos/02-servidor-http-jdbc.md) |

## Exercícios

Os exercícios ficam **dentro de cada módulo**, no final, indicando os testes
que devem passar.

## Bibliotecas usadas (sem framework)

| Biblioteca   | Onde entra                    | Papel                          |
| ------------ | ----------------------------- | ------------------------------ |
| Jackson      | Módulo 19                     | Serialização JSON              |
| HikariCP     | Módulo 17                     | Connection pool                |
| JUnit 5      | Módulo 13 em diante           | Framework de testes            |
| Mockito      | Módulo 18                     | Mocks, spies e captors         |
| AssertJ      | Módulo 18                     | Asserções fluentes             |
| JMH          | Módulo 23                     | Benchmark de micro-desempenho  |

## Recursos recomendados

- [Dev.java](https://dev.java/) — portal oficial de aprendizado da Oracle
- [Documentação da plataforma Java SE 25](https://docs.oracle.com/en/java/javase/25/) — referência definitiva
- [JEP Index](https://openjdk.org/jeps/) — acompanhe as propostas de evolução da linguagem
- [JDK 25 release notes](https://www.oracle.com/java/technologies/javase/25-relnote-issues.html) — o que mudou na última LTS
- [Inside Java (Nicolai Parlog)](https://inside.java/) — artigos profundos sobre o Java moderno
- [Foojay](https://foojay.io/) — comunidade open source do Java
- [Baeldung](https://www.baeldung.com/) — tutoriais práticos, do básico ao avançado

### Comunidade brasileira

- **SouJava** — maior grupo de usuários Java da América Latina (soujava.org.br)
- **GURU-BR** — grupos de usuários Java espalhados pelo Brasil

---

**Versão do curso:** 1.0 — Agosto 2026 · Java 25 LTS