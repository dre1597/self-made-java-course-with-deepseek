static long measure(boolean virtual) {
  var executor = virtual
      ? Executors.newVirtualThreadPerTaskExecutor()
      : Executors.newFixedThreadPool(4);

  var start = System.nanoTime();

  try (executor) {
    for (var i = 0; i < 100; i++) {
      executor.submit(() -> {
        Thread.sleep(50);
        return null;
      });
    }
  }

  return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
}

void main() {
  IO.println("Virtual threads: " + measure(true) + " ms");
  IO.println("Fixed pool: " + measure(false) + " ms");
}