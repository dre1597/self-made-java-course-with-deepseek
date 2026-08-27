void main() {
  var values = List.of("Java", "25", "Streams");

  IO.println(Concat.execute(values));
  IO.println(Concat.executeJoining(values));

  IO.println(Concat.execute(List.of()));
  IO.println(Concat.executeJoining(List.of()));
}

static class Concat {
  private Concat() {
  }

  static String execute(List<String> values) {
    return values.stream()
        .reduce((left, right) -> left + " - " + right)
        .orElse("");
  }

  static String executeJoining(List<String> values) {
    return String.join(" - ", values);
  }
}