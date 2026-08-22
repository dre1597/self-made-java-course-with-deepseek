# 11 — Pattern Matching e Switch Moderno

Pattern matching no Java: `instanceof` com pattern, `switch` sobre tipos,
guardas e destructuring de records. Tudo permanente desde o JDK 21.

## `instanceof` com pattern (JDK 16)

O cast manual some quando o pattern declara a variável no próprio teste:

```java
// antes
if (obj instanceof String) {
    String text = (String) obj;
    return text.length();
}

// depois
if (obj instanceof String text) {
    return text.length();
}
```

A variável `text` nasce já com o tipo `String`, sem cast. O escopo dela
termina quando o pattern deixa de ser garantido:

```java
if (obj instanceof String text) {
    return text.length();       // text vale aqui
}
return 0;                       // text não existe mais aqui
```

## `switch` sobre tipos (JDK 21)

O `switch` testa padrões, não só igualdade de constante:

```java
String describe(Object value) {
    return switch (value) {
        case String s -> "string de " + s.length() + " caracteres";
        case Integer i -> "inteiro " + i;
        case List<?> list -> "lista de " + list.size() + " itens";
        case null -> "nulo";
        default -> "tipo desconhecido";
    };
}
```

O selector agora aceita **qualquer tipo de referência**. Antes (JDK 7) eram só
`byte`, `short`, `char`, `int`, `String` e `enum`.

Exigência: um `switch` de patterns precisa ser **exaustivo**. Com `default`
ou com uma sealed hierarchy completa (módulo 10), você cobre tudo. Sem uma das
duas, não compila.

## Guardas com `when`

```java
String describe(Object value) {
    return switch (value) {
        case Integer i when i > 1000 -> "inteiro grande";
        case Integer i -> "inteiro " + i;
        case String s when s.length() > 10 -> "string longa";
        case String s -> "string " + s;
        default -> "outro";
    };
}
```

A guarda é uma condição `boolean` extra que o valor precisa satisfazer além de
bater o pattern. Importante: a ordem importa. A guarda pode falhar e o valor
"desce" pro próximo case. Um `case Integer i ->` sem guarda depois de um com
guarda funciona; o inverso (case genérico antes de específico) é erro de
dominância no compilador.

Comparação com TS: o `when` lembra o `if` dentro do `case` do TS, mas aqui a
guarda faz parte do pattern e o fluxo é declarativo.

## Record patterns (JDK 21)

O `instanceof` e o `switch` destruturam records no próprio pattern:

```java
record Address(String street, String city, String zip) {}
record User(String name, Address address) {}
```

```java
if (obj instanceof User(String name, Address(String street, String city, String zip))) {
    System.out.println(name + " mora em " + city);
}
```

`User(String name, Address(...))` desempacota o record em variáveis. Dá pra
aninhar: `Address(...)` destrutura o record interno na mesma linha.

No `switch`:

```java
String city = switch (user) {
    case User(String name, Address(String street, String city, String zip)) -> city;
    default -> "sem endereço";
};
```

### `var` nos componentes

Os tipos no pattern são opcionais: com `var` o compilador infere do record.

```java
if (obj instanceof User(var name, Address(var street, var city, var zip))) {
    System.out.println(name + " mora em " + city);
}
```

`User(var name, ...)` equivale a `User(String name, ...)` quando o componente é
`String`. O `var` deixa o pattern mais curto e quebra menos quando o tipo do
componente muda. Em `switch`, a inferência vale pra record patterns genéricos
também.

### Record patterns fora do `for`

No preview (JDK 19/20) dava pra usar record pattern no enhanced `for`; o JDK 21
**removeu** isso antes de finalizar (JEP 440). Código que tentar
`for (User(var name, ...) : users)` não compila no Java 21+. Se você viu isso
em tutorial antigo, desconfie da versão.

### Records, sealed e exaustividade juntos

O destructuring combina com sealed: um switch que cobre os subtipos de um
sealed hierarchy dispensa o `default`.

```java
sealed interface Shape permits Circle, Square, Rectangle {}

record Circle(double radius) implements Shape {}
record Square(double side) implements Shape {}
record Rectangle(double width, double height) implements Shape {}
```

```java
double area(Shape shape) {
    return switch (shape) {
        case Circle(double radius) -> Math.PI * radius * radius;
        case Square(double side) -> side * side;
        case Rectangle(double width, double height) -> width * height;
    };
}
```

Se `Circle` mudar pra ter dois campos, o pattern `Circle(double radius)` para
de compilar e você é obrigado a atualizar. O compilador é o guarda-costas.

### `MatchException` — o caso que parece impossível

O switch exaustivo em compile-time ainda pode falhar em runtime quando a
hierarquia muda **depois** da compilação. Se `Shape` era sealed com três
subtipos e o switch cobria os três, mas um novo subtipo é compilado e
adicionado depois (ex.: deploy com classes de versões diferentes), o switch
lança `MatchException` em vez de devolver valor. Raro, mas explica o erro que
parece "não deveria acontecer".

## `case null`

Desde o JDK 21:

```java
String describe(Object value) {
    return switch (value) {
        case null -> "nulo";
        case String s -> "string";
        default -> "outro";
    };
}
```

Antes, `null` no selector estourava NPE. Agora `case null` é explícito.

## Armadilhas

- **Pattern de tipo não aceita primitivo**: `case int i ->` não compila.
  Use o wrapper `Integer` (primitivos em patterns são preview no JDK 25,
  JEP 507, ainda não permanente).
- **`when` não é `if` de escape**: se nenhum case casa (guarda falha em
  todos), o `default` roda. Exaustividade conta guardas como cobertura
  parcial.
- **Dominância**: um pattern que é subconjunto de outro precisa vir depois.
  `case Object o` antes de `case String s` é erro de compilação.

## O que mudou entre versões

| Feature | Versão | Situação |
| ------- | ------ | -------- |
| `instanceof` com pattern | JDK 16 | Permanente |
| Switch expression | JDK 14 | Permanente |
| Pattern matching no switch | JDK 21 | Permanente |
| Record patterns | JDK 21 | Permanente |
| `case null` | JDK 21 | Permanente |
| Primitivos em patterns | JDK 23 | Preview (3ª no 25) |

## Comparação com TypeScript

| Conceito | Java | TypeScript |
| -------- | ---- | ---------- |
| Narrowing | `instanceof` + pattern | `typeof`, `instanceof`, `in` |
| Switch sobre tipo | `switch` + pattern | `switch` só igualdade |
| Guarda | `when` | `if` dentro do case |
| Destructuring | record pattern | destructuring de objeto |
| Exaustividade | compilador exige | não existe |

No TS o narrowing funciona, mas a exaustividade é manual. No Java o compilador
te força a cobrir todos os casos de um sealed ou a incluir `default`.

## Exercícios

1. Escreva `lengthOf(Object value)` que devolve o tamanho se for `String`,
   `List` ou `Map`, e `-1` caso contrário. Teste com `null` e com tipos
   inesperados (`Integer`, por exemplo).
2. Escreva um `switch` com guardas que classifica um `Integer`: "negativo",
   "zero", "pequeno" (1 a 100), "grande" (acima). Teste a ordem dos cases:
   o que acontece se o case genérico vier antes dos específicos?
3. Crie um `record Order(String id, BigDecimal total, OrderStatus status)` e
   um `switch` que devolve a descrição usando record pattern que destrutura
   `id` e `status`. Depois aninhe um record `Customer` dentro de `Order` e
   destrutura os dois níveis.
4. Escreva uma `sealed interface Expression` com `Constant(int value)` e
   `Sum(Expression left, Expression right)`. Implemente `evaluate()` com
   `switch` exaustivo e record patterns recursivos. Teste `Sum(Constant(2),
   Sum(Constant(3), Constant(4)))`.

## Referências

- [JEP 441 — Pattern Matching for switch](https://openjdk.org/jeps/441) — pattern labels, guardas, `null` case e exaustividade (JDK 21)
- [JEP 440 — Record Patterns](https://openjdk.org/jeps/440) — destructuring de records e patterns aninhados (JDK 21)
- [Pattern Matching with switch (Java Language Updates)](https://docs.oracle.com/en/java/javase/26/language/pattern-matching-switch.html) — guardas, dominância e `null` na doc oficial
- [Record Patterns (Java Language Updates)](https://docs.oracle.com/en/java/javase/26/language/record-patterns.html) — patterns de record com `var` e genéricos

## Próximo módulo

**Generics** — type parameters, wildcards, bounded types e o comportamento em
runtime.

[→ 12 — Generics](./12-generics.md)