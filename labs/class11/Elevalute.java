void main() {
  var expression = new Sum(
      new Constant(2),
      new Sum(
          new Constant(3),
          new Constant(4)
      )
  );

  IO.println(Evaluate.execute(expression));
}

sealed interface Expression
    permits Constant, Sum {
}

record Constant(int value) implements Expression {
}

record Sum(Expression left, Expression right) implements Expression {
}

static class Evaluate {
  private Evaluate() {
  }

  static int execute(Expression expression) {
    return switch (expression) {
      case Constant(int value) -> value;
      case Sum(Expression left, Expression right) -> execute(left) + execute(right);
    };
  }
}