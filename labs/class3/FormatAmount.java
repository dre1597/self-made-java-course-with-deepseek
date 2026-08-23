void main() {
  IO.println(FormatAmount.execute(0.1 + 0.2));
  var firstValue = new BigDecimal("0.1");
  var secondValue = new BigDecimal("0.2");
  IO.println(FormatAmount.execute(firstValue.add(secondValue)));
}

class FormatAmount {
  private FormatAmount() {

  }

  public static String execute(double value) {
    IO.println("Valor Bruto: " + value);
    return String.format("%.2f", value);

  }

  public static String execute(BigDecimal value) {
    IO.println("Valor Bruto: " + value);
    return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }
}