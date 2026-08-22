void main() {
  var item = new OrderItem("sku", 1);
  IO.println(item);
}

record OrderItem(String sku, int quantity) {
}