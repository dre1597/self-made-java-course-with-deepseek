void main(String[] args) {
  if (args.length == 0) {
    throw new IllegalArgumentException("Please provide a value");
  }

  var result = MultiplyByTwo.execute(Integer.parseInt(args[0]));
//  var result = MultiplyByTwo.execute(null);

  IO.println(result);

}

class MultiplyByTwo {
  private MultiplyByTwo() {
    /* This utility class should not be instantiated */
  }

  public static Integer execute(Integer number) {
    if (number == null) {
      throw new IllegalArgumentException("Please provide a value");
    }

    if (number > Integer.MAX_VALUE / 2) {
      throw new ArithmeticException("Value is too large");
    }

    if (number < Integer.MIN_VALUE / 2) {
      throw new ArithmeticException("Value is too small");
    }

    return number * 2;
  }
}