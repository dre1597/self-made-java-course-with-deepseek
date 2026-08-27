void main() {
  IO.println(FindFirstLongName.execute(
      List.of("Ana", "Maria", "João", "Alexandre"),
      5
  ));

  IO.println(FindFirstLongName.execute(List.of(), 5));

  IO.println(FindFirstLongName.execute(
      List.of("Ana", "João"),
      10
  ));

  var names = new ArrayList<String>();
  names.add("Ana");
  names.add(null);
  names.add("Alexandre");

  IO.println(FindFirstLongName.execute(names, 5));
}

static class FindFirstLongName {
  private FindFirstLongName() {
  }

  static Optional<String> execute(List<String> names, int minLength) {
    return names.stream()
        .map(Optional::ofNullable)
        .filter(name -> name.map(value -> value.length() >= minLength).orElse(false))
        .flatMap(Optional::stream)
        .findFirst();
  }
}