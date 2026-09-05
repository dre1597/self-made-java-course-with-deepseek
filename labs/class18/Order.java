package class18;

import java.math.BigDecimal;

enum OrderStatus {
  PENDING,
  PAID
}

public record Order(
    long id,
    BigDecimal total,
    OrderStatus status
) {
}