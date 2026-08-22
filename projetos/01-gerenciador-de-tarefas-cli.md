# Projeto 01: Gerenciador de Tarefas CLI

## Objetivo

Uma CLI de gerenciamento de tarefas em Java puro (sem framework): adicionar,
listar, concluir, reabrir e remover tarefas. Exercita os módulos 10 (records),
08 (coleções), 13 (streams) e 18 (JUnit + Mockito).

## Estrutura de Arquivos

```
projetos/gerenciador-de-tarefas/
    build.gradle.kts
    settings.gradle.kts
    src/main/java/br/com/exemplo/tasks/
        Task.java
        TaskRepository.java
        InMemoryTaskRepository.java
        TaskService.java
        TaskCli.java
    src/test/java/br/com/exemplo/tasks/
        TaskTest.java
        TaskServiceTest.java
```

O domínio (`Task`), o contrato de dados (`TaskRepository`) e a lógica
(`TaskService`) são independentes da CLI. Trocar o repositório por um que
grava em arquivo não toca no serviço nem na CLI.

## Como Executar

```bash
cd projetos/gerenciador-de-tarefas
./gradlew run
# ou, depois do fat jar (módulo 22):
./gradlew shadowJar && java -jar build/libs/gerenciador-de-tarefas-all.jar
./gradlew test   # roda os testes
```

## Código Completo

### `settings.gradle.kts`

```kotlin
rootProject.name = "gerenciador-de-tarefas"
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
    testImplementation(platform("org.junit:junit-bom:5.14.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

application {
    mainClass = "br.com.exemplo.tasks.TaskCli"
}

tasks.test {
    useJUnitPlatform()
}
```

### `src/main/java/br/com/exemplo/tasks/Task.java`

```java
package br.com.exemplo.tasks;

public record Task(long id, String description, boolean completed) {

    public Task {
        if (id <= 0) {
            throw new IllegalArgumentException("id deve ser positivo");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("descrição não pode ser vazia");
        }
    }

    public Task complete() {
        return new Task(id, description, true);
    }

    public Task reopen() {
        return new Task(id, description, false);
    }
}
```

O record garante que tarefa sem descrição ou com id inválido nem existe. A
transição de estado devolve outra instância; a original fica intacta.

### `src/main/java/br/com/exemplo/tasks/TaskRepository.java`

```java
package br.com.exemplo.tasks;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {

    long nextId();

    Task save(Task task);

    Optional<Task> findById(long id);

    List<Task> findAll();

    boolean deleteById(long id);
}
```

### `src/main/java/br/com/exemplo/tasks/InMemoryTaskRepository.java`

```java
package br.com.exemplo.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemoryTaskRepository implements TaskRepository {

    private final Map<Long, Task> tasks = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public long nextId() {
        return nextId.getAndIncrement();
    }

    @Override
    public Task save(Task task) {
        tasks.put(task.id(), task);
        return task;
    }

    @Override
    public Optional<Task> findById(long id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public List<Task> findAll() {
        return new ArrayList<>(tasks.values());
    }

    @Override
    public boolean deleteById(long id) {
        return tasks.remove(id) != null;
    }
}
```

O `ConcurrentHashMap` e o `AtomicLong` deixam o repositório seguro se a CLI
um dia usar virtual threads (módulo 16). Pra esse projeto, um `HashMap`
simples bastava; o custo é zero e fica pronto pra evoluir.

### `src/main/java/br/com/exemplo/tasks/TaskService.java`

```java
package br.com.exemplo.tasks;

import java.util.Comparator;
import java.util.List;

public class TaskService {

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public Task add(String description) {
        long id = repository.nextId();
        return repository.save(new Task(id, description, false));
    }

    public List<Task> listAll() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Task::id))
                .toList();
    }

    public List<Task> listPending() {
        return listAll().stream()
                .filter(task -> !task.completed())
                .toList();
    }

    public List<Task> listCompleted() {
        return listAll().stream()
                .filter(Task::completed)
                .toList();
    }

    public Task complete(long id) {
        Task task = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("tarefa não encontrada: " + id));
        return repository.save(task.complete());
    }

    public Task reopen(long id) {
        Task task = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("tarefa não encontrada: " + id));
        return repository.save(task.reopen());
    }

    public boolean remove(long id) {
        return repository.deleteById(id);
    }
}
```

### `src/main/java/br/com/exemplo/tasks/TaskCli.java`

```java
package br.com.exemplo.tasks;

import java.util.List;
import java.util.Scanner;
import java.util.function.LongFunction;

public class TaskCli {

    private final TaskService service;

    public TaskCli(TaskService service) {
        this.service = service;
    }

    public static void main(String[] args) {
        TaskRepository repository = new InMemoryTaskRepository();
        TaskService service = new TaskService(repository);
        new TaskCli(service).run();
    }

    private void run() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("Gerenciador de Tarefas");
            System.out.println("Comandos: add <desc>, list, list --pending, "
                    + "list --completed, complete <id>, reopen <id>, remove <id>, exit");
            while (true) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();
                if (line.equals("exit")) {
                    break;
                }
                dispatch(line);
            }
        }
    }

    private void dispatch(String line) {
        String[] parts = line.split(" ", 2);
        switch (parts[0].toLowerCase()) {
            case "add" -> add(parts);
            case "list" -> list(parts);
            case "complete" -> complete(parts);
            case "reopen" -> reopen(parts);
            case "remove" -> remove(parts);
            default -> System.out.println("comando desconhecido: " + parts[0]);
        }
    }

    private void add(String[] parts) {
        if (parts.length < 2) {
            System.out.println("uso: add <descrição>");
            return;
        }
        Task task = service.add(parts[1]);
        System.out.println("criada #" + task.id() + ": " + task.description());
    }

    private void list(String[] parts) {
        String filter = parts.length > 1 ? parts[1] : "";
        List<Task> tasks = switch (filter) {
            case "--pending" -> service.listPending();
            case "--completed" -> service.listCompleted();
            default -> service.listAll();
        };
        if (tasks.isEmpty()) {
            System.out.println("nenhuma tarefa");
            return;
        }
        tasks.forEach(task -> System.out.printf(
                "[%s] #%d %s%n", task.completed() ? "x" : " ", task.id(), task.description()));
    }

    private void complete(String[] parts) {
        withId(parts, service::complete, "concluída");
    }

    private void reopen(String[] parts) {
        withId(parts, service::reopen, "reaberta");
    }

    private void remove(String[] parts) {
        withId(parts, id -> {
            boolean removed = service.remove(id);
            return removed ? "removida" : "não encontrada";
        }, "removida");
    }

    private void withId(String[] parts, LongFunction<String> action, String verb) {
        if (parts.length < 2) {
            System.out.println("uso: " + parts[0] + " <id>");
            return;
        }
        try {
            long id = Long.parseLong(parts[1]);
            System.out.println("tarefa " + id + " " + action.apply(id));
        } catch (NumberFormatException e) {
            System.out.println("id inválido: " + parts[1]);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        }
    }
}
```

O `switch` em expressão (módulo 11) e a interface funcional `LongFunction`
fazem o mapeamento comando→serviço sem `if` encadeado.

## Como Executar

```bash
cd projetos/gerenciador-de-tarefas
./gradlew run
```

Testes:

```bash
./gradlew test
```

## Testes incluídos

### `src/test/java/br/com/exemplo/tasks/TaskTest.java`

```java
package br.com.exemplo.tasks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskTest {

    @Test
    @DisplayName("complete e reopen devolvem cópia com estado trocado")
    void togglesStateInNewInstance() {
        Task task = new Task(1, "Estudar streams", false);

        Task completed = task.complete();
        Task reopened = completed.reopen();

        assertThat(completed.completed()).isTrue();
        assertThat(reopened.completed()).isFalse();
        assertThat(task.completed()).isFalse();
        assertThat(task).isNotSameAs(completed).isNotSameAs(reopened);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    @DisplayName("id não positivo lança")
    void rejectsNonPositiveId(long id) {
        assertThatThrownBy(() -> new Task(id, "x", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("descrição em branco lança")
    void rejectsBlankDescription(String description) {
        assertThatThrownBy(() -> new Task(1, description, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("complete e reopen preservam id e descrição")
    void preservesIdentity() {
        Task task = new Task(9, "Comprar pão", false);

        Task completed = task.complete();

        assertThat(completed.id()).isEqualTo(9);
        assertThat(completed.description()).isEqualTo("Comprar pão");
    }
}
```

### `src/test/java/br/com/exemplo/tasks/TaskServiceTest.java`

```java
package br.com.exemplo.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(repository);
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        @DisplayName("cria tarefa pendente com id vindo do repositório")
        void createsPendingTask() {
            when(repository.nextId()).thenReturn(7L);
            when(repository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            Task created = service.add("Estudar streams");

            assertThat(created.id()).isEqualTo(7);
            assertThat(created.description()).isEqualTo("Estudar streams");
            assertThat(created.completed()).isFalse();
            verify(repository).save(created);
        }
    }

    @Nested
    @DisplayName("list")
    class List {

        @Test
        @DisplayName("ordena por id e separa por status")
        void ordersAndFilters() {
            when(repository.findAll()).thenReturn(List.of(
                    new Task(2, "b", true),
                    new Task(1, "a", false),
                    new Task(3, "c", false)));

            assertThat(service.listAll())
                    .extracting(Task::id)
                    .containsExactly(1L, 2L, 3L);
            assertThat(service.listPending())
                    .extracting(Task::id)
                    .containsExactly(1L, 3L);
            assertThat(service.listCompleted())
                    .extracting(Task::id)
                    .containsExactly(2L);
        }

        @Test
        @DisplayName("lista vazia não estoura")
        void emptyList() {
            when(repository.findAll()).thenReturn(List.of());

            assertThat(service.listAll()).isEmpty();
            assertThat(service.listPending()).isEmpty();
            assertThat(service.listCompleted()).isEmpty();
        }
    }

    @Nested
    @DisplayName("complete e reopen")
    class Complete {

        @Test
        @DisplayName("conclui tarefa existente")
        void completesExistingTask() {
            Task pending = new Task(1, "x", false);
            when(repository.findById(1L)).thenReturn(Optional.of(pending));
            when(repository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

            Task completed = service.complete(1L);

            assertThat(completed.completed()).isTrue();
            verify(repository).save(new Task(1, "x", true));
        }

        @Test
        @DisplayName("lança para id inexistente")
        void throwsForMissingTask() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.complete(99L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    @DisplayName("remove")
    class Remove {

        @Test
        @DisplayName("devolve true quando removeu")
        void removesExisting() {
            when(repository.deleteById(1L)).thenReturn(true);

            assertThat(service.remove(1L)).isTrue();
        }

        @Test
        @DisplayName("devolve false quando não existia")
        void missingTask() {
            when(repository.deleteById(99L)).thenReturn(false);

            assertThat(service.remove(99L)).isFalse();
        }
    }
}
```

## Desafios Extras

### 1. Persistir em arquivo JSON
Implemente `FileTaskRepository` que grava a lista num JSON (com Jackson 3,
módulo 19) a cada operação e carrega na criação. Troque a instância no `main`
e veja que serviço e CLI não mudam.

### 2. Prioridade e prazo
Adicione campos `priority` (enum `LOW`, `MEDIUM`, `HIGH`) e `dueDate`
(`LocalDate`) ao `Task`. Ajuste a ordenação pra prioridade primeiro, depois
prazo, e crie um comando `list --overdue`.

### 3. Teste com virtual threads
Simule 10.000 tarefas adicionadas em paralelo com virtual threads (módulo 16)
e prove que nenhum id repete e nenhuma tarefa some.

## Conceitos Aplicados

- `record` pra estado imutável e transições que devolvem cópia
- Interface `TaskRepository` + implementação em memória (módulo 21, D)
- `switch` em expressão e `List` de streams com filtro/ordenação
- JUnit 5 (`@Nested`, `@DisplayName`, parametrizado) + Mockito (mock de
  repositório) + AssertJ
- Injeção por construtor e composite root no `main`