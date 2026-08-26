void main() {
  IO.println(new Transaction("any_id", BigDecimal.valueOf(100), TransactionType.INCOME));
  IO.println(new Transaction("any_id", BigDecimal.valueOf(-10), TransactionType.EXPENSE));
  IO.println(new Transaction(null, BigDecimal.valueOf(100), TransactionType.EXPENSE));
}

enum TransactionType {
  INCOME,
  EXPENSE,
}

record Transaction(String id, BigDecimal amount, TransactionType type) {
  Transaction {
    if (id.isBlank()) {
      throw new IllegalArgumentException("id cannot be blank");
    }

    if (amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("amount cannot be negative");
    }
  }
}