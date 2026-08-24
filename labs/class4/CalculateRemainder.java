void main() {
  IO.println(CalculateRemainder.execute(10, 3));
  IO.println(CalculateRemainder.execute(-10, -30));
  IO.println(CalculateRemainder.execute(10, -30));
  IO.println(CalculateRemainder.execute(-10, 30));
//  IO.println(CalculateRemainder.execute(10, 0));
  IO.println(CalculateRemainder.execute(CalculateRemainder.execute(Integer.MIN_VALUE, -1));
  IO.println(CalculateRemainder.execute(CalculateRemainder.execute(Integer.MAX_VALUE , -1));
  IO.println(CalculateRemainder.execute(10, -1));
}

class CalculateRemainder {
  private CalculateRemainder() {
  }

  public static int execute(int dividend, int divisor) {
    if (divisor == 0) {
      throw new ArithmeticException("Division by zero is not allowed");
    }

    return dividend % divisor;
  }
}