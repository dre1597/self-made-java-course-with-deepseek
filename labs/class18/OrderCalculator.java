package class18;

import java.math.BigDecimal;

class OrderCalculator {
  private OrderCalculator() {
  }

  static BigDecimal totalWithDiscount(BigDecimal total, BigDecimal discount) {
    if (total == null) {
      throw new IllegalArgumentException("total não pode ser nulo");
    }

    if (discount.signum() < 0) {
      throw new IllegalArgumentException("desconto não pode ser negativo");
    }

    return total.multiply(BigDecimal.ONE.subtract(discount));
  }
}