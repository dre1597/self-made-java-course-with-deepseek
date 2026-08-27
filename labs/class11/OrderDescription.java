void main() {
  var order = new Order(
      new Customer("Dré"),
      "A-1",
      new BigDecimal("99.90"),
      OrderStatus.PENDING
  );

  IO.println(OrderDescription.execute(order));
}

enum OrderStatus {
  PENDING, PAID, CANCELLED
}

record Customer(String name) {
}

record Order(
    Customer customer,
    String id,
    BigDecimal total,
    OrderStatus status
) {
}

static class OrderDescription {
  private OrderDescription() {
  }

  static String execute(Order order) {
    return switch (order) {
      case Order(Customer(String name), String id, _, OrderStatus status) -> id + " — " + name + " — " + status;
    };
  }
}