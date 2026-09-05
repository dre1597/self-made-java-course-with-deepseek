package class18;

class OrderService2 {
  private final OrderRepository repository;
  private final EmailSender emailSender;

  OrderService2(
      OrderRepository repository,
      EmailSender emailSender
  ) {
    this.repository = repository;
    this.emailSender = emailSender;
  }

  void confirmPayment(long orderId) {
    var order = repository.findById(orderId)
        .orElseThrow(() -> new IllegalArgumentException(
            "Pedido não encontrado: " + orderId
        ));

    var paidOrder = new Order(
        order.id(),
        order.total(),
        OrderStatus.PAID
    );

    repository.save(paidOrder);
    emailSender.sendConfirmation(paidOrder);
  }
}
