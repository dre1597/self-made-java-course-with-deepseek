import class15.Config;

void main() throws IOException {
  var config = new Config("jdbc:postgresql://localhost/app");

  try (
      var output = Files.newOutputStream(Path.of("config.ser"));
      var objectOutput = new ObjectOutputStream(output)
  ) {
    objectOutput.writeObject(config);
  }
}