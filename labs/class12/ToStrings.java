void main() {
  IO.println(ToStrings.execute(List.of()));
  IO.println(ToStrings.execute(List.of(10, 20, 30)));
  IO.println(ToStrings.execute(List.of("abc", null, 42, true)));
}

static class ToStrings {
  private ToStrings() {
  }

  static List<String> execute(List<?> values) {
    return values.stream()
        .map(String::valueOf)
        .toList();
  }
}