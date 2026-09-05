package class18;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OrderServiceTest {

  @Test
  void shouldConfirmPayment() {
    var repository = mock(OrderRepository.class);
    var service = new OrderService(repository);

    var order = new Order(
        1,
        new BigDecimal("100"),
        OrderStatus.PENDING
    );

    when(repository.findById(1))
        .thenReturn(Optional.of(order));

    service.confirmPayment(1);

    verify(repository).save(
        new Order(1, new BigDecimal("100"), OrderStatus.PAID)
    );
  }

  @Test
  void shouldRejectMissingOrder() {
    var repository = mock(OrderRepository.class);
    var service = new OrderService(repository);

    when(repository.findById(999))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() ->
        service.confirmPayment(999)
    )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Pedido não encontrado: 999");

    verify(repository, never()).save(any());
  }
}