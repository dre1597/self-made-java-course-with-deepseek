void main() {
  var original = List.of("a", "b", "c");

  IO.println(Copy.execute(original));
}

static class Copy {
  private Copy() {
  }

  public static String execute(List<String> items) {
    var immutable = List.copyOf(items);
    var mutable = new ArrayList<>(items);

    try {
      immutable.add("d");
    } catch (UnsupportedOperationException e) {
      IO.println("List.copyOf: não permite add");
    }

    try {
      mutable.add("d");
      IO.println("ArrayList: permite add");
    } catch (UnsupportedOperationException e) {
      IO.println("ArrayList: não permite add");
    }

    return "imutável vs mutável";
  }
}