void main() {
  IO.println(CalculateShipping.execute(1.5));
  IO.println(CalculateShipping.execute(10, "USA"));
}

class CalculateShipping {
  private CalculateShipping() {
  }

  public static double execute(double weight) {
    return weight * 10;
  }

  public static double execute(double weight, String destination) {
    return weight * 15;
  }
}