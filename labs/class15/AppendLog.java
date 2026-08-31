void main() throws IOException {
  var path = Path.of("logs/app/application.log");

  AppendLog.execute(path, "Aplicação iniciada");
  AppendLog.execute(path, "Usuário conectado");

  IO.println(Files.readString(path));
}

static class AppendLog {
  private AppendLog() {
  }

  static void execute(Path path, String line) throws IOException {
    var parent = path.getParent();

    if (parent != null) {
      Files.createDirectories(parent);
    }

    var logLine = "%s %s%n".formatted(LocalDateTime.now(Clock.systemUTC()), line);

    Files.writeString(
        path,
        logLine,
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND
    );
  }
}