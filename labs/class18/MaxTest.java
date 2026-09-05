package class18;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaxTest {

  @Test
  void shouldReturnEmptyForEmptyList() {
    assertThat(Max.execute(List.of()))
        .isEmpty();
  }

  @Test
  void shouldReturnSingleElement() {
    assertThat(Max.execute(List.of(42)))
        .contains(42);
  }

  @Test
  void shouldHandleIntegerMaxValue() {
    assertThat(Max.execute(List.of(
        10,
        Integer.MAX_VALUE,
        100
    ))).contains(Integer.MAX_VALUE);
  }

  @Test
  void shouldHandleNegativeNumbers() {
    assertThat(Max.execute(List.of(
        -10,
        -50,
        -1,
        -100
    ))).contains(-1);
  }

  @Test
  void shouldRejectNullElement() {
    assertThatThrownBy(() ->
        Max.execute(java.util.Arrays.asList(10, null, 20))
    )
        .isInstanceOf(NullPointerException.class);
  }
}
