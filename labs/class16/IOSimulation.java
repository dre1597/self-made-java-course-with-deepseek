static long measure(AutoCloseableExecutor executor) throws Exception {
  var start = System.nanoTime();

  try (executor) {
    for (var i = 0; i < 100; i++) {
      executor.submit(() -> {
        Thread.sleep(50);
        return null;
      });
    }

    executor.awaitTermination(10, TimeUnit.SECONDS);
  }

  return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
}

void main() throws Exception {
  var virtualTime = measure((AutoCloseableExecutor) Executors.newVirtualThreadPerTaskExecutor());
  var fixedTime = measure((AutoCloseableExecutor) Executors.newFixedThreadPool(4));

  IO.println("Virtual threads: " + virtualTime + " ms");
  IO.println("Fixed pool:      " + fixedTime + " ms");
}

interface AutoCloseableExecutor extends AutoCloseable {
  <T> void submit(Callable<T> task);

  void awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;

  void close();
}