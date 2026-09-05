package class18;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCalculatorTest {

  @Test
  void shouldRejectNullTotal() {
    assertThatThrownBy(() ->
        OrderCalculator.totalWithDiscount(null, new BigDecimal("0.1"))
    )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("total não pode ser nulo");
  }

  @Test
  void shouldRejectNegativeDiscount() {
    assertThatThrownBy(() ->
        OrderCalculator.totalWithDiscount(
            new BigDecimal("100"),
            new BigDecimal("-0.1")
        )
    )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("desconto não pode ser negativo");
  }

  @Test
  void shouldHandleZeroDiscount() {
    assertThat(OrderCalculator.totalWithDiscount(
        new BigDecimal("100"),
        BigDecimal.ZERO
    )).isEqualByComparingTo("100");
  }

  @Test
  void shouldHandleFullDiscount() {
    assertThat(OrderCalculator.totalWithDiscount(
        new BigDecimal("100"),
        BigDecimal.ONE
    )).isEqualByComparingTo("0");
  }

  @Test
  void shouldHandleZeroTotal() {
    assertThat(OrderCalculator.totalWithDiscount(
        BigDecimal.ZERO,
        new BigDecimal("0.5")
    )).isEqualByComparingTo("0");
  }

  @Test
  void shouldHandleHalfDiscount() {
    assertThat(OrderCalculator.totalWithDiscount(
        new BigDecimal("100"),
        new BigDecimal("0.5")
    )).isEqualByComparingTo("50");
  }
}