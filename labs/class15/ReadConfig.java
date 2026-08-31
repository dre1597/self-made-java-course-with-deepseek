import class15.Config;

void main() throws IOException, ClassNotFoundException {
  try (
      var input = Files.newInputStream(Path.of("config.ser"));
      var objectInput = new ObjectInputStream(input)
  ) {
    var config = (Config) objectInput.readObject();

    IO.println(config);
  }
}