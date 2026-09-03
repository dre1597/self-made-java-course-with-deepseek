import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

void main() throws Exception {
  try (var setup = DriverManager.getConnection("jdbc:sqlite:app.db")) {
    try (var statement = setup.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS products (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              price NUMERIC NOT NULL,
              version INTEGER NOT NULL DEFAULT 0
          )
          """);

      statement.execute("""
          INSERT INTO products (name, price, version)
          VALUES ('Keyboard', 100.00, 0)
          """);
    }
  }

  try (
      var connection1 = DriverManager.getConnection("jdbc:sqlite:app.db");
      var connection2 = DriverManager.getConnection("jdbc:sqlite:app.db")
  ) {
    var product1 = ProductRepository.findById(connection1, 1);
    var product2 = ProductRepository.findById(connection2, 1);

    ProductRepository.update(
        connection1,
        new Product(
            product1.id(),
            product1.name(),
            new BigDecimal("110.00"),
            product1.version()
        )
    );

    try {
      ProductRepository.update(
          connection2,
          new Product(
              product2.id(),
              product2.name(),
              new BigDecimal("120.00"),
              product2.version()
          )
      );
    } catch (OptimisticLockException exception) {
      IO.println(exception.getMessage());
    }
  }
}

record Product(long id, String name, BigDecimal price, int version) {
}

static class OptimisticLockException extends Exception {
  OptimisticLockException(long id) {
    super("Conflito de versão para product " + id);
  }
}

static class ProductRepository {
  private ProductRepository() {
  }

  static Product findById(Connection connection, long id) throws SQLException {
    var sql = """
        SELECT id, name, price, version
        FROM products
        WHERE id = ?
        """;

    try (var statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);

      try (var result = statement.executeQuery()) {
        if (!result.next()) {
          throw new SQLException("Produto não encontrado");
        }

        return new Product(
            result.getLong("id"),
            result.getString("name"),
            result.getBigDecimal("price"),
            result.getInt("version")
        );
      }
    }
  }

  static Product update(
      Connection connection,
      Product product
  ) throws SQLException, OptimisticLockException {

    var sql = """
        UPDATE products
        SET name = ?, price = ?, version = version + 1
        WHERE id = ?
          AND version = ?
        """;

    try (var statement = connection.prepareStatement(sql)) {
      statement.setString(1, product.name());
      statement.setBigDecimal(2, product.price());
      statement.setLong(3, product.id());
      statement.setInt(4, product.version());

      if (statement.executeUpdate() == 0) {
        throw new OptimisticLockException(product.id());
      }

      return new Product(
          product.id(),
          product.name(),
          product.price(),
          product.version() + 1
      );
    }
  }
}