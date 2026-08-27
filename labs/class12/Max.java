void main() {
  IO.println(MaxValue.execute(List.of(10, 30, 20)));
  IO.println(MaxValue.execute(List.of(30, 20, 10)));
  IO.println(MaxValue.execute(List.<Integer>of()));

  var values = new ArrayList<Integer>();
  values.add(10);
  values.add(null);
  values.add(30);

  IO.println(MaxValue.execute(values));
}

static class MaxValue {
  private MaxValue() {
  }

  static <T extends Comparable<T>> T execute(List<T> values) {
    if (values.isEmpty()) {
      return null;
    }

    var max = values.getFirst();

    for (var value : values) {
      if (value.compareTo(max) > 0) {
        max = value;
      }
    }

    return max;
  }
}