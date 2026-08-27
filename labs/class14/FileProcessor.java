void main() {
  var processor = new FileProcessor();

  try {
    processor.read(Path.of("arquivo-inexistente.txt"));
  } catch (IllegalStateException exception) {
    IO.println(exception.getMessage());
  }

  var path = Path.of("arquivo-teste.txt");

  processor.write(path, "Java 25");

  try {
    IO.println(processor.read(path));
  } catch (IllegalStateException exception) {
    IO.println(exception.getMessage());
  }
}

static class FileProcessor {
  String read(Path path) {
    try (var reader = Files.newBufferedReader(path)) {
      return reader.readLine();
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Não foi possível ler o arquivo: " + path,
          exception
      );
    }
  }

  void write(Path path, String content) {
    try (var writer = Files.newBufferedWriter(path)) {
      writer.write(content);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Não foi possível escrever no arquivo: " + path,
          exception
      );
    }
  }
}