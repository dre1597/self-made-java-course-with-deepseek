void main() {
  List<Integer> integers = new ArrayList<>();

  Add.executeExtends(integers);
  IO.println(integers);

  Add.executeSuper(integers);
  IO.println(integers);

  List<Number> numbers = new ArrayList<>();

  Add.executeSuper(numbers);
  IO.println(numbers);
}

static class Add {
  private Add() {
  }

  static void executeExtends(List<? extends Number> values) {
//    values.add(10); // ERRO: não é possível adicionar
  }

  static void executeSuper(List<? super Integer> values) {
    values.add(10);
  }
}