String greeting = "Hello";

String greet(String name) {
  return greeting + ", " + name;
}

void main() {
  IO.println(greet("world"));
}