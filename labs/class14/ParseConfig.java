static void test(String line) {
  try {
    IO.println(ParseConfig.execute(line));
  } catch (IllegalArgumentException exception) {
    IO.println(exception.getMessage());
  }
}

void main() {
  IO.println(ParseConfig.execute("host=localhost"));
  IO.println(ParseConfig.execute("port=8080"));

  test("");
  test("localhost");
  test("=localhost");
}

static class ParseConfig {
  private ParseConfig() {
  }

  static Map.Entry<String, String> execute(String line) {
    if (line == null || line.isEmpty()) {
      throw new IllegalArgumentException("Linha não pode ser vazia");
    }

    var separator = line.indexOf('=');

    if (separator < 0) {
      throw new IllegalArgumentException(
          "Formato inválido: esperado chave=valor"
      );
    }

    var key = line.substring(0, separator);
    var value = line.substring(separator + 1);

    if (key.isEmpty()) {
      throw new IllegalArgumentException("Chave não pode ser vazia");
    }

    return Map.entry(key, value);
  }
}