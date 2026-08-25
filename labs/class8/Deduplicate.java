void main() {
  IO.println(Deduplicate.execute(List.of("a", "b", "c", "a", "b", "c")));
  IO.println(Deduplicate.execute(Arrays.asList("a", "b", null, "a", null, "c")));
  IO.println(Deduplicate.execute(List.of("a", "a", "a")));
  IO.println(Deduplicate.execute(List.of()));
}

static class Deduplicate {
  private Deduplicate() {
  }

  public static List<String> execute(List<String> items) {
    return items.stream().distinct().toList();
  }
}