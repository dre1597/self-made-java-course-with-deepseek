void main() {
  IO.println(Classify.execute(1));
  IO.println(Classify.execute(3));
  IO.println(Classify.execute(4));
  IO.println(Classify.execute(7));
  IO.println(Classify.execute(8));
  IO.println(Classify.execute(10));
  IO.println(Classify.execute(0));
  IO.println(Classify.execute(-5));
}

class Classify {
  private Classify() {
  }

  public static String execute(int priority) {
    return switch (priority) {
      case 1, 2, 3 -> "low";
      case 4, 5, 6, 7 -> "medium";
      case 8, 9, 10 -> "high";
      default -> "invalid";
    };
  }
}