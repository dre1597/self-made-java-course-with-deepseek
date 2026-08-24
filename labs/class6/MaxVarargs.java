void main() {
  IO.println(MaxVaargs.execute(1, 2, 3));
  IO.println(MaxVaargs.execute(3, 2, 1));
  IO.println(MaxVaargs.execute(-3, -5, 1));
  IO.println(MaxVaargs.execute());
}

class MaxVaargs {
  private MaxVaargs() {
  }

  public static int execute(int... numbers) {
    if (numbers.length == 0) {
      throw new IllegalArgumentException("Cannot find max of empty array");
    }

    int max = Integer.MIN_VALUE;
    for (int number : numbers) {
      if (number > max) {
        max = number;
      }
    }

    return max;
  }
}