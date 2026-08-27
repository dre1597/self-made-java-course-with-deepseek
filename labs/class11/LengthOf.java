void main() {
  IO.println(LengthOf.execute("abc"));
  IO.println(LengthOf.execute(List.of(1, 2)));
  IO.println(LengthOf.execute(Map.of("a", 1, "b", 2)));
  IO.println(LengthOf.execute(null));
  IO.println(LengthOf.execute(42));
}

static class LengthOf {
  private LengthOf() {
  }

  static int execute(Object value) {
    return switch (value) {
      case String text -> text.length();
      case List<?> list -> list.size();
      case Map<?, ?> map -> map.size();
      default -> -1;
    };
  }
}