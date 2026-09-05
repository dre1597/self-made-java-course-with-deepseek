package class18;

import java.util.List;
import java.util.Optional;

class Max {
  static Optional<Integer> execute(List<Integer> values) {
    return values.stream()
        .max(Integer::compareTo);
  }
}
