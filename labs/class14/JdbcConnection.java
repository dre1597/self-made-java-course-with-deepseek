void main() {
  try {
    JdbcConnection.execute();
  } catch (Exception exception) {
    IO.println("Exceção principal: " + exception.getMessage());

    for (var suppressed : exception.getSuppressed()) {
      IO.println("Suppressed: " + suppressed.getMessage());
    }
  }
}

static class JdbcConnection {
  private JdbcConnection() {
  }

  static void execute() {
    try (var connection = new FakeConnection()) {
      throw new IllegalStateException("Erro durante a operação");
    }
//    try (var connection = DriverManager.getConnection(url, user, password)) {
//      // operação
//    }
  }
}

static class FakeConnection implements AutoCloseable {
  @Override
  public void close() {
    throw new IllegalStateException("Erro ao fechar conexão");
  }
}