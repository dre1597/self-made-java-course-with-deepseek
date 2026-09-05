package class18;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCalculatorParameterizedTest {

  static Stream<Object[]> invalidInputs() {
    return Stream.of(
        new Object[]{
            null,
            new BigDecimal("0.1"),
            "total não pode ser nulo"
        },
        new Object[]{
            new BigDecimal("100"),
            new BigDecimal("-0.1"),
            "desconto não pode ser negativo"
        }
    );
  }

  @ParameterizedTest
  @CsvSource({
      "100, 0,   100",
      "100, 1,   0",
      "0,   0.5, 0",
      "100, 0.5, 50"
  })
  void shouldCalculateDiscount(
      String total,
      String discount,
      String expected
  ) {
    var result = OrderCalculator.totalWithDiscount(
        new BigDecimal(total),
        new BigDecimal(discount)
    );

    assertThat(result).isEqualByComparingTo(expected);
  }

  @ParameterizedTest
  @MethodSource("invalidInputs")
  void shouldRejectInvalidInputs(
      BigDecimal total,
      BigDecimal discount,
      String message
  ) {
    assertThatThrownBy(() ->
        OrderCalculator.totalWithDiscount(total, discount)
    )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(message);
  }
}