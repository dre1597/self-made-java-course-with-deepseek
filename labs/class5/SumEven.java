void main() {
  IO.println(SumEven.execute(10));
  IO.println(SumEven.execute(0));
  IO.println(SumEven.execute(-10));
}

public class SumEven {
  private SumEven() {
  }

  public static int execute(int limit) {
    var sum = 0;

    for (var i = 0; i <= limit; i++) {
      if (i % 2 != 0) {
        continue;
      }
      sum += i;
    }

    return sum;
  }
}