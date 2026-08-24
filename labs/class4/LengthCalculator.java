void main() {
  IO.println(LengthCalculator.execute("hello"));
  IO.println(LengthCalculator.execute(List.of(1, 2, 3)));
  IO.println(LengthCalculator.execute(123));
  IO.println(LengthCalculator.execute(null));
}


class LengthCalculator {
  private LengthCalculator() {
  }

  public static int execute(Object object) {
    if (object instanceof String string) {
      return string.length();
    }
    if (object instanceof List<?> list) {
      return list.size();
    }
    return -1;
  }
}