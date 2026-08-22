void main() {
  var product1 = new Product("0123", 10);

  IO.println(product1.sku());
  IO.println(product1.price());
}

record Product(String sku, double price) {
}