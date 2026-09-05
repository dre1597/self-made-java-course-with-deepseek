package class18;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OrderServiceInteractionTest {

  @Test
  void shouldSaveCalculatedOrderAndSendEmailInOrder() {
    var repository = mock(OrderRepository.class);
    var emailSender = mock(EmailSender.class);
    var service = new OrderService2(repository, emailSender);

    var order = new Order(
        1,
        new BigDecimal("150.00"),
        OrderStatus.PENDING
    );

    when(repository.findById(1))
        .thenReturn(Optional.of(order));

    service.confirmPayment(1);

    var captor = ArgumentCaptor.forClass(Order.class);

    verify(repository).save(captor.capture());

    var savedOrder = captor.getValue();

    assertThat(savedOrder.status())
        .isEqualTo(OrderStatus.PAID);

    assertThat(savedOrder.total())
        .isEqualByComparingTo("150.00");

    var inOrder = inOrder(repository, emailSender);

    inOrder.verify(repository).save(savedOrder);
    inOrder.verify(emailSender).sendConfirmation(savedOrder);
  }
}
