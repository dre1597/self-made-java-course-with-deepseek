void main() {
  IO.println(Classify.execute(-5));
  IO.println(Classify.execute(0));
  IO.println(Classify.execute(50));
  IO.println(Classify.execute(200));
}

static class Classify {
  private Classify() {
  }

  static String execute(Integer value) {
    return switch (value) {
      case Integer number when number < 0 -> "negativo";
      case Integer number when number == 0 -> "zero";
      case Integer number when number <= 100 -> "pequeno";
      case Integer _ -> "grande";
    };
  }
}