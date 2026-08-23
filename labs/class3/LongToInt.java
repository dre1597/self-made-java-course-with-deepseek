void main() {
  var intMaxValue = LongToInt.execute(Integer.MAX_VALUE);
  IO.println(intMaxValue);

//  var closeToMaxValuePlus = LongToInt.execute((long) Integer.MAX_VALUE + 1);
//  IO.println(closeToMaxValuePlus);

  var closeToMaxValueMinus = LongToInt.execute((long) Integer.MAX_VALUE - 1);
  IO.println(closeToMaxValueMinus);

  var intMinValue = LongToInt.execute(Integer.MIN_VALUE);
  IO.println(intMinValue);

  var closeToMinValuePlus = LongToInt.execute((long) Integer.MIN_VALUE + 1);
  IO.println(closeToMinValuePlus);

  var closeToMinValueMinus = LongToInt.execute((long) Integer.MIN_VALUE - 1);
  IO.println(closeToMinValueMinus);
}

class LongToInt {
  private LongToInt() {
  }

  public static int execute(long number) {
    if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
      throw new ArithmeticException("Number is out of int range: " + number);
    }

    return (int) number;
  }
}