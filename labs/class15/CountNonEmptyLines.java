void main() throws IOException {
  var empty = Path.of("empty.txt");
  var blank = Path.of("blank.txt");
  var bom = Path.of("bom.txt");

  Files.writeString(empty, "");

  Files.writeString(blank, """
      
      
      
      
      """);

  Files.writeString(bom, "\uFEFFprimeira linha\n\nsegunda linha\n");

  IO.println(CountNonEmptyLines.execute(empty));
  IO.println(CountNonEmptyLines.execute(blank));
  IO.println(CountNonEmptyLines.execute(bom));
}

static class CountNonEmptyLines {
  private CountNonEmptyLines() {
  }

  static long execute(Path path) throws IOException {
    try (var lines = Files.lines(path)) {
      return lines
          .map(line -> line.replaceFirst("^\\uFEFF", ""))
          .filter(line -> !line.isBlank())
          .count();
    }
  }
}