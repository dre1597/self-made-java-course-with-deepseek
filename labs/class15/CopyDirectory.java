void main() throws IOException {
  var source = Path.of("source");
  var target = Path.of("target");

  Files.createDirectories(source.resolve("nested/deep"));
  Files.writeString(source.resolve("file.txt"), "arquivo na raiz");
  Files.writeString(source.resolve("nested/file.txt"), "arquivo aninhado");
  Files.writeString(source.resolve("nested/deep/file.txt"), "arquivo bem aninhado");

  CopyDirectory.execute(source, target);
}

static class CopyDirectory {
  private CopyDirectory() {
  }

  static void execute(Path source, Path target) throws IOException {
    try (var paths = Files.walk(source)) {
      paths.forEach(path -> {
        var destination = target.resolve(source.relativize(path));

        try {
          if (Files.isDirectory(path)) {
            Files.createDirectories(destination);
          } else {
            Files.copy(path, destination);
          }
        } catch (IOException exception) {
          throw new RuntimeException(exception);
        }
      });
    }
  }
}