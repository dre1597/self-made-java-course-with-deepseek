void main() {
  IO.println(DescribeValue.execute("Java"));
  IO.println(DescribeValue.execute(101));
  IO.println(DescribeValue.execute(100));
  IO.println(DescribeValue.execute(null));
  IO.println(DescribeValue.execute(10.5));
}

public class DescribeValue {
  private DescribeValue() {
  }

  public static String execute(Object value) {
    return switch (value) {
      case null -> "null";
      case String s -> "string: " + s.length();
      case Integer i when i > 100 -> "big number";
      case Integer i -> "small number";
      default -> "other: " + value.getClass().getName();
    };
  }
}