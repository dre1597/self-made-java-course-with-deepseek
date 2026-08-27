void main() {
  var products = List.of(
      new Product("Notebook", "Eletrônicos", new BigDecimal("3500.00")),
      new Product("Mouse", "Eletrônicos", new BigDecimal("100.00")),
      new Product("Camiseta", "Roupas", new BigDecimal("80.00")),
      new Product("Calça", "Roupas", new BigDecimal("120.00")),
      new Product("Desconto", "Roupas", new BigDecimal("-20.00"))
  );

  IO.println(TotalByCategory.execute(products));
  IO.println(TotalByCategory.execute(List.of()));
}

record Product(String name, String category, BigDecimal price) {
}

static class TotalByCategory {
  private TotalByCategory() {
  }

  static Map<String, BigDecimal> execute(List<Product> products) {
    return products.stream()
        .collect(Collectors.groupingBy(
            Product::category,
            Collectors.reducing(
                BigDecimal.ZERO,
                Product::price,
                BigDecimal::add
            )
        ));
  }
}