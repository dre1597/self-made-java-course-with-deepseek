double totalArea(List<Shape> shapes) {
  double total = 0;
  for (Shape shape : shapes) {
    total += shape.area();
  }
  return total;
}

void main() {
  var list = List.of(new Circle(1), new Rectangle(2, 3));
  IO.println(totalArea(list));
}

interface Shape {
  double area();
}

static class Circle implements Shape {
  private final int radius;

  public Circle(final int radius) {
    this.radius = radius;
  }

  @Override
  public double area() {
    return Math.PI * Math.pow(radius, 2);
  }
}

static class Rectangle implements Shape {
  private final int length;
  private final int width;

  public Rectangle(final int length, final int width) {
    this.length = length;
    this.width = width;
  }

  @Override
  public double area() {
    return length * width;
  }
}