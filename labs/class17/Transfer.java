import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

void main() throws Exception {
  try (var connection = DriverManager.getConnection("jdbc:sqlite:app.db")) {
    try (var statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS accounts (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              owner TEXT NOT NULL,
              balance NUMERIC NOT NULL
          )
          """);

      statement.execute("""
          INSERT INTO accounts (owner, balance)
          VALUES ('Alice', 100.00), ('Bob', 50.00)
          """);
    }

    try {
      Transfer.execute(
          connection,
          1,
          2,
          new BigDecimal("1000.00")
      );
    } catch (InsufficientBalanceException exception) {
      IO.println(exception.getMessage());
    }
  }
}

static class InsufficientBalanceException extends Exception {
  InsufficientBalanceException() {
    super("Saldo insuficiente");
  }
}

static class Transfer {
  private Transfer() {
  }

  static void execute(
      Connection connection,
      long fromId,
      long toId,
      BigDecimal amount
  ) throws SQLException, InsufficientBalanceException {

    connection.setAutoCommit(false);

    try {
      var withdrawSql = """
          UPDATE accounts
          SET balance = balance - ?
          WHERE id = ?
            AND balance >= ?
          """;

      try (var statement = connection.prepareStatement(withdrawSql)) {
        statement.setBigDecimal(1, amount);
        statement.setLong(2, fromId);
        statement.setBigDecimal(3, amount);

        if (statement.executeUpdate() == 0) {
          throw new InsufficientBalanceException();
        }
      }

      var depositSql = """
          UPDATE accounts
          SET balance = balance + ?
          WHERE id = ?
          """;

      try (var statement = connection.prepareStatement(depositSql)) {
        statement.setBigDecimal(1, amount);
        statement.setLong(2, toId);
        statement.executeUpdate();
      }

      connection.commit();
    } catch (Exception exception) {
      connection.rollback();
      throw exception;
    } finally {
      connection.setAutoCommit(true);
    }
  }
}