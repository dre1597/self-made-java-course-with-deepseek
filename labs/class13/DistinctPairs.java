void main() {
  IO.println(DistinctPairs.execute(List.of(
      List.of(5, 2, 3),
      List.of(2, 7, 5),
      List.of(1, 3, 4)
  )));

  IO.println(DistinctPairs.execute(List.of()));
  IO.println(DistinctPairs.execute(List.of(
      List.of(3, 3, 2),
      List.of(2, 1, 1)
  )));
}

static class DistinctPairs {
  private DistinctPairs() {
  }

  static List<Integer> execute(List<List<Integer>> values) {
    return values.stream()
        .flatMap(List::stream)
        .distinct()
        .sorted()
        .toList();
  }
}