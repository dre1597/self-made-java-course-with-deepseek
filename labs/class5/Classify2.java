void main() {
  IO.println(Classify2.execute(1));
  IO.println(Classify2.execute(3));
  IO.println(Classify2.execute(4));
  IO.println(Classify2.execute(7));
  IO.println(Classify2.execute(8));
  IO.println(Classify2.execute(10));
  IO.println(Classify2.execute(0));
  IO.println(Classify2.execute(-5));
}

static class Classify2 {
  private Classify2() {
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