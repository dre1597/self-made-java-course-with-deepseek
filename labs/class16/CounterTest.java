static void test(Counter counter) throws InterruptedException {
  var threads = 10;
  var increments = 10_000;
  var done = new CountDownLatch(threads);

  for (var i = 0; i < threads; i++) {
    Thread.startVirtualThread(() -> {
      for (var j = 0; j < increments; j++) {
        counter.increment();
      }

      done.countDown();
    });
  }

  done.await();

  IO.println(counter.get());
}

void main() throws InterruptedException {
  test(new AtomicCounter());
  test(new SynchronizedCounter());
}

interface Counter {
  void increment();

  int get();
}

static class AtomicCounter implements Counter {
  private final AtomicInteger value = new AtomicInteger();

  @Override
  public void increment() {
    value.incrementAndGet();
  }

  @Override
  public int get() {
    return value.get();
  }
}

static class SynchronizedCounter implements Counter {
  private int value;

  @Override
  public synchronized void increment() {
    value++;
  }

  @Override
  public synchronized int get() {
    return value;
  }
}