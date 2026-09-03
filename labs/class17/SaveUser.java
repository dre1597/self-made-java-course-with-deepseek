import java.sql.DriverManager;
import java.sql.SQLException;

void main() throws Exception {
  try (var connection = DriverManager.getConnection("jdbc:sqlite:app.db")) {
    try (var statement = connection.createStatement()) {
      statement.execute("""
          CREATE TABLE IF NOT EXISTS users (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              email TEXT NOT NULL UNIQUE
          )
          """);
    }

    var repository = new UserRepository(connection);

    var first = repository.save(
        new User(0, "Dré", "dre@example.com")
    );

    IO.println(first);

    try {
      repository.save(
          new User(0, "Outro", "dre@example.com")
      );
    } catch (SQLException exception) {
      IO.println("Constraint violada: " + exception.getMessage());
    }
  }
}

record User(long id, String name, String email) {
}

static class UserRepository {
  private final java.sql.Connection connection;

  UserRepository(java.sql.Connection connection) {
    this.connection = connection;
  }

  User save(User user) throws SQLException {
    var sql = """
        INSERT INTO users (name, email)
        VALUES (?, ?)
        """;

    try (var statement = connection.prepareStatement(
        sql,
        java.sql.Statement.RETURN_GENERATED_KEYS
    )) {
      statement.setString(1, user.name());
      statement.setString(2, user.email());

      statement.executeUpdate();

      try (var keys = statement.getGeneratedKeys()) {
        if (!keys.next()) {
          throw new SQLException("ID gerado não retornado");
        }

        return new User(
            keys.getLong(1),
            user.name(),
            user.email()
        );
      }
    }
  }
}