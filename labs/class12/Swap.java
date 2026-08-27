void main() {
  var pair = new Pair<>("idade", 29);

  var swapped = Swap.execute(pair);

  IO.println(pair);
  IO.println(swapped);
}

record Pair<K, V>(K key, V value) {
}

static class Swap {
  private Swap() {
  }

  static <K, V> Pair<V, K> execute(Pair<K, V> pair) {
    return new Pair<>(pair.value(), pair.key());
  }
}