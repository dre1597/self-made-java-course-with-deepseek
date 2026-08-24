void main() {
  IO.println(SafeDivide.execute(10, 4));
  IO.println(SafeDivide.execute(-10, 4));
  IO.println(SafeDivide.execute(7, 0));
  IO.println(SafeDivide.execute(0, 0));
}

class SafeDivide {
  private SafeDivide() {
  }

  static double execute(int dividend, int divisor) {
    if (divisor == 0) {
      return Double.NaN;
    }
    return (double) dividend / divisor;
  }
}