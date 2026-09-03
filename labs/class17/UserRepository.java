import java.sql.Connection;
import java.sql.SQLException;

void main() throws Exception {
  try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:app.db")) {
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

    IO.println(repository.findById(1));
    IO.println(repository.findById(999));
    IO.println(repository.findAll());
  }
}

record User(long id, String name, String email) {
}

static class UserRepository {
  private final Connection connection;

  UserRepository(final Connection connection) {
    this.connection = connection;
  }

  Optional<User> findById(long id) throws SQLException {
    var sql = """
        SELECT id, name, email
        FROM users
        WHERE id = ?
        """;

    try (var statement = connection.prepareStatement(sql)) {
      statement.setLong(1, id);

      try (var result = statement.executeQuery()) {
        if (!result.next()) {
          return Optional.empty();
        }

        return Optional.of(map(result));
      }
    }
  }

  ArrayList<User> findAll() throws SQLException {
    var users = new ArrayList<User>();

    var sql = """
        SELECT id, name, email
        FROM users
        ORDER BY id
        """;

    try (
        var statement = connection.prepareStatement(sql);
        var result = statement.executeQuery()
    ) {
      while (result.next()) {
        users.add(map(result));
      }
    }

    return users;
  }

  private User map(java.sql.ResultSet result) throws SQLException {
    return new User(
        result.getLong("id"),
        result.getString("name"),
        result.getString("email")
    );
  }
}