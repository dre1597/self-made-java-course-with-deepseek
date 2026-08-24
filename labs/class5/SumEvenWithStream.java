import java.util.stream.IntStream;

void main() {
  IO.println(SumEvenWithStream.execute(10));
  IO.println(SumEvenWithStream.execute(0));
  IO.println(SumEvenWithStream.execute(-10));
}

public class SumEvenWithStream {
  private SumEvenWithStream() {
  }

  public static int execute(int limit) {
    return IntStream.rangeClosed(0, limit)
        .filter(i -> i % 2 == 0)
        .sum();
  }
}