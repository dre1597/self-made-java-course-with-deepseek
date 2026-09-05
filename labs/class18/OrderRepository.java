package class18;

import java.util.Optional;

interface OrderRepository {
  Optional<Order> findById(long id);

  void save(Order order);
}