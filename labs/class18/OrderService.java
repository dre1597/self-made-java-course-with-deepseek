package class18;

class OrderService {
  private final OrderRepository repository;

  OrderService(OrderRepository repository) {
    this.repository = repository;
  }

  void confirmPayment(long orderId) {
    var order = repository.findById(orderId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Pedido não encontrado: " + orderId
        ));

    repository.save(new Order(
        order.id(),
        order.total(),
        OrderStatus.PAID
    ));
  }
}