static void withoutFallback() {
  var price = fetchPrice();
  var tax = fetchTax();

  var total = price.thenCombine(
      tax,
      BigDecimal::add
  );

  try {
    IO.println(total.join());
  } catch (CompletionException exception) {
    IO.println("Falhou: " + exception.getCause());
  }
}

static void withFallback() {
  var price = fetchPrice();
  var tax = fetchTax()
      .exceptionally(exception -> BigDecimal.ZERO);

  var total = price.thenCombine(
      tax,
      BigDecimal::add
  );

  IO.println("Com fallback: " + total.join());
}

static CompletableFuture<BigDecimal> fetchPrice() {
  return CompletableFuture.supplyAsync(() -> {
    sleep(100);
    return new BigDecimal("100.00");
  });
}

static CompletableFuture<BigDecimal> fetchTax() {
  return CompletableFuture.supplyAsync(() -> {
    sleep(100);
    throw new IllegalStateException("Falha ao buscar imposto");
  });
}

static void sleep(long millis) {
  try {
    Thread.sleep(millis);
  } catch (InterruptedException exception) {
    Thread.currentThread().interrupt();
    throw new RuntimeException(exception);
  }
}

void main() {
  withoutFallback();
  withFallback();
}