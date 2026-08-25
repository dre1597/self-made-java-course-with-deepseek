void main() {
  IO.println(Count.execute(List.of(1, 1, 1, 2, 5)));
  IO.println(Count.execute(List.of(1, 2, 3)));
  IO.println(Count.execute(List.of()));
}

static class Count {
  private Count() {
  }

  public static Map<Integer, Long> execute(List<Integer> items) {
    return items.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
  }
}